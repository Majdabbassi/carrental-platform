package com.back.car_rent.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.back.car_rent.model.Employee;
import com.back.car_rent.service.EmployeeService;
import com.back.car_rent.spec.EmployeeSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@PreAuthorize("@perm.isAdmin()")
public class EmployeeController {

    private final EmployeeService service;

    private final com.back.car_rent.repository.UserRepository users;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;

    public EmployeeController(EmployeeService service, com.back.car_rent.repository.UserRepository users,
                              org.springframework.security.crypto.password.PasswordEncoder encoder) {
        this.service = service;
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping
    public List<Employee> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<Employee> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Employee create(@RequestBody Employee employee) { employee.setId(null); return service.save(employee); }

    @PutMapping("/{id}")
    public ResponseEntity<Employee> update(@PathVariable Long id, @RequestBody Employee employee) {
        return service.findById(id).map(existing -> {
            employee.setId(existing.getId());
            return ResponseEntity.ok(service.save(employee));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!service.existsById(id)) return ResponseEntity.notFound().build();
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public List<Employee> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department
    ) {
        return service.findAll(
                Specification.allOf(
                        EmployeeSpecifications.nameContains(name),
                        EmployeeSpecifications.emailContains(email),
                        EmployeeSpecifications.phoneContains(phone),
                        EmployeeSpecifications.roleEquals(role),
                        EmployeeSpecifications.statusEquals(status),
                        EmployeeSpecifications.departmentEquals(department)
                )
        );
    }

    /** Creates (or resets) the login of an employee: the employee signs in with this username and password. */
    @PostMapping("/{id}/account")
    public ResponseEntity<java.util.Map<String, String>> createAccount(@PathVariable Long id,
                                                                       @RequestBody java.util.Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.length() < 8) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "A username and a password of at least 8 characters are required"));
        }
        return service.findById(id).map(employee -> {
            com.back.car_rent.model.User user = users.findByUsername(username.trim()).orElseGet(com.back.car_rent.model.User::new);
            if (user.getId() != null && (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(employee.getEmail()))) {
                return ResponseEntity.status(409).body(java.util.Map.of("message", "This username belongs to someone else"));
            }
            user.setUsername(username.trim());
            user.setEmail(employee.getEmail());
            user.setPassword(encoder.encode(password));
            user.setRole(com.back.car_rent.security.Role.AGENCY_EMPLOYEE);
            user.setEnabled(true);
            users.save(user);
            return ResponseEntity.ok(java.util.Map.of("username", user.getUsername(), "role", user.getRole().name()));
        }).orElse(ResponseEntity.notFound().build());
    }
}
