package vn.yang.slots;
public final class Contracts {
  public record ResourceInput(String name,int capacity){}
  public record RenameInput(String name,long version){}
  public record HoldInput(String resourceId,String title,String startsAt,String endsAt,int units){}
  public record Revision(long version){}
  public record ResourceView(String id,String name,int capacity,long version,long created,long updated){}
  public record ReservationView(String id,String resourceId,String resourceName,String title,int units,String startsAt,String endsAt,String status,String expiresAt,long version,long created){}
  public record Slot(String startsAt,String endsAt,int availableUnits,boolean canHold){}
  public record Accepted(ReservationView reservation,boolean replayed){}
  public record Workspace(String id,String csrf,long expires){}
  public record Session(Workspace workspace,String cookie){}
  private Contracts(){}
}
