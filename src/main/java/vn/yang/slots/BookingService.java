package vn.yang.slots;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.yang.slots.Contracts.*;
import static vn.yang.slots.Problem.require;

@Service
@Transactional(readOnly=true)
class BookingService {
  private final JdbcTemplate db; private final Clock clock;
  private final RowMapper<ResourceView> resourceMapper=(rs,n)->new ResourceView(rs.getString("id"),rs.getString("name"),rs.getInt("capacity"),rs.getLong("version"),rs.getLong("created"),rs.getLong("updated"));
  record Stored(String id,String resourceId,String resourceName,String title,int units,long start,long end,String status,Long until,long version,long created){}
  record Replay(String digest,String reservation){}
  BookingService(JdbcTemplate db,Clock clock){this.db=db;this.clock=clock;}
  long now(){return clock.millis();}
  private void readOwner(String owner){require(!db.query("SELECT id FROM workspaces WHERE id=? AND expires>?",(rs,n)->rs.getString(1),owner,now()).isEmpty(),401,"Workspace hết hạn.");}
  private void lockOwner(String owner){require(!db.query("SELECT id FROM workspaces WHERE id=? AND expires>? FOR UPDATE",(rs,n)->rs.getString(1),owner,now()).isEmpty(),401,"Workspace hết hạn.");}
  private int count(String sql,Object...args){Integer result=db.queryForObject(sql,Integer.class,args);return result==null?0:result;}
  List<ResourceView> resources(String owner){readOwner(owner);return db.query("SELECT * FROM resources WHERE owner=? ORDER BY created,id",resourceMapper,owner);}
  ResourceView resource(String owner,String id){readOwner(owner);Rules.id(id);List<ResourceView> rows=db.query("SELECT * FROM resources WHERE owner=? AND id=?",resourceMapper,owner,id);require(!rows.isEmpty(),404,"Không tìm thấy tài nguyên.");return rows.get(0);}
  private ResourceView insertResource(String owner,String name,int capacity,String demoTag){
    require(count("SELECT COUNT(*) FROM resources WHERE owner=?",owner)<10,409,"Mỗi workspace tối đa 10 tài nguyên.");
    String id=UUID.randomUUID().toString();long now=now();db.update("INSERT INTO resources(id,owner,name,capacity,created,updated,demo_tag) VALUES(?,?,?,?,?,?,?)",id,owner,name,capacity,now,now,demoTag);return resource(owner,id);
  }
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public ResourceView createResource(String owner,ResourceInput input){
    require(input!=null,400,"Thiếu thông tin tài nguyên.");String name=Rules.text(input.name(),"Tên tài nguyên",100);
    require(input.capacity()>=1&&input.capacity()<=20,400,"Sức chứa từ 1 đến 20 đơn vị.");lockOwner(owner);return insertResource(owner,name,input.capacity(),null);
  }
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public ResourceView rename(String owner,String id,RenameInput input){
    require(input!=null,400,"Thiếu cấu hình.");String name=Rules.text(input.name(),"Tên tài nguyên",100);Rules.revision(input.version());lockOwner(owner);ResourceView old=resource(owner,id);require(old.version()==input.version(),409,"Tài nguyên đã thay đổi. Hãy tải lại.");
    require(db.update("UPDATE resources SET name=?,version=version+1,updated=? WHERE owner=? AND id=? AND version=?",name,now(),owner,id,input.version())==1,409,"Tài nguyên đã thay đổi.");return resource(owner,id);
  }
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public void deleteResource(String owner,String id,long version){
    Rules.revision(version);lockOwner(owner);ResourceView old=resource(owner,id);require(old.version()==version,409,"Tài nguyên đã thay đổi. Hãy tải lại.");
    require(db.update("DELETE FROM resources WHERE owner=? AND id=? AND version=?",owner,id,version)==1,409,"Tài nguyên đã thay đổi.");
  }
  private Stored stored(String owner,String id){
    readOwner(owner);Rules.id(id);
    List<Stored> found=db.query("SELECT b.*,r.name AS resource_name FROM reservations b JOIN resources r ON r.id=b.resource_id WHERE b.owner=? AND b.id=?",
      (rs,n)->new Stored(rs.getString("id"),rs.getString("resource_id"),rs.getString("resource_name"),rs.getString("title"),rs.getInt("units"),rs.getLong("start_ms"),rs.getLong("end_ms"),rs.getString("status"),(Long)rs.getObject("hold_until"),rs.getLong("version"),rs.getLong("created")),owner,id);
    require(!found.isEmpty(),404,"Không tìm thấy lượt đặt.");return found.get(0);
  }
  private String status(Stored row){return row.status.equals("HELD")&&row.until!=null&&row.until<=now()?"EXPIRED":row.status;}
  private ReservationView view(Stored row){return new ReservationView(row.id,row.resourceId,row.resourceName,row.title,row.units,Rules.instant(row.start),Rules.instant(row.end),status(row),row.until==null?null:Rules.instant(row.until),row.version,row.created);}
  ReservationView reservation(String owner,String id){return view(stored(owner,id));}
  List<ReservationView> reservations(String owner,String resourceId,String day,String status,String query){
    readOwner(owner);if(resourceId!=null&&!resourceId.isBlank())resource(owner,resourceId);
    LocalDate selected=day==null||day.isBlank()?null:Rules.day(day);
    require(status==null||status.isBlank()||Set.of("HELD","CONFIRMED","CANCELLED","EXPIRED").contains(status),400,"Trạng thái không hợp lệ.");
    require(query==null||query.length()<=100,400,"Tìm kiếm tối đa 100 ký tự.");String needle=query==null?"":query.toLowerCase(Locale.ROOT);
    List<String> ids=db.query("SELECT id FROM reservations WHERE owner=? ORDER BY start_ms,id",(rs,n)->rs.getString(1),owner);
    List<ReservationView> result=new ArrayList<>();
    for(String id:ids){Stored row=stored(owner,id);ReservationView value=view(row);if(resourceId!=null&&!resourceId.isBlank()&&!row.resourceId.equals(resourceId))continue;if(selected!=null&&!Instant.ofEpochMilli(row.start).atZone(Rules.ZONE).toLocalDate().equals(selected))continue;if(status!=null&&!status.isBlank()&&!value.status().equals(status))continue;if(!value.title().toLowerCase(Locale.ROOT).contains(needle)&&!value.resourceName().toLowerCase(Locale.ROOT).contains(needle))continue;result.add(value);}
    return result;
  }
  private List<Rules.Span> spans(String owner,String resourceId,long start,long end){
    return db.query("SELECT start_ms,end_ms,units FROM reservations WHERE owner=? AND resource_id=? AND start_ms<? AND end_ms>? AND (status='CONFIRMED' OR (status='HELD' AND hold_until>?))",
      (rs,n)->new Rules.Span(rs.getLong(1),rs.getLong(2),rs.getInt(3)),owner,resourceId,end,start,now());
  }
  Map<String,Object> availability(String owner,String resourceId,String day,int minutes,int units){
    ResourceView resource=resource(owner,resourceId);LocalDate date=Rules.day(day);Rules.dayRange(date,now());
    require(minutes>=15&&minutes<=240&&minutes%15==0,400,"Thời lượng từ 15 đến 240 phút, bước 15 phút.");require(units>=1&&units<=resource.capacity(),400,"Số đơn vị vượt sức chứa.");
    long open=Rules.at(date,8,0),close=Rules.at(date,20,0),now=now();List<Rules.Span> spans=spans(owner,resourceId,open,close);List<Slot> slots=new ArrayList<>();
    for(long start=open;start+minutes*Rules.MINUTE<=close;start+=15*Rules.MINUTE){long end=start+minutes*Rules.MINUTE;int left=resource.capacity()-Rules.peak(spans,start,end);slots.add(new Slot(Rules.instant(start),Rules.instant(end),left,start>=now+Rules.MINUTE&&left>=units));}
    return Map.of("resource",resource,"day",date.toString(),"minutes",minutes,"units",units,"serverTime",now,"slots",slots);
  }
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public Accepted hold(String owner,HoldInput input,String requestKey){
    require(input!=null,400,"Thiếu thông tin giữ chỗ.");String resourceId=Rules.id(input.resourceId()),title=Rules.text(input.title(),"Tên lượt đặt",100),key=Rules.key(requestKey);
    require(input.units()>=1&&input.units()<=20,400,"Số đơn vị từ 1 đến 20.");Rules.Window window=Rules.parseWindow(input.startsAt(),input.endsAt());
    String digest=Rules.sha(resourceId+"\0"+window.start()+"\0"+window.end()+"\0"+input.units()+"\0"+title);lockOwner(owner);
    List<Replay> entries=db.query("SELECT digest,reservation_id FROM idempotency WHERE owner=? AND request_key=?",(rs,n)->new Replay(rs.getString(1),rs.getString(2)),owner,key);
    if(!entries.isEmpty()){Replay old=entries.get(0);require(old.digest.equals(digest),409,"Mã yêu cầu đã dùng với nội dung khác.");require(old.reservation!=null,410,"Lượt đặt của mã yêu cầu này đã bị xóa.");return new Accepted(reservation(owner,old.reservation),true);}
    ResourceView resource=resource(owner,resourceId);Rules.future(window,now());require(input.units()<=resource.capacity(),400,"Số đơn vị vượt sức chứa.");
    require(count("SELECT COUNT(*) FROM reservations WHERE owner=?",owner)<100,409,"Workspace đã đạt 100 lượt đặt.");require(count("SELECT COUNT(*) FROM idempotency WHERE owner=?",owner)<200,409,"Workspace đã đạt 200 mã yêu cầu.");
    int peak=Rules.peak(spans(owner,resourceId,window.start(),window.end()),window.start(),window.end());require(peak+input.units()<=resource.capacity(),409,"Khung giờ không còn đủ chỗ. Hãy tải lại lịch.");
    String id=UUID.randomUUID().toString();long now=now();
    db.update("INSERT INTO reservations(id,owner,resource_id,title,units,start_ms,end_ms,status,hold_until,created,updated) VALUES(?,?,?,?,?,?,?,'HELD',?,?,?)",id,owner,resourceId,title,input.units(),window.start(),window.end(),now+Rules.HOLD_TTL,now,now);
    db.update("INSERT INTO idempotency(owner,request_key,digest,reservation_id) VALUES(?,?,?,?)",owner,key,digest,id);return new Accepted(reservation(owner,id),false);
  }
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public ReservationView confirm(String owner,String id,long version){
    Rules.revision(version);lockOwner(owner);Stored old=stored(owner,id);require(!status(old).equals("EXPIRED"),410,"Giữ chỗ đã hết hạn. Hãy tạo lượt mới.");
    require(old.version==version,409,"Lượt đặt đã thay đổi. Hãy tải lại.");require(old.status.equals("HELD"),409,"Chỉ xác nhận lượt đang giữ chỗ.");
    require(db.update("UPDATE reservations SET status='CONFIRMED',hold_until=NULL,version=version+1,updated=? WHERE owner=? AND id=? AND version=? AND status='HELD' AND hold_until>?",now(),owner,id,version,now())==1,410,"Giữ chỗ đã hết hạn.");
    return reservation(owner,id);
  }
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public ReservationView cancel(String owner,String id,long version){
    Rules.revision(version);lockOwner(owner);Stored old=stored(owner,id);require(!status(old).equals("EXPIRED"),410,"Giữ chỗ đã hết hạn.");require(old.version==version,409,"Lượt đặt đã thay đổi. Hãy tải lại.");require(Set.of("HELD","CONFIRMED").contains(old.status),409,"Lượt đặt đã hủy.");
    require(db.update("UPDATE reservations SET status='CANCELLED',hold_until=NULL,version=version+1,updated=? WHERE owner=? AND id=? AND version=?",now(),owner,id,version)==1,409,"Lượt đặt đã thay đổi.");return reservation(owner,id);
  }
  private ResourceView demoResource(String owner,String tag,String name,int capacity){
    List<ResourceView> existing=db.query("SELECT * FROM resources WHERE owner=? AND demo_tag=?",resourceMapper,owner,tag);return existing.isEmpty()?insertResource(owner,name,capacity,tag):existing.get(0);
  }
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public Map<String,Object> demo(String owner){
    lockOwner(owner);ResourceView room=demoResource(owner,"room-v1","Phòng họp Lotus",1),desks=demoResource(owner,"desks-v1","Bàn làm việc tầng 2",4);LocalDate day=Rules.today(now()).plusDays(1);
    Object[][] rows={{room,"Trao đổi nhóm",9,10,1},{desks,"Nhóm thiết kế",8,10,2},{desks,"Ghi chú API",10,11,1}};
    int i=0;for(Object[] row:rows){ResourceView resource=(ResourceView)row[0];String key="demo-v1-"+resource.id()+"-"+(i++);Accepted accepted=hold(owner,new HoldInput(resource.id(),(String)row[1],Rules.instant(Rules.at(day,(int)row[2],0)),Rules.instant(Rules.at(day,(int)row[3],0)),(int)row[4]),key);if(accepted.reservation().status().equals("HELD"))confirm(owner,accepted.reservation().id(),accepted.reservation().version());}
    return Map.of("resourceId",room.id(),"day",day.toString(),"serverTime",now(),"fictional",true);
  }
}
