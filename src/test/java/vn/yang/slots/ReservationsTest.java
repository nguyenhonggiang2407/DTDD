package vn.yang.slots;

import static org.junit.jupiter.api.Assertions.*;

import com.jayway.jsonpath.JsonPath;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.yang.slots.Contracts.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:yangslots-tests;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;DATABASE_TO_UPPER=FALSE",
      "server.address=127.0.0.1"
    })
@Import(ReservationsTest.TimeConfig.class)
class ReservationsTest {
  static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  @Autowired BookingService service;
  @Autowired Sessions sessions;
  @Autowired JdbcTemplate db;
  @Autowired MutableClock clock;
  @Autowired Environment env;
  static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    volatile Instant value = NOW;

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return value;
    }
  }

  record Browser(String cookie, String csrf) {}

  @BeforeEach
  void reset() {
    clock.value = NOW;
    db.update("DELETE FROM workspaces");
  }

  String base() {
    return "http://127.0.0.1:" + env.getProperty("local.server.port");
  }

  Browser browser() throws Exception {
    var r = call(null, "/api/workspace", "GET", null, Map.of());
    return new Browser(
        r.headers().firstValue("set-cookie").orElseThrow().split(";")[0],
        JsonPath.read(r.body(), "$.csrf"));
  }

  HttpResponse<String> call(
      Browser b, String path, String method, String body, Map<String, String> headers)
      throws Exception {
    var builder = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(15));
    if (b != null) builder.header("Cookie", b.cookie()).header("X-CSRF-Token", b.csrf());
    if (body != null) builder.header("Content-Type", "application/json");
    headers.forEach(builder::setHeader);
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  String owner() {
    return sessions.create().workspace().id();
  }

  ResourceView resource(String owner, int capacity) {
    return service.createResource(owner, new ResourceInput("Phòng thử nghiệm", capacity));
  }

  HoldInput input(String id, int start, int end, int units) {
    return new HoldInput(
        id,
        "Lịch nhóm",
        Rules.instant(Rules.at(LocalDate.of(2030, 1, 2), start, 0)),
        Rules.instant(Rules.at(LocalDate.of(2030, 1, 2), end, 0)),
        units);
  }

  String holdJson(String id) {
    return "{\"resourceId\":\""
        + id
        + "\",\"title\":\"HTTP"
        + " hold\",\"startsAt\":\"2030-01-02T02:00:00Z\",\"endsAt\":\"2030-01-02T03:00:00Z\",\"units\":1}";
  }

  @Test
  void realHttpFlowHoldsConfirmsCancelsAndReleasesCapacity() throws Exception {
    Browser b = browser();
    var created =
        call(b, "/api/resources", "POST", "{\"name\":\"Meeting\",\"capacity\":1}", Map.of());
    assertEquals(201, created.statusCode(), created.body());
    String id = JsonPath.read(created.body(), "$.resource.id");
    var held =
        call(b, "/api/holds", "POST", holdJson(id), Map.of("Idempotency-Key", "http-hold-key-001"));
    assertEquals(201, held.statusCode(), held.body());
    String booking = JsonPath.read(held.body(), "$.reservation.id");
    assertEquals(
        409,
        call(b, "/api/holds", "POST", holdJson(id), Map.of("Idempotency-Key", "http-hold-key-002"))
            .statusCode());
    var confirm =
        call(b, "/api/reservations/" + booking + "/confirm", "POST", "{\"version\":1}", Map.of());
    assertEquals(200, confirm.statusCode());
    assertEquals("CONFIRMED", JsonPath.read(confirm.body(), "$.reservation.status"));
    assertEquals(
        409,
        call(b, "/api/reservations/" + booking + "/cancel", "POST", "{\"version\":1}", Map.of())
            .statusCode());
    assertEquals(
        200,
        call(b, "/api/reservations/" + booking + "/cancel", "POST", "{\"version\":2}", Map.of())
            .statusCode());
    assertEquals(
        201,
        call(b, "/api/holds", "POST", holdJson(id), Map.of("Idempotency-Key", "http-hold-key-002"))
            .statusCode());
  }

  @Test
  void concurrentHttpReservationsNeverExceedCapacity() throws Exception {
    Browser b = browser();
    var r = call(b, "/api/resources", "POST", "{\"name\":\"Shared\",\"capacity\":4}", Map.of());
    String id = JsonPath.read(r.body(), "$.resource.id");
    ExecutorService pool = Executors.newFixedThreadPool(12);
    try {
      List<Future<Integer>> futures = new ArrayList<>();
      CountDownLatch gate = new CountDownLatch(1);
      for (int i = 0; i < 20; i++) {
        final int index = i;
        futures.add(
            pool.submit(
                () -> {
                  gate.await();
                  return call(
                          b,
                          "/api/holds",
                          "POST",
                          holdJson(id),
                          Map.of("Idempotency-Key", "race-request-" + index))
                      .statusCode();
                }));
      }
      gate.countDown();
      int success = 0, conflict = 0;
      for (Future<Integer> future : futures) {
        int code = future.get(20, TimeUnit.SECONDS);
        if (code == 201) success++;
        else if (code == 409) conflict++;
        else fail("Unexpected HTTP " + code);
      }
      assertEquals(4, success);
      assertEquals(16, conflict);
      assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentSameKeyReturnsOneReservation() throws Exception {
    Browser b = browser();
    var r = call(b, "/api/resources", "POST", "{\"name\":\"Shared\",\"capacity\":4}", Map.of());
    String id = JsonPath.read(r.body(), "$.resource.id");
    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      List<Future<HttpResponse<String>>> futures = new ArrayList<>();
      for (int i = 0; i < 8; i++)
        futures.add(
            pool.submit(
                () ->
                    call(
                        b,
                        "/api/holds",
                        "POST",
                        holdJson(id),
                        Map.of("Idempotency-Key", "one-intent-001"))));
      Set<String> ids = new HashSet<>();
      int created = 0;
      for (var future : futures) {
        var response = future.get(20, TimeUnit.SECONDS);
        assertTrue(response.statusCode() == 200 || response.statusCode() == 201, response.body());
        if (response.statusCode() == 201) created++;
        ids.add(JsonPath.read(response.body(), "$.reservation.id"));
      }
      assertEquals(1, created);
      assertEquals(1, ids.size());
      assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM idempotency", Integer.class));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void overlapSweepAcceptsNonConcurrentLoadsAndAdjacentIntervals() {
    String owner = owner();
    ResourceView r = resource(owner, 2);
    service.hold(owner, input(r.id(), 8, 9, 1), "sweep-key-001");
    service.hold(owner, input(r.id(), 10, 11, 1), "sweep-key-002");
    assertDoesNotThrow(() -> service.hold(owner, input(r.id(), 8, 11, 1), "sweep-key-003"));
    assertDoesNotThrow(() -> service.hold(owner, input(r.id(), 9, 10, 1), "sweep-key-004"));
    var blocked =
        assertThrows(
            Problem.class, () -> service.hold(owner, input(r.id(), 9, 11, 1), "sweep-key-005"));
    assertEquals(409, blocked.status());
  }

  @Test
  void expiredHoldsReleaseCapacityWithoutScheduledJobsAndCannotConfirm() {
    String owner = owner();
    ResourceView r = resource(owner, 1);
    var first = service.hold(owner, input(r.id(), 9, 10, 1), "expires-key-001").reservation();
    clock.value = NOW.plusMillis(Rules.HOLD_TTL);
    assertEquals("EXPIRED", service.reservation(owner, first.id()).status());
    assertEquals(
        410, assertThrows(Problem.class, () -> service.confirm(owner, first.id(), 1)).status());
    assertDoesNotThrow(() -> service.hold(owner, input(r.id(), 9, 10, 1), "expires-key-002"));
    assertEquals(
        first.id(),
        service.hold(owner, input(r.id(), 9, 10, 1), "expires-key-001").reservation().id());
    clock.value = NOW.plus(Duration.ofDays(2));
    assertEquals(
        "EXPIRED",
        service.hold(owner, input(r.id(), 9, 10, 1), "expires-key-001").reservation().status());
  }

  @Test
  void replayKeepsCancelledStateAndRejectsPayloadConflicts() {
    String owner = owner();
    ResourceView r = resource(owner, 1);
    HoldInput input = input(r.id(), 9, 10, 1);
    var first = service.hold(owner, input, "replay-key-001").reservation();
    service.cancel(owner, first.id(), 1);
    Accepted replay = service.hold(owner, input, "replay-key-001");
    assertTrue(replay.replayed());
    assertEquals("CANCELLED", replay.reservation().status());
    assertEquals(
        409,
        assertThrows(
                Problem.class,
                () ->
                    service.hold(
                        owner,
                        new HoldInput(r.id(), "Changed", input.startsAt(), input.endsAt(), 1),
                        "replay-key-001"))
            .status());
    assertDoesNotThrow(() -> service.hold(owner, input, "replay-key-002"));
  }

  @Test
  void privateScopesCoverBookingActionsAvailabilityAndExports() throws Exception {
    Browser a = browser(), b = browser();
    var created =
        call(a, "/api/resources", "POST", "{\"name\":\"Private\",\"capacity\":1}", Map.of());
    String resource = JsonPath.read(created.body(), "$.resource.id");
    var accepted =
        call(
            a,
            "/api/holds",
            "POST",
            holdJson(resource),
            Map.of("Idempotency-Key", "private-hold-001"));
    String id = JsonPath.read(accepted.body(), "$.reservation.id");
    for (String path :
        List.of(
            "/api/reservations/" + id,
            "/api/availability?resourceId=" + resource + "&day=2030-01-02",
            "/api/export.csv?resourceId=" + resource,
            "/api/calendar.ics?resourceId=" + resource))
      assertEquals(404, call(b, path, "GET", null, Map.of()).statusCode(), path);
    for (String action : List.of("confirm", "cancel"))
      assertEquals(
          404,
          call(b, "/api/reservations/" + id + "/" + action, "POST", "{\"version\":1}", Map.of())
              .statusCode());
    assertEquals(
        404,
        call(b, "/api/resources/" + resource, "DELETE", "{\"version\":1}", Map.of()).statusCode());
    assertEquals(
        404,
        call(
                b,
                "/api/holds",
                "POST",
                holdJson(resource),
                Map.of("Idempotency-Key", "other-owner-001"))
            .statusCode());
  }

  @Test
  void cookieCsrfOriginAndCrossSiteBootstrapAreEnforced() throws Exception {
    var bootstrap = call(null, "/api/workspace", "GET", null, Map.of());
    assertTrue(
        bootstrap
            .headers()
            .firstValue("set-cookie")
            .orElseThrow()
            .contains("HttpOnly; SameSite=Strict"));
    Browser b = browser();
    assertEquals(401, call(null, "/api/resources", "GET", null, Map.of()).statusCode());
    assertEquals(
        403,
        call(
                new Browser(b.cookie(), ""),
                "/api/resources",
                "POST",
                "{\"name\":\"No\",\"capacity\":1}",
                Map.of())
            .statusCode());
    assertEquals(
        403,
        call(
                b,
                "/api/resources",
                "POST",
                "{\"name\":\"No\",\"capacity\":1}",
                Map.of("Origin", "https://evil.invalid"))
            .statusCode());
    assertEquals(
        403,
        call(b, "/api/workspace", "GET", null, Map.of("Sec-Fetch-Site", "cross-site"))
            .statusCode());
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM resources", Integer.class));
  }

  @Test
  void hostStaticAllowlistBodyBoundsAndJsonTypesAreChecked() throws Exception {
    try (Socket socket =
        new Socket("127.0.0.1", Integer.parseInt(env.getProperty("local.server.port")))) {
      socket
          .getOutputStream()
          .write(
              "GET /health HTTP/1.1\r\nHost: evil.invalid\r\nConnection: close\r\n\r\n"
                  .getBytes(StandardCharsets.US_ASCII));
      assertTrue(
          new BufferedReader(new InputStreamReader(socket.getInputStream()))
              .readLine()
              .contains("400"));
    }
    for (String path :
        List.of("/data/yangslots.mv.db", "/upstream/hotel_search.py", "/application.properties"))
      assertEquals(404, call(null, path, "GET", null, Map.of()).statusCode());
    Browser b = browser();
    assertEquals(
        415,
        call(b, "/api/resources", "POST", "{}", Map.of("Content-Type", "text/plain")).statusCode());
    assertEquals(413, call(b, "/api/resources", "POST", "a".repeat(270000), Map.of()).statusCode());
    assertEquals(
        400,
        call(b, "/api/resources", "POST", "{\"name\":\"Float\",\"capacity\":1.5}", Map.of())
            .statusCode());
    assertEquals(
        400,
        call(
                b,
                "/api/resources",
                "POST",
                "{\"name\":\"Unknown\",\"capacity\":1,\"sql\":\"delete\"}",
                Map.of())
            .statusCode());
  }

  @Test
  void quotaRollbackDoesNotConsumeFailedRequestKeys() {
    String owner = owner();
    ResourceView r = resource(owner, 1);
    var first = service.hold(owner, input(r.id(), 9, 10, 1), "rollback-key-001").reservation();
    assertThrows(
        Problem.class, () -> service.hold(owner, input(r.id(), 9, 10, 1), "rollback-key-002"));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM idempotency", Integer.class));
    service.cancel(owner, first.id(), 1);
    assertDoesNotThrow(() -> service.hold(owner, input(r.id(), 9, 10, 1), "rollback-key-002"));
    for (int i = 0; i < 9; i++) resource(owner, 1);
    assertEquals(409, assertThrows(Problem.class, () -> resource(owner, 1)).status());
  }

  @Test
  void deletionCascadesBookingsAndRetainsIdempotencyTombstones() {
    String owner = owner();
    ResourceView r = resource(owner, 1);
    HoldInput input = input(r.id(), 9, 10, 1);
    service.hold(owner, input, "deleted-key-001");
    var renamed = service.rename(owner, r.id(), new RenameInput("New name", 1));
    assertEquals(2, renamed.version());
    assertEquals(
        409, assertThrows(Problem.class, () -> service.deleteResource(owner, r.id(), 1)).status());
    service.deleteResource(owner, r.id(), 2);
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class));
    assertEquals(
        410,
        assertThrows(Problem.class, () -> service.hold(owner, input, "deleted-key-001")).status());
  }

  @Test
  void availabilityTracksEffectiveOccupancyAndExportContainsOnlyConfirmed() throws Exception {
    String owner = owner();
    ResourceView r = resource(owner, 2);
    var held = service.hold(owner, input(r.id(), 9, 10, 1), "calendar-key-001").reservation();
    var slots = (List<Slot>) service.availability(owner, r.id(), "2030-01-02", 60, 2).get("slots");
    Slot selected =
        slots.stream().filter(s -> s.startsAt().equals(held.startsAt())).findFirst().orElseThrow();
    assertEquals(1, selected.availableUnits());
    assertFalse(selected.canHold());
    assertFalse(
        Exports.calendar(service.reservations(owner, null, null, null, null), service.now())
            .contains("BEGIN:VEVENT"));
    service.confirm(owner, held.id(), 1);
    assertTrue(
        Exports.calendar(service.reservations(owner, null, null, null, null), service.now())
            .contains("BEGIN:VEVENT"));
  }

  @Test
  void demoIsPrivateAndIdempotentAndSessionsExpireWithTheirData() throws Exception {
    String owner = owner();
    service.demo(owner);
    service.demo(owner);
    assertEquals(2, service.resources(owner).size());
    assertEquals(3, service.reservations(owner, null, null, null, null).size());
    String other = owner();
    assertTrue(service.resources(other).isEmpty());
    clock.value = NOW.plus(Duration.ofDays(1));
    service.demo(owner);
    assertEquals(6, service.reservations(owner, null, null, null, null).size());
    clock.value = NOW.plusMillis(Rules.SESSION_TTL);
    sessions.cleanup();
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM resources", Integer.class));
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class));
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM idempotency", Integer.class));
  }
}
