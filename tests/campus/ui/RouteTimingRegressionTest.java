package campus.ui;
import campus.map.*;
import campus.model.*;
import campus.model.PlannerModel.*;
import campus.service.DayPlanService;
import java.nio.file.*;
import java.time.*;
import java.util.List;
public final class RouteTimingRegressionTest {
 interface Checked{void run()throws Exception;}
 static int passed,failed;
 static void test(String n,Checked c){try{c.run();passed++;System.out.println("PASS "+n);}catch(Throwable e){failed++;System.out.println("FAIL "+n+" "+e);}}
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[]args)throws Exception{
  Path osm=Path.of(args[0]).toAbsolutePath().getParent().getParent().getParent().resolve("tests/oneway.osm");
  CampusMap map=CampusMap.load(osm);RouteService routes=new RouteService(map);
  test("oneway-destination-with-only-incoming-edge",()->ok(routes.route(map.building("w20"),map.building("w21")).reachable(),"valid forward route missing"));
  test("oneway-reverse-remains-unreachable",()->ok(!routes.route(map.building("w21"),map.building("w20")).reachable(),"reverse incorrectly allowed"));
  LocalDate date=LocalDate.of(2026,11,18);
  Event a=new Event("a","a","b",LocalTime.of(9,0),LocalTime.of(10,0,30),Category.CLASS,null,date,"");
  Event b=new Event("b","b","b",LocalTime.of(10,0),LocalTime.of(11,0),Category.CLASS,null,date,"");
  var leg=new DayPlanService.Leg(new Occurrence(a,date,false),new Occurrence(b,date,false),new RouteService.Route(List.of(),0,true,""));
  test("subminute-overlap-keeps-negative-sign",()->ok(leg.gapMinutes()<0,"30 second overlap became zero"));
  Event c=new Event("c","c","b",LocalTime.of(10,0,30),LocalTime.of(11,0),Category.CLASS,null,date,"");
  var exact=new DayPlanService.Leg(new Occurrence(a,date,false),new Occurrence(c,date,false),leg.route());
  test("touching-events-remain-zero-gap",()->ok(exact.gapMinutes()==0,"exact boundary changed"));
  System.out.println("ROUTE TIMING RESULT: "+passed+" passed, "+failed+" failed");System.exit(failed==0?0:1);
 }
}
