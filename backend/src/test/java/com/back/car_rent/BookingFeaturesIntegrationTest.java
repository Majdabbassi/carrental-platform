package com.back.car_rent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bookings that cannot clash, availability, seasonal quotes, contract PDFs and the daily alerts, over real HTTP. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:carrental_booking;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef",
                "app.admin.password=Admin#12345",
                "app.demo-data=true",
                "app.demo-password=Rental@2026!"
        })
class BookingFeaturesIntegrationTest {

    private static final String DEMO = "Rental@2026!";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired Environment environment;

    // ------------------------------------------------------------------ helpers

    private record Reply(int status, String body, byte[] bytes, String contentType) {
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
        HttpResponse<byte[]> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new Reply(response.statusCode(), new String(response.body(), java.nio.charset.StandardCharsets.UTF_8),
                response.body(), response.headers().firstValue("Content-Type").orElse(""));
    }

    private String login(String username, String password) throws Exception {
        Reply reply = call("POST", "/api/auth/login", null, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertEquals(200, reply.status(), reply.body());
        return reply.json().path("token").asText();
    }

    private String manager() throws Exception {
        return login("manager", DEMO);
    }

    private static String contract(String id, String plate, LocalDate from, LocalDate to, String status) {
        return "{\"contractId\":\"" + id + "\",\"clientName\":\"Salma Hamdi\",\"clientPhone\":\"+216 23 100 004\","
                + "\"carMake\":\"Test\",\"carModel\":\"Car\",\"licensePlate\":\"" + plate + "\","
                + "\"startDate\":\"" + from + "\",\"endDate\":\"" + to + "\",\"dailyRate\":50,\"totalValue\":"
                + (50 * java.time.temporal.ChronoUnit.DAYS.between(from, to)) + ",\"status\":\"" + status + "\"}";
    }

    private static LocalDate in(int days) {
        return LocalDate.now().plusDays(days);
    }

    private static final java.util.concurrent.atomic.AtomicInteger AGENTS = new java.util.concurrent.atomic.AtomicInteger();

    /** A new employee with exactly one section ("cars", "expenses"...) and a login; returns their token. */
    private String agentWith(String section) throws Exception {
        String manager = manager();
        String name = section + AGENTS.incrementAndGet();
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

    private String carsOnlyAgent() throws Exception {
        return agentWith("cars");
    }

    // ------------------------------------------------------------------ double booking

    @Test
    void aCarCannotBeBookedTwiceForTheSameDays() throws Exception {
        String token = manager();
        String plate = "TN-2201-A";
        Reply first = call("POST", "/api/contracts", token, contract("T-A1", plate, in(100), in(105), "Reserved"));
        assertEquals(200, first.status(), first.body());

        Reply clash = call("POST", "/api/contracts", token, contract("T-A2", plate, in(103), in(108), "Reserved"));
        assertEquals(409, clash.status(), clash.body());
        assertTrue(clash.json().path("message").asText().contains("T-A1"), clash.body());

        // inside, around and across the edges of the first booking
        assertEquals(409, call("POST", "/api/contracts", token, contract("T-A3", plate, in(101), in(102), "Active")).status());
        assertEquals(409, call("POST", "/api/contracts", token, contract("T-A4", plate, in(99), in(106), "Reserved")).status());
        assertEquals(409, call("POST", "/api/contracts", token, contract("T-A5", plate, in(104), in(106), "Reserved")).status());

        // back to back is fine: returned on day 105, rented out again on day 105
        assertEquals(200, call("POST", "/api/contracts", token, contract("T-A6", plate, in(105), in(107), "Reserved")).status());
        assertEquals(200, call("POST", "/api/contracts", token, contract("T-A7", plate, in(95), in(100), "Reserved")).status());

        // a canceled or completed contract holds nothing
        assertEquals(200, call("POST", "/api/contracts", token, contract("T-A8", plate, in(101), in(104), "Canceled")).status());

        // editing a booking never clashes with itself, but moving it onto another one does
        long id = first.json().path("id").asLong();
        assertEquals(200, call("PUT", "/api/contracts/" + id, token, contract("T-A1", plate, in(100), in(104), "Reserved")).status());
        assertEquals(409, call("PUT", "/api/contracts/" + id, token, contract("T-A1", plate, in(100), in(106), "Reserved")).status());
    }

    @Test
    void whenManyPeopleBookTheSameCarAtOnceExactlyOneWins() throws Exception {
        String token = manager();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String body = contract("T-RACE-" + i, "TN-2154-G", in(200), in(204), "Reserved");
            results.add(pool.submit(() -> {
                go.await();
                return call("POST", "/api/contracts", token, body).status();
            }));
        }
        go.countDown();
        int created = 0;
        int refused = 0;
        for (Future<Integer> result : results) {
            int status = result.get();
            if (status == 200) created++;
            else if (status == 409) refused++;
            else throw new AssertionError("unexpected status " + status);
        }
        pool.shutdown();
        assertEquals(1, created, "exactly one booking may win");
        assertEquals(threads - 1, refused);
        JsonNode all = call("GET", "/api/contracts", token, null).json();
        int stored = 0;
        for (JsonNode c : all) {
            if (c.path("contractId").asText().startsWith("T-RACE-")) stored++;
        }
        assertEquals(1, stored);
    }

    @Test
    void aBookingNeedsSensibleDatesAndARealCar() throws Exception {
        String token = manager();
        assertEquals(400, call("POST", "/api/contracts", token, contract("T-B1", "TN-2201-A", in(60), in(59), "Reserved")).status());
        assertEquals(400, call("POST", "/api/contracts", token, contract("T-B2", "TN-2201-A", in(60), in(60), "Reserved")).status());
        assertEquals(400, call("POST", "/api/contracts", token, contract("T-B3", "TN-9999-Z", in(60), in(62), "Reserved")).status());
        assertEquals(400, call("POST", "/api/contracts", token, "{\"contractId\":\"T-B4\",\"status\":\"Reserved\"}").status());
        assertEquals(400, call("POST", "/api/contracts", token,
                contract("T-B5", "TN-2201-A", in(60), in(62), "Reserved").replace(in(60).toString(), "02/11/2026")).status());
    }

    // ------------------------------------------------------------------ availability

    @Test
    void availabilityListsTheCarsThatAreFree() throws Exception {
        String token = manager();
        String plate = "TN-2275-C";
        Reply booking = call("POST", "/api/contracts", token, contract("T-AV1", plate, in(300), in(305), "Reserved"));
        assertEquals(200, booking.status(), booking.body());

        assertFalse(plates(token, in(301), in(303), null).contains(plate), "booked car must not be offered");
        assertFalse(plates(token, in(299), in(301), null).contains(plate), "overlap at the start");
        assertTrue(plates(token, in(305), in(307), null).contains(plate), "free again on the return day");
        assertTrue(plates(token, in(295), in(300), null).contains(plate), "free until the pick-up day");
        assertTrue(plates(token, in(301), in(303), booking.json().path("id").asLong()).contains(plate),
                "the contract being edited does not block its own car");
        assertTrue(plates(token, in(301), in(303), null).contains("TN-2201-A"), "other cars stay available");
        assertFalse(plates(token, in(301), in(303), null).contains("TN-2260-F"), "the car in the workshop is not for rent");

        assertEquals(400, call("GET", "/api/availability/cars?from=" + in(5) + "&to=" + in(5), token, null).status());
        assertEquals(400, call("GET", "/api/availability/cars?from=yesterday&to=" + in(5), token, null).status());
        assertEquals(401, call("GET", "/api/availability/cars?from=" + in(5) + "&to=" + in(6), null, null).status());
        assertEquals(200, call("GET", "/api/availability/cars?from=" + in(5) + "&to=" + in(6), login("reception", DEMO), null).status());
        assertEquals(403, call("GET", "/api/availability/cars?from=" + in(5) + "&to=" + in(6), agentWith("expenses"), null).status());
    }

    private List<String> plates(String token, LocalDate from, LocalDate to, Long exclude) throws Exception {
        Reply reply = call("GET", "/api/availability/cars?from=" + from + "&to=" + to
                + (exclude == null ? "" : "&excludeContract=" + exclude), token, null);
        assertEquals(200, reply.status(), reply.body());
        List<String> plates = new ArrayList<>();
        reply.json().forEach(c -> plates.add(c.path("licensePlate").asText()));
        return plates;
    }

    // ------------------------------------------------------------------ pricing

    private JsonNode quote(String token, String plate, LocalDate from, LocalDate to) throws Exception {
        Reply reply = call("GET", "/api/pricing/quote?plate=" + plate + "&from=" + from + "&to=" + to, token, null);
        assertEquals(200, reply.status(), reply.body());
        return reply.json();
    }

    @Test
    void seasonsAndLongStaysChangeTheQuote() throws Exception {
        String token = manager();
        int year = LocalDate.now().getYear() + 1;
        // Toyota Corolla: 45 a day. Summer high season is x1.3 (58.50), from 15 June to 15 September.
        JsonNode plain = quote(token, "TN-2201-A", LocalDate.of(year, 2, 1), LocalDate.of(year, 2, 5));
        assertEquals(4, plain.path("days").asInt());
        assertEquals(180.0, plain.path("total").asDouble(), 0.001);
        assertEquals(1, plain.path("lines").size());

        JsonNode summer = quote(token, "TN-2201-A", LocalDate.of(year, 7, 1), LocalDate.of(year, 7, 5));
        assertEquals(234.0, summer.path("total").asDouble(), 0.001);
        assertEquals(58.5, summer.path("averageDailyRate").asDouble(), 0.001);

        // two nights before the season, two inside: two lines, 2 x 45 + 2 x 58.50
        JsonNode edge = quote(token, "TN-2201-A", LocalDate.of(year, 6, 13), LocalDate.of(year, 6, 17));
        assertEquals(2, edge.path("lines").size());
        assertEquals(207.0, edge.path("total").asDouble(), 0.001);

        // long stay: 6 days nothing, 7 days -10%, 30 days -20%
        assertEquals(270.0, quote(token, "TN-2201-A", LocalDate.of(year, 2, 1), LocalDate.of(year, 2, 7)).path("total").asDouble(), 0.001);
        JsonNode week = quote(token, "TN-2201-A", LocalDate.of(year, 2, 1), LocalDate.of(year, 2, 8));
        assertEquals(315.0, week.path("subtotal").asDouble(), 0.001);
        assertEquals(283.5, week.path("total").asDouble(), 0.001);
        assertEquals(10.0, week.path("discountPercent").asDouble(), 0.001);
        // 30 nights: 1350 less 20%
        assertEquals(1080.0, quote(token, "TN-2201-A", LocalDate.of(year, 2, 1), LocalDate.of(year, 3, 3)).path("total").asDouble(), 0.001);

        // a rule limited to luxury cars (x1.15 from 1 May to 14 June) leaves a Corolla alone but not the Mercedes (140)
        assertEquals(90.0, quote(token, "TN-2201-A", LocalDate.of(year, 5, 10), LocalDate.of(year, 5, 12)).path("total").asDouble(), 0.001);
        assertEquals(322.0, quote(token, "TN-2188-E", LocalDate.of(year, 5, 10), LocalDate.of(year, 5, 12)).path("total").asDouble(), 0.001);
    }

    @Test
    void quotesRefuseNonsense() throws Exception {
        String token = manager();
        assertEquals(400, call("GET", "/api/pricing/quote?plate=TN-2201-A&from=" + in(10) + "&to=" + in(10), token, null).status());
        assertEquals(400, call("GET", "/api/pricing/quote?plate=NOPE&from=" + in(10) + "&to=" + in(12), token, null).status());
        assertEquals(400, call("GET", "/api/pricing/quote?plate=TN-2201-A&from=abc&to=" + in(12), token, null).status());
        assertEquals(401, call("GET", "/api/pricing/quote?plate=TN-2201-A&from=" + in(10) + "&to=" + in(12), null, null).status());
        assertEquals(200, call("GET", "/api/pricing/quote?plate=TN-2201-A&from=" + in(10) + "&to=" + in(12), login("reception", DEMO), null).status());
    }

    @Test
    void onlyAdministratorsManagePricingRules() throws Exception {
        String reception = login("reception", DEMO);
        assertEquals(403, call("GET", "/api/pricing-rules", reception, null).status());
        assertEquals(403, call("POST", "/api/pricing-rules", reception, "{}").status());
        assertEquals(403, call("DELETE", "/api/pricing-rules/1", reception, null).status());
        assertEquals(401, call("GET", "/api/pricing-rules", null, null).status());

        String manager = manager();
        assertEquals(200, call("GET", "/api/pricing-rules", manager, null).status());
        Reply created = call("POST", "/api/pricing-rules", manager,
                "{\"name\":\"Eid\",\"type\":\"SEASON\",\"startDate\":\"2031-04-01\",\"endDate\":\"2031-04-05\",\"multiplier\":1.5}");
        assertEquals(200, created.status(), created.body());
        long id = created.json().path("id").asLong();
        assertEquals(200, call("PUT", "/api/pricing-rules/" + id, manager,
                "{\"name\":\"Eid\",\"type\":\"SEASON\",\"startDate\":\"2031-04-01\",\"endDate\":\"2031-04-06\",\"multiplier\":1.6}").status());
        // the new rule is used by quotes
        assertEquals(2 * 45 * 1.6, quote(manager, "TN-2201-A", LocalDate.of(2031, 4, 2), LocalDate.of(2031, 4, 4)).path("total").asDouble(), 0.001);
        // an overlapping milder rule does not stack: the one that moves the price most wins on each night
        Reply mild = call("POST", "/api/pricing-rules", manager,
                "{\"name\":\"Mild\",\"type\":\"SEASON\",\"startDate\":\"2031-04-01\",\"endDate\":\"2031-04-06\",\"multiplier\":1.1}");
        assertEquals(200, mild.status(), mild.body());
        assertEquals(2 * 45 * 1.6, quote(manager, "TN-2201-A", LocalDate.of(2031, 4, 2), LocalDate.of(2031, 4, 4)).path("total").asDouble(), 0.001);
        assertEquals(204, call("DELETE", "/api/pricing-rules/" + mild.json().path("id").asLong(), manager, null).status());
        assertEquals(204, call("DELETE", "/api/pricing-rules/" + id, manager, null).status());
        assertEquals(404, call("DELETE", "/api/pricing-rules/" + id, manager, null).status());
        assertEquals(404, call("PUT", "/api/pricing-rules/" + id, manager,
                "{\"name\":\"x\",\"type\":\"LONG_STAY\",\"minDays\":3,\"discountPercent\":5}").status());

        // broken rules are refused
        for (String bad : new String[]{
                "{\"name\":\"\",\"type\":\"SEASON\",\"startDate\":\"2031-04-01\",\"endDate\":\"2031-04-05\",\"multiplier\":1.5}",
                "{\"name\":\"a\",\"type\":\"SEASON\",\"startDate\":\"2031-04-05\",\"endDate\":\"2031-04-01\",\"multiplier\":1.5}",
                "{\"name\":\"a\",\"type\":\"SEASON\",\"startDate\":\"2031-04-01\",\"endDate\":\"2031-04-05\",\"multiplier\":0}",
                "{\"name\":\"a\",\"type\":\"LONG_STAY\",\"minDays\":1,\"discountPercent\":10}",
                "{\"name\":\"a\",\"type\":\"LONG_STAY\",\"minDays\":7,\"discountPercent\":100}",
                "{\"name\":\"a\",\"type\":\"WEEKEND\"}"}) {
            assertEquals(400, call("POST", "/api/pricing-rules", manager, bad).status(), bad);
        }
    }

    // ------------------------------------------------------------------ PDF

    private String pdfText(byte[] bytes) throws Exception {
        PdfReader reader = new PdfReader(bytes);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder text = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(extractor.getTextFromPage(page)).append('\n');
        }
        return text.toString();
    }

    @Test
    void aContractIsAPrintableAgreementAndAnInvoice() throws Exception {
        String token = manager();
        JsonNode booking = call("POST", "/api/contracts", token, contract("T-PDF1", "TN-2342-D", in(400), in(403), "Reserved")).json();
        long id = booking.path("id").asLong();

        Reply agreement = call("GET", "/api/contracts/" + id + "/pdf", token, null);
        assertEquals(200, agreement.status());
        assertTrue(agreement.contentType().startsWith("application/pdf"), agreement.contentType());
        assertEquals("%PDF", new String(agreement.bytes(), 0, 4));
        String text = pdfText(agreement.bytes());
        assertTrue(text.contains("Rental agreement"), text);
        assertTrue(text.contains("T-PDF1"), text);
        assertTrue(text.contains("Salma Hamdi"), text);
        assertTrue(text.contains("TN-2342-D"), text);
        assertTrue(text.contains("CONDITIONS OF RENTAL"), text);
        assertTrue(text.contains("150.00"), text); // 3 days x 50

        Reply invoice = call("GET", "/api/contracts/" + id + "/pdf?type=invoice", token, null);
        assertEquals(200, invoice.status());
        String invoiceText = pdfText(invoice.bytes());
        assertTrue(invoiceText.contains("Invoice"), invoiceText);
        assertTrue(invoiceText.contains("INV-T-PDF1"), invoiceText);
        assertTrue(invoiceText.contains("150.00"), invoiceText);

        assertEquals(400, call("GET", "/api/contracts/" + id + "/pdf?type=receipt", token, null).status());
        assertEquals(404, call("GET", "/api/contracts/999999/pdf", token, null).status());
        assertEquals(401, call("GET", "/api/contracts/" + id + "/pdf", null, null).status());
        assertEquals(200, call("GET", "/api/contracts/" + id + "/pdf", login("accountant", DEMO), null).status());
        assertEquals(403, call("GET", "/api/contracts/" + id + "/pdf", carsOnlyAgent(), null).status());
    }

    // ------------------------------------------------------------------ alerts

    private JsonNode alertWith(JsonNode alerts, String fragment) {
        for (JsonNode alert : alerts) {
            if (alert.path("refKey").asText().contains(fragment)) return alert;
        }
        return null;
    }

    @Test
    void theDailyCheckFlagsWhatNeedsAttentionAndClearsItself() throws Exception {
        String token = manager();
        JsonNode alerts = call("GET", "/api/alerts", token, null).json();

        JsonNode overdue = alertWith(alerts, "overdue:CT-1007");
        assertNotNull(overdue, "a rental past its return date: " + alerts);
        assertEquals("CRITICAL", overdue.path("severity").asText());
        assertEquals("OVERDUE_RETURN", overdue.path("type").asText());
        assertNotNull(alertWith(alerts, "insurance:TN-2188-E"), "insurance expiring in 12 days");
        assertEquals("WARNING", alertWith(alerts, "insurance:TN-2188-E").path("severity").asText());
        assertNotNull(alertWith(alerts, "registration:TN-2154-G"), "registration expiring in 25 days");
        assertEquals("CRITICAL", alertWith(alerts, "service:TN-2260-F").path("severity").asText(), "service overdue");
        assertNull(alertWith(alerts, "insurance:TN-2201-A"), "a car with 8 months of insurance left is fine");
        assertEquals("CRITICAL", alerts.get(0).path("severity").asText(), "most urgent first");

        // dismissing hides it, and it stays hidden after the next check while the cause remains
        long id = overdue.path("id").asLong();
        assertEquals(204, call("POST", "/api/alerts/" + id + "/dismiss", token, null).status());
        assertEquals(404, call("POST", "/api/alerts/999999/dismiss", token, null).status());
        assertNull(alertWith(call("GET", "/api/alerts", token, null).json(), "overdue:CT-1007"));
        assertEquals(200, call("POST", "/api/alerts/refresh", token, null).status());
        assertNull(alertWith(call("GET", "/api/alerts", token, null).json(), "overdue:CT-1007"));

        // once the cause is gone (the car came back) the alert is gone for good: closing the contract...
        long contractId = 0;
        for (JsonNode c : call("GET", "/api/contracts", token, null).json()) {
            if (c.path("contractId").asText().equals("CT-1007")) contractId = c.path("id").asLong();
        }
        JsonNode contract = call("GET", "/api/contracts/" + contractId, token, null).json();
        ((com.fasterxml.jackson.databind.node.ObjectNode) contract).put("status", "Completed");
        assertEquals(200, call("PUT", "/api/contracts/" + contractId, token, contract.toString()).status());
        assertEquals(200, call("POST", "/api/alerts/refresh", token, null).status());
        // ...and a brand new problem appears on the next check
        JsonNode golf = null;
        for (JsonNode c : call("GET", "/api/cars", token, null).json()) {
            if (c.path("licensePlate").asText().equals("TN-2099-H")) golf = c;
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) golf).put("insuranceExpiry", LocalDate.now().minusDays(2).toString());
        assertEquals(200, call("PUT", "/api/cars/" + golf.path("id").asLong(), token, golf.toString()).status());
        call("POST", "/api/alerts/refresh", token, null);
        JsonNode after = call("GET", "/api/alerts", token, null).json();
        JsonNode expired = alertWith(after, "insurance:TN-2099-H");
        assertNotNull(expired);
        assertEquals("CRITICAL", expired.path("severity").asText());
    }

    @Test
    void alertsFollowTheDashboardPermission() throws Exception {
        assertEquals(401, call("GET", "/api/alerts", null, null).status());
        assertEquals(200, call("GET", "/api/alerts", login("reception", DEMO), null).status());
        String carsOnly = carsOnlyAgent();
        assertEquals(403, call("GET", "/api/alerts", carsOnly, null).status());
        assertEquals(403, call("POST", "/api/alerts/refresh", carsOnly, null).status());
        assertEquals(403, call("POST", "/api/alerts/1/dismiss", carsOnly, null).status());
    }

    private static void assertNull(Object value, String message) {
        org.junit.jupiter.api.Assertions.assertNull(value, message);
    }

    private static void assertNull(Object value) {
        org.junit.jupiter.api.Assertions.assertNull(value);
    }
}
