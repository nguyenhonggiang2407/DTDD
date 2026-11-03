package vn.yang.slots;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import vn.yang.slots.Contracts.*;

class PersistenceTest {
  @TempDir Path directory;

  ConfigurableApplicationContext start() {
    return new SpringApplicationBuilder(
            YangSlotsApplication.class, ReservationsTest.TimeConfig.class)
        .web(WebApplicationType.NONE)
        .run(
            "--spring.datasource.url=jdbc:h2:file:"
                + directory.resolve("persist")
                + ";LOCK_TIMEOUT=5000;DATABASE_TO_UPPER=FALSE",
            "--spring.main.banner-mode=off",
            "--logging.level.root=ERROR");
  }

  @Test
  void fileDatabaseRetainsSessionResourceAndRequestIdentityAcrossRestart() {
    String cookie, owner, id, resourceId;
    HoldInput input;
    try (var context = start()) {
      Sessions sessions = context.getBean(Sessions.class);
      BookingService service = context.getBean(BookingService.class);
      Session session = sessions.create();
      cookie = session.cookie();
      owner = session.workspace().id();
      ResourceView resource = service.createResource(owner, new ResourceInput("Persistent", 1));
      resourceId = resource.id();
      input =
          new HoldInput(
              resource.id(), "Across restart", "2030-01-02T02:00:00Z", "2030-01-02T03:00:00Z", 1);
      id = service.hold(owner, input, "restart-request-001").reservation().id();
    }
    try (var context = start()) {
      Sessions sessions = context.getBean(Sessions.class);
      BookingService service = context.getBean(BookingService.class);
      assertEquals(owner, sessions.find(cookie).id());
      assertEquals(resourceId, service.resources(owner).get(0).id());
      Accepted replay = service.hold(owner, input, "restart-request-001");
      assertTrue(replay.replayed());
      assertEquals(id, replay.reservation().id());
      assertEquals("HELD", replay.reservation().status());
      assertEquals(1, service.reservations(owner, null, null, null, null).size());
    }
  }
}
