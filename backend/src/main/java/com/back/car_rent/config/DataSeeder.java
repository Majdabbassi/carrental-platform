package com.back.car_rent.config;

import com.back.car_rent.model.AccessRights;
import com.back.car_rent.model.Car;
import com.back.car_rent.model.Client;
import com.back.car_rent.model.Contract;
import com.back.car_rent.model.Employee;
import com.back.car_rent.model.EmergencyContact;
import com.back.car_rent.model.Expense;
import com.back.car_rent.model.Partner;
import com.back.car_rent.model.User;
import com.back.car_rent.pricing.PricingRule;
import com.back.car_rent.pricing.PricingRuleRepository;
import com.back.car_rent.repository.CarRepository;
import com.back.car_rent.repository.ClientRepository;
import com.back.car_rent.repository.ContractRepository;
import com.back.car_rent.repository.EmployeeRepository;
import com.back.car_rent.repository.ExpenseRepository;
import com.back.car_rent.repository.PartnerRepository;
import com.back.car_rent.repository.UserRepository;
import com.back.car_rent.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Creates the first administrator (from ADMIN_USERNAME / ADMIN_PASSWORD, only when no user exists yet) and,
 * when DEMO_DATA=true, a small demo agency with staff accounts. Nothing is created over existing data.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final CarRepository cars;
    private final ClientRepository clients;
    private final ContractRepository contracts;
    private final EmployeeRepository employees;
    private final ExpenseRepository expenses;
    private final PartnerRepository partners;
    private final PricingRuleRepository pricingRules;
    private final PasswordEncoder encoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;
    @Value("${app.admin.email:admin@carrental.demo}")
    private String adminEmail;
    @Value("${app.admin.password:}")
    private String adminPassword;
    @Value("${app.demo-data:false}")
    private boolean demoData;
    @Value("${app.demo-password:Rental@2026!}")
    private String demoPassword;

    public DataSeeder(UserRepository users, CarRepository cars, ClientRepository clients, ContractRepository contracts,
                      EmployeeRepository employees, ExpenseRepository expenses, PartnerRepository partners,
                      PricingRuleRepository pricingRules, PasswordEncoder encoder) {
        this.users = users;
        this.cars = cars;
        this.clients = clients;
        this.contracts = contracts;
        this.employees = employees;
        this.expenses = expenses;
        this.partners = partners;
        this.pricingRules = pricingRules;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() == 0) {
            if (adminPassword == null || adminPassword.isBlank()) {
                log.warn("No user exists and ADMIN_PASSWORD is not set: nobody will be able to log in. Set ADMIN_PASSWORD.");
            } else {
                users.save(User.builder().username(adminUsername).email(adminEmail)
                        .password(encoder.encode(adminPassword)).role(Role.SUPERADMIN).enabled(true).build());
                log.info("Created the administrator '{}'", adminUsername);
            }
        }
        if (demoData && cars.count() == 0) {
            seedDemoAgency();
        }
    }

    // ------------------------------------------------------------------ demo agency

    private void seedDemoAgency() {
        // staff: a manager (administrator), an accountant and a receptionist with limited rights
        staff("Sarra Ben Ali", "sarra@carrental.demo", "Managers Department", "Agency manager", 4200.0,
                rights(true, true, true, true, true, true, true, true), Role.AGENCY_ADMIN, "manager");
        staff("Hedi Trabelsi", "hedi@carrental.demo", "Accounts Department", "Accountant", 2600.0,
                rights(true, false, false, true, true, false, true, true), Role.AGENCY_EMPLOYEE, "accountant");
        staff("Amel Jaziri", "amel@carrental.demo", "Marketing Department", "Receptionist", 1900.0,
                rights(true, true, true, true, false, false, false, false), Role.AGENCY_EMPLOYEE, "reception");

        car("Toyota", "Corolla", 2022, "TN-2201-A", "Sedan", 45.0, "Available", "Gasoline", "Automatic", "#2563eb", 28400);
        car("Renault", "Clio", 2023, "TN-2310-B", "Sedan", 35.0, "Rented", "Gasoline", "Manual", "#16a34a", 12100);
        car("Peugeot", "3008", 2022, "TN-2275-C", "SUV", 70.0, "Available", "Diesel", "Automatic", "#7c3aed", 33900);
        car("Kia", "Sportage", 2023, "TN-2342-D", "SUV", 75.0, "Rented", "Diesel", "Automatic", "#ea580c", 9800);
        car("Mercedes", "C-Class", 2021, "TN-2188-E", "Luxury", 140.0, "Available", "Diesel", "Automatic", "#0f172a", 41200);
        car("BMW", "X5", 2022, "TN-2260-F", "Luxury", 190.0, "Maintenance", "Diesel", "Automatic", "#475569", 27700);
        car("Dacia", "Duster", 2021, "TN-2154-G", "SUV", 40.0, "Available", "Diesel", "Manual", "#0891b2", 52300);
        car("Volkswagen", "Golf", 2020, "TN-2099-H", "Sedan", 38.0, "Rented", "Gasoline", "Manual", "#dc2626", 61800);

        client("CL-001", "Yassine Gharbi", "yassine.gharbi@example.com", "+216 20 100 001", "Male", "Active", 4);
        client("CL-002", "Nour Mansouri", "nour.mansouri@example.com", "+216 21 100 002", "Female", "Active", 2);
        client("CL-003", "Karim Bouzid", "karim.bouzid@example.com", "+216 22 100 003", "Male", "Active", 6);
        client("CL-004", "Salma Hamdi", "salma.hamdi@example.com", "+216 23 100 004", "Female", "Pending Verification", 0);
        client("CL-005", "Omar Jlassi", "omar.jlassi@example.com", "+216 24 100 005", "Male", "Blacklisted", 1);
        client("CL-006", "Ines Sassi", "ines.sassi@example.com", "+216 25 100 006", "Female", "Active", 3);

        contract("CT-1001", "Yassine Gharbi", "+216 20 100 001", "Renault", "Clio", "TN-2310-B", "Weekly", -3, 4, 35.0, "Active", "Partial");
        contract("CT-1002", "Nour Mansouri", "+216 21 100 002", "Kia", "Sportage", "TN-2342-D", "Daily", -1, 2, 75.0, "Active", "Paid");
        contract("CT-1003", "Karim Bouzid", "+216 22 100 003", "Toyota", "Corolla", "TN-2201-A", "Monthly", -40, -10, 45.0, "Completed", "Paid");
        contract("CT-1004", "Ines Sassi", "+216 25 100 006", "Peugeot", "3008", "TN-2275-C", "Weekly", 5, 12, 70.0, "Reserved", "Pending");
        contract("CT-1005", "Salma Hamdi", "+216 23 100 004", "Mercedes", "C-Class", "TN-2188-E", "Daily", -20, -17, 140.0, "Completed", "Paid");
        contract("CT-1006", "Karim Bouzid", "+216 22 100 003", "Dacia", "Duster", "TN-2154-G", "Daily", 9, 12, 40.0, "Reserved", "Partial");

        // dates that give the daily alert check something to report
        dates("TN-2188-E", 12, 200, 40);   // insurance expires in 12 days
        dates("TN-2154-G", 200, 25, 40);   // registration expires in 25 days
        dates("TN-2260-F", 200, 200, -3);  // service overdue (the BMW is in the workshop)
        dates("TN-2310-B", 200, 200, 9);   // service due soon
        contract("CT-1007", "Yassine Gharbi", "+216 20 100 001", "Volkswagen", "Golf", "TN-2099-H", "Weekly", -9, -2, 38.0, "Active", "Pending");

        int year = LocalDate.now().getYear();
        for (int y = year; y <= year + 1; y++) {
            season("Summer high season", y + "-06-15", y + "-09-15", 1.3, null);
            season("Year-end holidays", y + "-12-20", (y + 1) + "-01-05", 1.25, null);
            season("Luxury wedding season", y + "-05-01", y + "-06-14", 1.15, "Luxury");
        }
        longStay("One week or more", 7, 10.0);
        longStay("One month or more", 30, 20.0);

        expense("EX-001", "Maintenance", "BMW X5: brake pads and service", 780.0, -6, "Sarra Ben Ali", "paid", "BMW X5");
        expense("EX-002", "Insurance", "Fleet insurance, 3rd quarter", 5200.0, -20, "Hedi Trabelsi", "paid", null);
        expense("EX-003", "Fuel", "Fuel for delivery runs", 310.0, -4, "Amel Jaziri", "paid", null);
        expense("EX-004", "Maintenance", "Renault Clio: oil change", 95.0, -2, "Sarra Ben Ali", "pending", "Renault Clio");
        expense("EX-005", "Marketing", "Airport leaflet campaign", 450.0, -35, "Hedi Trabelsi", "overdue", null);
        expense("EX-006", "Rent", "Office rent", 1800.0, -1, "Hedi Trabelsi", "pending", null);

        partner("PT-001", "Maghrebia Assurances", "Insurance", "Mehdi Karray", "+216 71 200 001", "contact@maghrebia.example");
        partner("PT-002", "Garage El Manar", "Maintenance", "Anis Saidi", "+216 71 200 002", "garage@elmanar.example");
        partner("PT-003", "Sahara Tours", "Tour operator", "Rim Chaabane", "+216 71 200 003", "bookings@sahara-tours.example");

        log.info("Seeded the demo agency (cars, clients, contracts, expenses, partners, staff accounts).");
    }

    private static AccessRights rights(boolean dashboard, boolean cars, boolean clients, boolean contracts,
                                       boolean payments, boolean partners, boolean expenses, boolean reports) {
        AccessRights r = new AccessRights();
        r.setDashboard(dashboard);
        r.setCars(cars);
        r.setClients(clients);
        r.setContracts(contracts);
        r.setPayments(payments);
        r.setPartners(partners);
        r.setExpenses(expenses);
        r.setReports(reports);
        return r;
    }

    private void staff(String name, String email, String department, String title, double salary,
                       AccessRights rights, Role role, String username) {
        employees.save(Employee.builder()
                .employeeId("EMP-" + username.toUpperCase()).fullName(name).email(email).phone("+216 70 000 000")
                .role(title).status("Active").dateJoined(LocalDate.now().minusYears(2).toString())
                .department(department).salary(salary).accessRights(rights).build());
        users.save(User.builder().username(username).email(email).password(encoder.encode(demoPassword))
                .role(role).enabled(true).build());
    }

    private void car(String make, String model, int year, String plate, String category, double rate, String status,
                     String fuel, String transmission, String color, int mileage) {
        cars.save(Car.builder().make(make).model(model).year(year).licensePlate(plate).vin("VF1" + plate.replaceAll("\\W", ""))
                .category(category).dailyRate(rate).weeklyRate(rate * 6).monthlyRate(rate * 24).status(status)
                .fuelType(fuel).transmission(transmission).color(color).mileage(mileage)
                .lastMaintenance(LocalDate.now().minusMonths(3).toString())
                .nextMaintenance(LocalDate.now().plusMonths(3).toString())
                .insuranceExpiry(LocalDate.now().plusMonths(8).toString())
                .registrationExpiry(LocalDate.now().plusMonths(10).toString())
                .imageUrl(banner(make + " " + model, color)).build());
    }

    private void dates(String plate, int insuranceDays, int registrationDays, int serviceDays) {
        cars.findFirstByLicensePlate(plate).ifPresent(car -> {
            car.setInsuranceExpiry(LocalDate.now().plusDays(insuranceDays).toString());
            car.setRegistrationExpiry(LocalDate.now().plusDays(registrationDays).toString());
            car.setNextMaintenance(LocalDate.now().plusDays(serviceDays).toString());
            cars.save(car);
        });
    }

    private void season(String name, String start, String end, double multiplier, String category) {
        pricingRules.save(PricingRule.builder().name(name).type("SEASON").startDate(start).endDate(end)
                .multiplier(multiplier).category(category).build());
    }

    private void longStay(String name, int minDays, double percent) {
        pricingRules.save(PricingRule.builder().name(name).type("LONG_STAY").minDays(minDays).discountPercent(percent).build());
    }

    private void client(String id, String name, String email, String phone, String gender, String status, int rentals) {
        EmergencyContact emergency = new EmergencyContact();
        emergency.setName("Family contact");
        emergency.setPhone("+216 98 000 000");
        emergency.setRelationship("Relative");
        clients.save(Client.builder().clientId(id).fullName(name).email(email).phone(phone).gender(gender)
                .dateOfBirth("1990-05-14").address("Tunis, Tunisia").drivingLicenseNumber("DL-" + id)
                .licenseExpiryDate(LocalDate.now().plusYears(3).toString()).nationalId("0" + id.substring(3) + "4455")
                .status(status).registrationDate(LocalDate.now().minusMonths(8).toString()).totalRentals(rentals)
                .emergencyContact(emergency).build());
    }

    private void contract(String id, String clientName, String phone, String make, String model, String plate,
                          String rentalType, int startOffset, int endOffset, double rate, String status, String payment) {
        LocalDate start = LocalDate.now().plusDays(startOffset);
        LocalDate end = LocalDate.now().plusDays(endOffset);
        long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start, end));
        contracts.save(Contract.builder().contractId(id).clientName(clientName).clientPhone(phone).carMake(make)
                .carModel(model).licensePlate(plate).rentalType(rentalType).startDate(start.toString())
                .endDate(end.toString()).dailyRate(rate).totalValue(rate * days).deposit(200.0).status(status)
                .paymentStatus(payment).client(clientName).car(make + " " + model)
                .paymentMethod("Cash").build());
    }

    private void expense(String id, String category, String description, double amount, int dayOffset, String paidBy,
                         String status, String linkedCar) {
        expenses.save(Expense.builder().expenseId(id).category(category).description(description).amount(amount)
                .date(LocalDate.now().plusDays(dayOffset).toString()).paidBy(paidBy).status(status)
                .linkedCar(linkedCar).build());
    }

    private void partner(String id, String company, String type, String contact, String phone, String email) {
        partners.save(Partner.builder().partnerId(id).companyName(company).type(type).contactPerson(contact)
                .phone(phone).email(email).status("Active").address("Tunis, Tunisia")
                .partnershipStartDate(LocalDate.now().minusYears(1).toString()).build());
    }

    /** A simple generated picture (SVG data URL) so the demo shows no placeholder images. */
    private static String banner(String title, String color) {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='640' height='360' viewBox='0 0 640 360'>"
                + "<defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'><stop offset='0' stop-color='" + color
                + "'/><stop offset='1' stop-color='#0f172a'/></linearGradient></defs>"
                + "<rect width='640' height='360' fill='url(#g)'/>"
                + "<path d='M120 230 L170 160 Q190 140 220 140 L420 140 Q450 140 470 160 L520 230 Z' fill='white' fill-opacity='.18'/>"
                + "<rect x='90' y='225' width='460' height='60' rx='22' fill='white' fill-opacity='.28'/>"
                + "<circle cx='180' cy='290' r='34' fill='#0f172a' stroke='white' stroke-opacity='.6' stroke-width='6'/>"
                + "<circle cx='460' cy='290' r='34' fill='#0f172a' stroke='white' stroke-opacity='.6' stroke-width='6'/>"
                + "<text x='50%' y='70' text-anchor='middle' font-family='Arial, sans-serif' font-size='40' "
                + "font-weight='700' fill='white'>" + title + "</text></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }
}
