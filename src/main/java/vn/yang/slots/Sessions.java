package vn.yang.slots;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import vn.yang.slots.Contracts.*;
import static vn.yang.slots.Problem.require;

@Service
class Sessions {
  private final JdbcTemplate db; private final Clock clock;
  Sessions(JdbcTemplate db,Clock clock){this.db=db;this.clock=clock;}
  @EventListener(ApplicationReadyEvent.class) public void cleanup(){db.update("DELETE FROM workspaces WHERE expires<=?",clock.millis());}
  Workspace find(String token){
    if(token==null||!token.matches("[a-f0-9]{64}"))return null;
    List<Workspace> found=db.query("SELECT id,csrf,expires FROM workspaces WHERE token_hash=?",(rs,n)->new Workspace(rs.getString(1),rs.getString(2),rs.getLong(3)),Rules.sha(token));
    if(found.isEmpty())return null;
    Workspace w=found.get(0);if(w.expires()<=clock.millis()){cleanup();return null;}return w;
  }
  Session create(){cleanup();String id=UUID.randomUUID().toString(),token=Rules.random(32),csrf=Rules.random(24);long expires=clock.millis()+Rules.SESSION_TTL;db.update("INSERT INTO workspaces(id,token_hash,csrf,expires) VALUES(?,?,?,?)",id,Rules.sha(token),csrf,expires);return new Session(new Workspace(id,csrf,expires),token);}
}
