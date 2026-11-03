package vn.yang.slots;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.yang.slots.Contracts.ReservationView;

class RulesTest {
  static final long NOW=Instant.parse("2030-01-01T00:00:00Z").toEpochMilli();
  @Test void capacityUsesPeakAndMergesAdjacentBoundaries(){
    var spans=List.of(new Rules.Span(0,10,1),new Rules.Span(10,20,1));
    assertEquals(1,Rules.peak(spans,0,20));assertEquals(0,Rules.peak(spans,20,30));
    assertEquals(2,Rules.peak(List.of(new Rules.Span(0,10,1),new Rules.Span(5,15,1)),0,15));
  }
  @Test void capacityClipsIntervalsToTheRequestedWindow(){
    assertEquals(2,Rules.peak(List.of(new Rules.Span(0,100,2),new Rules.Span(20,30,4)),40,60));
    assertEquals(6,Rules.peak(List.of(new Rules.Span(0,100,2),new Rules.Span(20,30,4)),25,50));
    assertEquals(0,Rules.peak(List.of(new Rules.Span(30,30,10)),20,40));
  }
  @Test void datesRequireExplicitOffsetsAndQuarterHours(){
    var valid=Rules.window("2030-01-02T09:00:00+07:00","2030-01-02T10:00:00+07:00",NOW);
    assertEquals(Instant.parse("2030-01-02T02:00:00Z").toEpochMilli(),valid.start());
    for(String value:List.of("2030-01-02T09:00","2030-01-02T09:01:00+07:00","2030-01-02T09:00:01+07:00","2030-02-30T09:00:00+07:00"))assertThrows(Problem.class,()->Rules.window(value,"2030-01-02T10:00:00+07:00",NOW));
  }
  @Test void businessHoursDurationAndFutureHorizonAreEnforced(){
    for(String[] pair:List.of(
      new String[]{"2030-01-02T07:45:00+07:00","2030-01-02T08:30:00+07:00"},
      new String[]{"2030-01-02T19:00:00+07:00","2030-01-02T20:15:00+07:00"},
      new String[]{"2030-01-02T09:00:00+07:00","2030-01-02T13:15:00+07:00"},
      new String[]{"2030-01-02T09:00:00+07:00","2030-01-02T09:00:00+07:00"},
      new String[]{"2029-12-31T09:00:00+07:00","2029-12-31T10:00:00+07:00"},
      new String[]{"2030-05-02T09:00:00+07:00","2030-05-02T10:00:00+07:00"}))assertThrows(Problem.class,()->Rules.window(pair[0],pair[1],NOW));
  }
  @Test void exactMinuteLeadAndNinetyDayBoundaryAreAccepted(){
    long now=Rules.at(LocalDate.of(2030,1,1),8,59);
    assertDoesNotThrow(()->Rules.window("2030-01-01T09:00:00+07:00","2030-01-01T09:15:00+07:00",now));
    assertThrows(Problem.class,()->Rules.window("2030-01-01T09:00:00+07:00","2030-01-01T09:15:00+07:00",now+1));
    assertDoesNotThrow(()->Rules.dayRange(Rules.today(NOW).plusDays(90),NOW));
    assertThrows(Problem.class,()->Rules.dayRange(Rules.today(NOW).plusDays(91),NOW));
  }
  @Test void textAndIdentifiersAreNormalizedAndBounded(){
    assertEquals("Hà Nội",Rules.text(" Ha\u0300 Nội ","name",100));
    for(String value:List.of("","x\nb","x".repeat(101)))assertThrows(Problem.class,()->Rules.text(value,"name",100));
    assertThrows(Problem.class,()->Rules.key("too.short"));assertThrows(Problem.class,()->Rules.id("1 OR 1=1"));
    assertThrows(Problem.class,()->Rules.day("+99999999-01-01"));assertThrows(Problem.class,()->Rules.day("2030-02-30"));
  }
  @Test void csvGuardsFormulasAndQuotesEveryCell(){
    var row=record("=1+1","Phòng \"A\"", "CONFIRMED");
    String csv=Exports.csv(List.of(row));assertEquals('\ufeff',csv.charAt(0));assertTrue(csv.contains("\"'=1+1\""));assertTrue(csv.contains("\"Phòng \"\"A\"\"\""));assertTrue(csv.endsWith("\r\n"));
  }
  @Test void calendarUsesUtcStableUidAndOnlyConfirmedBookings(){
    var confirmed=record("Kế hoạch, nhóm A; "+"học ".repeat(15),"Phòng Lotus","CONFIRMED");
    String calendar=Exports.calendar(List.of(confirmed,record("Not final","Room","HELD")),NOW);
    assertEquals(1,calendar.split("BEGIN:VEVENT",-1).length-1);assertTrue(calendar.contains("DTSTART:20300102T020000Z\r\n"));assertTrue(calendar.contains("UID:"+confirmed.id()+"@yangslots"));
    String unfolded=calendar.replace("\r\n ","");assertTrue(unfolded.contains("SUMMARY:Kế hoạch\\, nhóm A\\;"));
    for(String line:calendar.split("\r\n"))assertTrue(line.getBytes(StandardCharsets.UTF_8).length<=75,line);
    assertFalse(calendar.contains("\ufffd"));
  }
  @Test void foldDoesNotSplitMultibyteCharacters(){
    String original="SUMMARY:"+"Nguyễn 😀, ".repeat(20),folded=Exports.fold(original);
    assertEquals(original+"\r\n",folded.replace("\r\n ",""));
    for(String line:folded.split("\r\n"))assertTrue(line.getBytes(StandardCharsets.UTF_8).length<=75);
  }
  static ReservationView record(String title,String resource,String status){
    return new ReservationView("00000000-0000-0000-0000-000000000001","00000000-0000-0000-0000-000000000002",resource,title,1,"2030-01-02T02:00:00Z","2030-01-02T03:00:00Z",status,null,2,NOW);
  }
}
