package com.back.car_rent;

import com.back.car_rent.model.User;
import com.back.car_rent.repository.UserRepository;
import com.back.car_rent.security.JwtProperties;
import com.back.car_rent.security.JwtService;
import com.back.car_rent.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may use which part of the back office, over real HTTP against the demo agency (DataSeeder):
 * admin (super admin), manager (agency admin), accountant (dashboard, contracts, payments, expenses, reports)
 * and reception (dashboard, cars, clients, contracts).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:carrental_it;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef",
                "app.admin.password=Admin#12345",
                "app.demo-data=true",
                "app.demo-password=Rental@2026!"
        })
class AccessControlIntegrationTest {

    private static final String DEMO = "Rental@2026!";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired Environment environment;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;

    // ------------------------------------------------------------------ helpers

    private record Reply(int status, String body) {
        JsonNode json() throws Exception {
            return JSON.readTree(body);
        }
    }

    private Reply call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body());
    }

    private JsonNode loginResponse(String username, String password) throws Exception {
        Reply reply = call("POST", "/api/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertEquals(200, reply.status(), "login " + username + " -> " + reply.body());
        return reply.json();
    }

    private String login(String username, String password) throws Exception {
        return loginResponse(username, password).path("token").asText();
    }

    private String admin() throws Exception {
        return login("admin", "Admin#12345");
    }

    private String staff(String username) throws Exception {
        return login(username, DEMO);
    }

    private void expect(int status, String method, String path, String token) throws Exception {
        String body = method.equals("POST") || method.equals("PUT") ? "{}" : null; // a body, so only the permission decides
        Reply reply = call(method, path, token, body);
        assertEquals(status, reply.status(), method + " " + path + " -> " + reply.body());
    }

    private static final String[] SECTIONS = {"/api/cars", "/api/clients", "/api/contracts", "/api/expenses",
            "/api/partners", "/api/employees", "/api/dashboard/summary", "/api/calendar/events"};

    // ------------------------------------------------------------------ tests

    // Regression: the whole API was open (anyRequest().permitAll()) and no administrator could even exist.
    @Test
    void everythingNeedsALogin() throws Exception {
        for (String path : SECTIONS) {
            expect(401, "GET", path, null);
        }
        expect(401, "POST", "/api/cars", null);
        expect(401, "DELETE", "/api/clients/1", null);
        expect(401, "GET", "/api/cars", "not-a-token");
        assertEquals(200, call("GET", "/v3/api-docs", null, null).status());
    }

    @Test
    void administratorsSeeEverything() throws Exception {
        for (String who : new String[]{"admin", null}) {
            String token = who == null ? staff("manager") : admin();
            for (String path : SECTIONS) {
                expect(200, "GET", path, token);
            }
        }
    }

    @Test
    void receptionUsesTheSectionsItWasGivenAndNothingElse() throws Exception {
        String reception = staff("reception"); // dashboard, cars, clients, contracts
        for (String path : new String[]{"/api/cars", "/api/clients", "/api/contracts", "/api/dashboard/summary", "/api/calendar/events"}) {
            expect(200, "GET", path, reception);
        }
        for (String path : new String[]{"/api/expenses", "/api/partners", "/api/employees"}) {
            expect(403, "GET", path, reception);
        }
        expect(403, "POST", "/api/expenses", reception);
        expect(403, "DELETE", "/api/partners/1", reception);
        expect(403, "PUT", "/api/employees/1", reception);
    }

    @Test
    void theAccountantCannotTouchCarsOrPartnersButCanReadWhatContractsNeed() throws Exception {
        String accountant = staff("accountant"); // dashboard, contracts, payments, expenses, reports
        for (String path : new String[]{"/api/expenses", "/api/contracts", "/api/dashboard/summary",
                "/api/cars", "/api/clients"}) { // cars and clients are readable as lookups for contracts
            expect(200, "GET", path, accountant);
        }
        expect(403, "GET", "/api/partners", accountant);
        expect(403, "GET", "/api/employees", accountant);
        expect(403, "POST", "/api/cars", accountant);
        expect(403, "DELETE", "/api/cars/1", accountant);
        expect(403, "POST", "/api/clients", accountant);
    }

    @Test
    void whoAmIDrivesTheMenu() throws Exception {
        JsonNode reception = call("GET", "/api/auth/me", staff("reception"), null).json();
        assertEquals("AGENCY_EMPLOYEE", reception.path("role").asText());
        assertTrue(reception.path("sections").path("cars").asBoolean());
        assertFalse(reception.path("sections").path("expenses").asBoolean());
        assertFalse(reception.path("sections").path("employees").asBoolean());

        JsonNode manager = call("GET", "/api/auth/me", staff("manager"), null).json();
        assertTrue(manager.path("sections").path("employees").asBoolean());
        assertTrue(manager.path("sections").path("expenses").asBoolean());
    }

    // Regression: refresh tokens and (emailed) password-reset tokens were accepted as access tokens.
    @Test
    void onlyAnAccessTokenOpensTheApi() throws Exception {
        JsonNode login = loginResponse("manager", DEMO);
        expect(200, "GET", "/api/cars", login.path("token").asText());
        expect(401, "GET", "/api/cars", login.path("refreshToken").asText());

        User manager = users.findByUsername("manager").orElseThrow();
        String resetToken = jwt.generatePasswordResetToken(manager.getUsername(), manager.getPassword());
        expect(401, "GET", "/api/cars", resetToken);
    }

    // Regression: anyone could register and the registered "client" account had no purpose.
    @Test
    void thereIsNoPublicRegistration() throws Exception {
        Reply reply = call("POST", "/api/auth/register/client", null, "{\"username\":\"mallory\",\"password\":\"Passw0rd!x\"}");
        assertTrue(reply.status() == 401 || reply.status() == 403 || reply.status() == 404, "answered " + reply.status());
        assertTrue(users.findByUsername("mallory").isEmpty());
    }

    @Test
    void loginFailuresAreTidy() throws Exception {
        assertEquals(401, call("POST", "/api/auth/login", null, "{\"username\":\"manager\",\"password\":\"wrong-password\"}").status());
        assertEquals(401, call("POST", "/api/auth/login", null, "{\"username\":\"nobody\",\"password\":\"whatever-123\"}").status());
        // forgot-password answers the same for known and unknown accounts
        Reply known = call("POST", "/api/auth/forgot-password", null, "{\"usernameOrEmail\":\"manager\"}");
        Reply unknown = call("POST", "/api/auth/forgot-password", null, "{\"usernameOrEmail\":\"nobody-here\"}");
        assertEquals(200, known.status());
        assertEquals(known.status(), unknown.status());
    }

    // Regression: a client-supplied id on create overwrote an existing record.
    @Test
    void creatingARecordNeverOverwritesAnExistingOne() throws Exception {
        String manager = staff("manager");
        JsonNode first = call("GET", "/api/cars", manager, null).json().get(0);
        long firstId = first.path("id").asLong();
        String make = first.path("make").asText();

        Reply created = call("POST", "/api/cars", manager,
                "{\"id\":" + firstId + ",\"make\":\"Hijack\",\"model\":\"X\",\"licensePlate\":\"TN-0000-Z\",\"status\":\"Available\"}");
        assertEquals(200, created.status(), created.body());
        assertNotEquals(firstId, created.json().path("id").asLong());
        assertEquals(make, call("GET", "/api/cars/" + firstId, manager, null).json().path("make").asText());
    }

    @Test
    void anAdministratorCreatesALoginForAnEmployee() throws Exception {
        String manager = staff("manager");
        Reply employee = call("POST", "/api/employees", manager,
                "{\"fullName\":\"New Agent\",\"email\":\"agent@carrental.demo\",\"status\":\"Active\","
                        + "\"accessRights\":{\"cars\":true,\"dashboard\":false,\"clients\":false,\"contracts\":false,"
                        + "\"payments\":false,\"partners\":false,\"expenses\":false,\"reports\":false}}");
        assertEquals(200, employee.status(), employee.body());
        long id = employee.json().path("id").asLong();

        Reply account = call("POST", "/api/employees/" + id + "/account", manager,
                "{\"username\":\"agent\",\"password\":\"Str0ng-Pass1\"}");
        assertEquals(200, account.status(), account.body());

        String agent = login("agent", "Str0ng-Pass1");
        expect(200, "GET", "/api/cars", agent);
        expect(403, "GET", "/api/expenses", agent);
        expect(403, "POST", "/api/employees/" + id + "/account", agent); // staff cannot mint accounts
        // a username that belongs to someone else cannot be taken over
        assertEquals(409, call("POST", "/api/employees/" + id + "/account", manager,
                "{\"username\":\"reception\",\"password\":\"Str0ng-Pass1\"}").status());
    }

    // Regression: client mistakes answered 500.
    @Test
    void clientMistakesAreFourHundreds() throws Exception {
        String manager = staff("manager");
        expect(400, "GET", "/api/cars/abc", manager);
        expect(404, "GET", "/api/cars/999999", manager);
        expect(404, "GET", "/api/nothing-here", manager);
        HttpRequest plain = HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/api/cars"))
                .header("Authorization", "Bearer " + manager).header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("hello")).build();
        assertEquals(415, HTTP.send(plain, HttpResponse.BodyHandlers.ofString()).statusCode());
        Reply malformed = call("POST", "/api/cars", manager, "{broken");
        assertEquals(400, malformed.status(), malformed.body());
    }

    @Test
    void theDevelopmentKeyIsRefusedInProductionAndShortKeysEverywhere() {
        JwtProperties dev = new JwtProperties();
        dev.setSecret("dev-only-change-me-signing-key-32-bytes-minimum");
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, () -> new JwtService(dev, prod));

        JwtProperties shortKey = new JwtProperties();
        shortKey.setSecret("short");
        assertThrows(IllegalStateException.class, () -> new JwtService(shortKey, new MockEnvironment()));
        assertThrows(IllegalStateException.class, () -> new JwtService(new JwtProperties(), new MockEnvironment()));
        new JwtService(dev, new MockEnvironment()); // fine locally
    }
}
