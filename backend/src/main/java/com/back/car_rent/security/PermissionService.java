package com.back.car_rent.security;

import com.back.car_rent.model.AccessRights;
import com.back.car_rent.model.Employee;
import com.back.car_rent.model.User;
import com.back.car_rent.repository.EmployeeRepository;
import com.back.car_rent.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * Who may use which part of the back office, used from {@code @PreAuthorize("@perm.can('cars')")}.
 *
 * <ul>
 *   <li>SUPERADMIN and AGENCY_ADMIN: everything.</li>
 *   <li>AGENCY_EMPLOYEE: the sections ticked in their employee record ({@link AccessRights}); the employee
 *       record is found through the e-mail address shared with the login.</li>
 *   <li>Anyone else (clients): nothing.</li>
 * </ul>
 */
@Service("perm")
public class PermissionService {

    /** Sections an employee needs to read when working on contracts (pick a car and a client). */
    private static final Set<String> LOOKUPS_FOR_CONTRACTS = Set.of("cars", "clients");

    private final UserRepository users;
    private final EmployeeRepository employees;

    public PermissionService(UserRepository users, EmployeeRepository employees) {
        this.users = users;
        this.employees = employees;
    }

    private Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public boolean isAdmin() {
        Authentication a = auth();
        return a != null && a.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_SUPERADMIN") || g.getAuthority().equals("ROLE_AGENCY_ADMIN"));
    }

    private boolean isEmployee() {
        Authentication a = auth();
        return a != null && a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_AGENCY_EMPLOYEE"));
    }

    /** The access rights of the logged-in employee, if any. */
    public Optional<AccessRights> rightsOfCurrentEmployee() {
        Authentication a = auth();
        if (a == null || !isEmployee()) {
            return Optional.empty();
        }
        return users.findByUsername(a.getName())
                .map(User::getEmail)
                .flatMap(employees::findByEmailIgnoreCase)
                .map(Employee::getAccessRights);
    }

    /** Full use (read and write) of one section. */
    public boolean can(String section) {
        if (isAdmin()) {
            return true;
        }
        return rightsOfCurrentEmployee().map(rights -> has(rights, section)).orElse(false);
    }

    /**
     * Read access: the section itself, or a section another one needs to look things up: cars and clients for
     * whoever writes contracts, contracts for whoever records payments.
     */
    public boolean canRead(String section) {
        if (can(section)) {
            return true;
        }
        if ("contracts".equals(section) && can("payments")) {
            return true;
        }
        return LOOKUPS_FOR_CONTRACTS.contains(section) && can("contracts");
    }

    public static boolean has(AccessRights rights, String section) {
        if (rights == null) {
            return false;
        }
        return switch (section) {
            case "dashboard" -> rights.isDashboard();
            case "cars" -> rights.isCars();
            case "clients" -> rights.isClients();
            case "contracts" -> rights.isContracts();
            case "payments" -> rights.isPayments();
            case "partners" -> rights.isPartners();
            case "expenses" -> rights.isExpenses();
            case "reports" -> rights.isReports();
            default -> false;
        };
    }
}
