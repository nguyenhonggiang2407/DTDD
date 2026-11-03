package vn.yang.slots;

import jakarta.validation.constraints.*;

public final class Contracts {
  public record ResourceInput(
      @NotBlank @Size(max = 100) String name, @Min(1) @Max(20) int capacity) {}

  public record RenameInput(@NotBlank @Size(max = 100) String name, @Min(1) long version) {}

  public record HoldInput(
      @NotBlank @Size(max = 36) String resourceId,
      @NotBlank @Size(max = 100) String title,
      @NotBlank @Size(max = 60) String startsAt,
      @NotBlank @Size(max = 60) String endsAt,
      @Min(1) @Max(20) int units) {}

  public record Revision(@Min(1) long version) {}

  public record ResourceView(
      String id, String name, int capacity, long version, long created, long updated) {}

  public record ReservationView(
      String id,
      String resourceId,
      String resourceName,
      String title,
      int units,
      String startsAt,
      String endsAt,
      String status,
      String expiresAt,
      long version,
      long created) {}

  public record Slot(String startsAt, String endsAt, int availableUnits, boolean canHold) {}

  public record Accepted(ReservationView reservation, boolean replayed) {}

  public record Workspace(String id, String csrf, long expires) {}

  public record Session(Workspace workspace, String cookie) {}

  private Contracts() {}
}
