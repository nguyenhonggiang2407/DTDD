package vn.yang.slots;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import vn.yang.slots.Contracts.*;

@RestController
class ApiController {
  private final BookingService service;
  private final JdbcTemplate db;

  ApiController(BookingService service, JdbcTemplate db) {
    this.service = service;
    this.db = db;
  }

  private Workspace workspace(HttpServletRequest req) {
    return (Workspace) req.getAttribute(RequestGuard.WORKSPACE);
  }

  private String owner(HttpServletRequest req) {
    return workspace(req).id();
  }

  @GetMapping("/health")
  Map<String, Object> health() {
    db.queryForObject("SELECT 1", Integer.class);
    return Map.of("status", "ok");
  }

  @GetMapping({"/", "/assets/app.js", "/assets/styles.css", "/assets/favicon.svg"})
  ResponseEntity<byte[]> page(HttpServletRequest req) throws java.io.IOException {
    Map<String, String> names =
        Map.of(
            "/",
            "index.html",
            "/assets/app.js",
            "app.js",
            "/assets/styles.css",
            "styles.css",
            "/assets/favicon.svg",
            "favicon.svg");
    String name = names.get(req.getRequestURI()),
        mime =
            name.endsWith(".html")
                ? "text/html"
                : name.endsWith(".css")
                    ? "text/css"
                    : name.endsWith(".js") ? "text/javascript" : "image/svg+xml";
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(mime + ";charset=UTF-8"))
        .body(new ClassPathResource("public/" + name).getContentAsByteArray());
  }

  @GetMapping("/api/workspace")
  Map<String, Object> bootstrap(HttpServletRequest req) {
    Workspace w = workspace(req);
    long now = service.now();
    return Map.of(
        "csrf",
        w.csrf(),
        "expires",
        w.expires(),
        "serverTime",
        now,
        "today",
        Rules.today(now).toString(),
        "maxDay",
        Rules.today(now).plusDays(90).toString(),
        "zone",
        "Asia/Ho_Chi_Minh",
        "holdSeconds",
        300,
        "limits",
        Map.of("resources", 10, "reservations", 100, "requestKeys", 200));
  }

  @GetMapping("/api/resources")
  Map<String, Object> resources(HttpServletRequest req) {
    return Map.of("resources", service.resources(owner(req)), "serverTime", service.now());
  }

  @PostMapping("/api/resources")
  ResponseEntity<?> create(HttpServletRequest req, @Valid @RequestBody ResourceInput body) {
    return ResponseEntity.status(201)
        .body(Map.of("resource", service.createResource(owner(req), body)));
  }

  @PatchMapping("/api/resources/{id}")
  Map<String, Object> rename(
      HttpServletRequest req, @PathVariable String id, @Valid @RequestBody RenameInput body) {
    return Map.of("resource", service.rename(owner(req), id, body));
  }

  @DeleteMapping("/api/resources/{id}")
  ResponseEntity<Void> delete(
      HttpServletRequest req, @PathVariable String id, @Valid @RequestBody Revision body) {
    service.deleteResource(owner(req), id, body.version());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/availability")
  Map<String, Object> availability(
      HttpServletRequest req,
      @RequestParam String resourceId,
      @RequestParam String day,
      @RequestParam(defaultValue = "60") int minutes,
      @RequestParam(defaultValue = "1") int units) {
    return service.availability(owner(req), resourceId, day, minutes, units);
  }

  @GetMapping("/api/reservations")
  Map<String, Object> list(
      HttpServletRequest req,
      @RequestParam(required = false) String resourceId,
      @RequestParam(required = false) String day,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String q) {
    return Map.of(
        "reservations",
        service.reservations(owner(req), resourceId, day, status, q),
        "serverTime",
        service.now());
  }

  @GetMapping("/api/reservations/{id}")
  Map<String, Object> detail(HttpServletRequest req, @PathVariable String id) {
    return Map.of("reservation", service.reservation(owner(req), id), "serverTime", service.now());
  }

  @PostMapping("/api/holds")
  ResponseEntity<?> hold(
      HttpServletRequest req,
      @Valid @RequestBody HoldInput body,
      @RequestHeader("Idempotency-Key") String key) {
    Accepted result = service.hold(owner(req), body, key);
    return ResponseEntity.status(result.replayed() ? 200 : 201).body(result);
  }

  @PostMapping("/api/reservations/{id}/confirm")
  Map<String, Object> confirm(
      HttpServletRequest req, @PathVariable String id, @Valid @RequestBody Revision body) {
    return Map.of(
        "reservation",
        service.confirm(owner(req), id, body.version()),
        "serverTime",
        service.now());
  }

  @PostMapping("/api/reservations/{id}/cancel")
  Map<String, Object> cancel(
      HttpServletRequest req, @PathVariable String id, @Valid @RequestBody Revision body) {
    return Map.of(
        "reservation", service.cancel(owner(req), id, body.version()), "serverTime", service.now());
  }

  @PostMapping("/api/demo")
  Map<String, Object> demo(HttpServletRequest req) {
    return service.demo(owner(req));
  }

  @GetMapping("/api/export.csv")
  ResponseEntity<byte[]> csv(
      HttpServletRequest req,
      @RequestParam(required = false) String resourceId,
      @RequestParam(required = false) String day,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String q) {
    return download(
        Exports.csv(service.reservations(owner(req), resourceId, day, status, q)),
        "text/csv",
        "yangslots.csv");
  }

  @GetMapping("/api/calendar.ics")
  ResponseEntity<byte[]> calendar(
      HttpServletRequest req,
      @RequestParam(required = false) String resourceId,
      @RequestParam(required = false) String day) {
    return download(
        Exports.calendar(
            service.reservations(owner(req), resourceId, day, "CONFIRMED", null), service.now()),
        "text/calendar",
        "yangslots.ics");
  }

  private ResponseEntity<byte[]> download(String content, String type, String file) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(type + ";charset=UTF-8"))
        .header("Content-Disposition", "attachment; filename=\"" + file + "\"")
        .body(content.getBytes(StandardCharsets.UTF_8));
  }
}
