package com.back.car_rent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Payments and Reports sections: who may use them, how payments drive a contract's status, and the report numbers. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:carrental_payments;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef",
                "app.admin.password=Admin#12345",
                "app.demo-data=true",
                "app.demo-password=Rental@2026!"
        })
class PaymentsReportsIntegrationTest {

    private static final String DEMO = "Rental@2026!";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired Environment environment;

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

    private String login(String username, String password) throws Exception {
        Reply reply = call("POST", "/api/auth/login", null, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertEquals(200, reply.status(), reply.body());
        return reply.json().path("token").asText();
    }

    private String manager() throws Exception {
        return login("manager", DEMO);
    }

    /** A new employee with exactly one section and a login; returns their token. */
    private String agentWith(String section) throws Exception {
        String manager = manager();
        String name = section + "-agent" + SEQ.incrementAndGet();
        StringBuilder rights = new StringBuilder("{");
        for (String s : new String[]{"dashboard", "cars", "clients", "contracts", "payments", "partners", "expenses", "reports"}) {
            rights.append("\"").append(s).append("\":").append(s.equals(section)).append(",");
        }
        rights.setLength(rights.length() - 1);
        rights.append("}");
        Reply employee = call("POST", "/api/employees", manager,
                "{\"fullName\":\"" + name + "\",\"email\":\"" + name + "@carrental.demo\",\"status\":\"Active\",\"accessRights\":" + rights + "}");
        assertEquals(200, employee.status(), employee.body());
        long id = employee.json().path("id").asLong();
        assertEquals(200, call("POST", "/api/employees/" + id + "/account", manager,
                "{\"username\":\"" + name + "\",\"password\":\"Str0ng-Pass1\"}").status());
        return login(name, "Str0ng-Pass1");
    }

    /** A car of its own, so these tests never clash with the demo bookings. */
    private String newCar(String token, String category) throws Exception {
        String plate = "PAY-" + SEQ.incrementAndGet();
        Reply car = call("POST", "/api/cars", token, "{\"make\":\"Test\",\"model\":\"Car\",\"licensePlate\":\"" + plate
                + "\",\"category\":\"" + category + "\",\"status\":\"Available\",\"dailyRate\":50}");
        assertEquals(200, car.status(), car.body());
        return plate;
    }

    /** Creates a contract and returns its database id. */
    private long newContract(String token, String plate, String from, String to, double total, String status) throws Exception {
        String number = "CT-P" + SEQ.incrementAndGet();
        Reply c = call("POST", "/api/contracts", token, "{\"contractId\":\"" + number + "\",\"clientName\":\"Karim Bouzid\","
                + "\"licensePlate\":\"" + plate + "\",\"carMake\":\"Test\",\"carModel\":\"Car\",\"startDate\":\"" + from
                + "\",\"endDate\":\"" + to + "\",\"dailyRate\":50,\"totalValue\":" + total + ",\"status\":\"" + status + "\"}");
        assertEquals(200, c.status(), c.body());
        return c.json().path("id").asLong();
    }

    private Reply pay(String token, long contractRef, double amount, String date) throws Exception {
        return call("POST", "/api/payments", token, "{\"contractRef\":" + contractRef + ",\"amount\":" + amount
                + ",\"method\":\"Cash\",\"date\":\"" + date + "\",\"contractId\":\"forged\",\"clientName\":\"forged\"}");
    }

    private String paymentStatus(String token, long contractRef) throws Exception {
        return call("GET", "/api/contracts/" + contractRef, token, null).json().path("paymentStatus").asText();
    }

    // ------------------------------------------------------------------ who may use the sections

    @Test
    void paymentsAndReportsFollowTheAccessRights() throws Exception {
        String reception = login("reception", DEMO);   // dashboard, cars, clients, contracts
        String accountant = login("accountant", DEMO); // dashboard, contracts, payments, expenses, reports
        assertEquals(403, call("GET", "/api/payments", reception, null).status());
        assertEquals(403, call("POST", "/api/payments", reception, "{\"contractRef\":1,\"amount\":1,\"date\":\"2030-01-01\"}").status());
        assertEquals(403, call("GET", "/api/reports/summary", reception, null).status());
        assertEquals(200, call("GET", "/api/payments", accountant, null).status());
        assertEquals(200, call("GET", "/api/reports/summary", accountant, null).status());

        // payments only: may read contracts to pick one, but not change them; no reports
        String cashier = agentWith("payments");
        assertEquals(200, call("GET", "/api/payments", cashier, null).status());
        assertEquals(200, call("GET", "/api/contracts", cashier, null).status());
        assertEquals(403, call("POST", "/api/contracts", cashier, "{\"contractId\":\"X\"}").status());
        assertEquals(403, call("GET", "/api/reports/summary", cashier, null).status());

        // reports only: the report, nothing else
        String analyst = agentWith("reports");
        assertEquals(200, call("GET", "/api/reports/summary", analyst, null).status());
        assertEquals(403, call("GET", "/api/payments", analyst, null).status());
        assertEquals(403, call("GET", "/api/contracts", analyst, null).status());

        // the menu shows exactly those sections
        JsonNode menu = call("GET", "/api/auth/me", cashier, null).json().path("sections");
        assertTrue(menu.path("payments").asBoolean());
        assertTrue(!menu.path("reports").asBoolean());
    }

    // ------------------------------------------------------------------ payments drive the contract status

    @Test
    void paymentsDecideTheContractsPaymentStatus() throws Exception {
        String token = manager();
        String plate = newCar(token, "Sedan");
        long contract = newContract(token, plate, "2034-03-01", "2034-03-11", 500, "Reserved");
        assertEquals("Pending", paymentStatus(token, contract));

        Reply first = pay(token, contract, 200, "2034-02-20");
        assertEquals(200, first.status(), first.body());
        assertTrue(first.json().path("contractId").asText().startsWith("CT-P"), "the contract number comes from the contract");
        assertEquals("Karim Bouzid", first.json().path("clientName").asText());
        assertTrue(first.json().path("paymentId").asText().startsWith("PY-"));
        assertEquals("Partial", paymentStatus(token, contract));

        assertEquals(200, pay(token, contract, 300, "2034-03-01").status());
        assertEquals("Paid", paymentStatus(token, contract));

        // nothing more can be paid, and nonsense is refused
        assertEquals(400, pay(token, contract, 1, "2034-03-02").status());
        assertEquals(400, pay(token, contract, 0, "2034-03-02").status());
        assertEquals(400, pay(token, contract, -5, "2034-03-02").status());
        assertEquals(400, call("POST", "/api/payments", token, "{\"amount\":10,\"date\":\"2034-03-02\"}").status());
        assertEquals(400, pay(token, 999999, 10, "2034-03-02").status());
        assertEquals(400, pay(token, contract, 10, "next week").status());

        // a contract with payments is canceled, not deleted
        assertEquals(409, call("DELETE", "/api/contracts/" + contract, token, null).status());

        // raising the total re-opens the balance
        Reply edit = call("PUT", "/api/contracts/" + contract, token, "{\"contractId\":\"CT-EDIT\",\"clientName\":\"Karim Bouzid\","
                + "\"licensePlate\":\"" + plate + "\",\"startDate\":\"2034-03-01\",\"endDate\":\"2034-03-11\","
                + "\"totalValue\":600,\"status\":\"Reserved\",\"paymentStatus\":\"Paid\"}");
        assertEquals(200, edit.status(), edit.body());
        assertEquals("Partial", edit.json().path("paymentStatus").asText(), "the status is computed, not taken from the form");

        // correcting and removing payments moves the status back
        long firstId = first.json().path("id").asLong();
        Reply corrected = call("PUT", "/api/payments/" + firstId, token,
                "{\"contractRef\":" + contract + ",\"amount\":300,\"method\":\"Card\",\"date\":\"2034-02-20\"}");
        assertEquals(200, corrected.status(), corrected.body());
        assertEquals("Paid", paymentStatus(token, contract));
        assertEquals(400, call("PUT", "/api/payments/" + firstId, token,
                "{\"contractRef\":" + contract + ",\"amount\":301,\"method\":\"Card\",\"date\":\"2034-02-20\"}").status());
        assertEquals(204, call("DELETE", "/api/payments/" + firstId, token, null).status());
        assertEquals("Partial", paymentStatus(token, contract));
        assertEquals(1, call("GET", "/api/payments?contractRef=" + contract, token, null).json().size());

        // nothing is due on a canceled contract
        long canceled = newContract(token, plate, "2034-05-01", "2034-05-03", 100, "Canceled");
        assertEquals(400, pay(token, canceled, 50, "2034-05-01").status());
    }

    @Test
    void twoPeoplePayingAtOnceCannotOverpay() throws Exception {
        String token = manager();
        long contract = newContract(token, newCar(token, "Sedan"), "2034-07-01", "2034-07-11", 500, "Reserved");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return pay(token, contract, 100, "2034-06-30").status();
            }));
        }
        go.countDown();
        int accepted = 0;
        for (Future<Integer> result : results) {
            int status = result.get();
            if (status == 200) accepted++;
            else if (status != 400) throw new AssertionError("unexpected status " + status);
        }
        pool.shutdown();
        assertEquals(5, accepted, "500 to pay in 100s: exactly five payments fit");
        double paid = 0;
        for (JsonNode p : call("GET", "/api/payments?contractRef=" + contract, token, null).json()) {
            paid += p.path("amount").asDouble();
        }
        assertEquals(500, paid, 0.001);
        assertEquals("Paid", paymentStatus(token, contract));
    }

    // ------------------------------------------------------------------ the report

    @Test
    void theReportCountsWhatHappenedInThePeriod() throws Exception {
        String token = manager();
        String van = newCar(token, "Van");
        // January 2036: a 10-night rental from the 10th (billed 600), 250 paid in January, 100 in February
        long jan = newContract(token, van, "2036-01-10", "2036-01-20", 600, "Completed");
        assertEquals(200, pay(token, jan, 250, "2036-01-12").status());
        assertEquals(200, pay(token, jan, 100, "2036-02-03").status());
        // February: a rental that runs into March (6 of its 10 nights are in the period: 2036 is a leap year), unpaid
        long feb = newContract(token, van, "2036-02-24", "2036-03-05", 400, "Reserved");
        // a canceled rental counts for nothing
        newContract(token, van, "2036-02-01", "2036-02-05", 999, "Canceled");
        assertEquals(200, call("POST", "/api/expenses", token,
                "{\"expenseId\":\"EX-R1\",\"category\":\"Fuel\",\"amount\":80,\"date\":\"2036-01-15\"}").status());

        JsonNode report = call("GET", "/api/reports/summary?from=2036-01&to=2036-02", token, null).json();
        assertEquals("2036-01", report.path("from").asText());
        JsonNode january = report.path("months").get(0);
        JsonNode february = report.path("months").get(1);
        assertEquals("2036-01", january.path("month").asText());
        assertEquals(600, january.path("billed").asDouble(), 0.001);
        assertEquals(250, january.path("collected").asDouble(), 0.001);
        assertEquals(80, january.path("expenses").asDouble(), 0.001);
        assertEquals(170, january.path("net").asDouble(), 0.001);
        assertEquals(400, february.path("billed").asDouble(), 0.001);
        assertEquals(100, february.path("collected").asDouble(), 0.001);
        assertEquals(1000, report.path("totals").path("billed").asDouble(), 0.001);
        assertEquals(350, report.path("totals").path("collected").asDouble(), 0.001);

        JsonNode use = null;
        for (JsonNode car : report.path("fleet")) {
            if (car.path("plate").asText().equals(van)) use = car;
        }
        assertEquals(10 + 6, use.path("nightsRented").asLong(), "nights outside the period are not counted");
        assertEquals(31 + 29, use.path("nightsInPeriod").asLong());
        assertEquals(26.7, use.path("utilizationPercent").asDouble(), 0.001); // 16 of 60 nights
        assertEquals(1000, use.path("billed").asDouble(), 0.001);

        boolean vanCategory = false;
        for (JsonNode category : report.path("categories")) {
            if (category.path("category").asText().equals("Van")) {
                vanCategory = true;
                assertEquals(2, category.path("rentals").asInt());
                assertEquals(1000, category.path("billed").asDouble(), 0.001);
            }
        }
        assertTrue(vanCategory);

        double janLeft = -1, febLeft = -1;
        for (JsonNode debtor : report.path("debtors")) {
            if (debtor.path("contractRef").asLong() == jan) janLeft = debtor.path("left").asDouble();
            if (debtor.path("contractRef").asLong() == feb) febLeft = debtor.path("left").asDouble();
        }
        assertEquals(250, janLeft, 0.001);
        assertEquals(400, febLeft, 0.001);

        // bad ranges are refused
        assertEquals(400, call("GET", "/api/reports/summary?from=2036-03&to=2036-01", token, null).status());
        assertEquals(400, call("GET", "/api/reports/summary?from=2030-01&to=2036-01", token, null).status());
        assertEquals(400, call("GET", "/api/reports/summary?from=January", token, null).status());
    }

    @Test
    void theDemoAgencyStartsWithPaymentsThatMatchItsContracts() throws Exception {
        String token = manager();
        JsonNode payments = call("GET", "/api/payments", token, null).json();
        assertTrue(payments.size() >= 4, "paid and partly paid demo contracts have payments");
        for (JsonNode c : call("GET", "/api/contracts", token, null).json()) {
            if (!c.path("contractId").asText().startsWith("CT-1")) continue; // demo contracts only
            double paid = 0;
            for (JsonNode p : payments) {
                if (p.path("contractRef").asLong() == c.path("id").asLong()) paid += p.path("amount").asDouble();
            }
            String expected = paid == 0 ? "Pending" : paid + 0.005 >= c.path("totalValue").asDouble() ? "Paid" : "Partial";
            assertEquals(expected, c.path("paymentStatus").asText(), c.path("contractId").asText());
        }
    }
}
