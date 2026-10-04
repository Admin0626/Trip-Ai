package com.trip.module.analysis;

import com.trip.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class AnalysisService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String CATALOG = " FROM route r JOIN destination d ON d.id=r.destination_id WHERE r.deleted=0 AND d.deleted=0";
    private static final String BOOKINGS = " FROM route_booking b JOIN route r ON r.id=b.route_id JOIN destination d ON d.id=r.destination_id WHERE r.deleted=0 AND d.deleted=0 AND b.create_time>=? AND b.create_time<?";

    @Autowired public AnalysisService(JdbcTemplate jdbc) { this(jdbc, Clock.system(ZONE)); }
    AnalysisService(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }
    public record Window(int days, LocalDate startDate, LocalDate endDate, LocalDateTime start, LocalDateTime until) {}
    public record Destination(long id, String name) {}
    public record Day(String date, long bookings, long cancelled, long newUsers, long aiCalls) {}
    public record Heat(long id, String name, long bookings) {}
    public record Point(long id, String title, long destinationId, String destination, BigDecimal price, int days, int status, long bookings) {}
    public record Summary(long users, long newUsers, long routes, long bookings, long cancelled, long aiCalls, long aiSucceeded, Double aiAverageMs) {}
    public record Status(int status, String label, long count) {}
    public record Dashboard(String generatedAt, String timezone, int days, String startDate, String endDate,
                            Long destinationId, Summary summary, List<Day> trend, List<Heat> destinationHeat,
                            List<Point> routeScatter, long matchingRoutes, int scatterLimit,
                            List<Status> bookingStatuses, List<Status> aiStatuses,
                            List<Destination> destinations, long destinationTotal, int destinationLimit) {}

    static Window window(int days, Clock clock) {
        if (!Set.of(7,30,90).contains(days)) throw new BizException(400,"统计天数只能为7、30或90");
        LocalDate today=LocalDate.now(clock.withZone(ZONE));
        LocalDate start=today.minusDays(days-1);
        return new Window(days,start,today,start.atStartOfDay(),today.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Dashboard dashboard(int days, Long destinationId) {
        Window w=window(days,clock);
        if (destinationId!=null && (destinationId<1 || destinationId>9007199254740991L)) throw new BizException(400,"目的地ID须为有效正整数");
        if (destinationId!=null && count("SELECT COUNT(*) FROM destination WHERE id=? AND deleted=0",destinationId)==0) throw new BizException(404,"目的地不存在或已删除");
        String filter=destinationId==null?"":" AND d.id=?";
        Object[] windowArgs=destinationId==null?new Object[]{w.start(),w.until()}:new Object[]{w.start(),w.until(),destinationId};
        Object[] catalogArgs=destinationId==null?new Object[]{}:new Object[]{destinationId};
        long routes=count("SELECT COUNT(*)"+CATALOG+filter,catalogArgs);
        Map<Integer,Long> statuses=new HashMap<>();
        jdbc.query("SELECT b.status,COUNT(*) AS n"+BOOKINGS+filter+" GROUP BY b.status",rs->{statuses.put(rs.getInt(1),rs.getLong(2));},windowArgs);
        var labels=List.of("待确认","已确认","已取消","已完成");
        List<Status> bookingStatuses=new ArrayList<>();
        for(int i=0;i<4;i++) bookingStatuses.add(new Status(i,labels.get(i),statuses.getOrDefault(i,0L)));
        long bookings=bookingStatuses.stream().mapToLong(Status::count).sum(),cancelled=statuses.getOrDefault(2,0L);
        var ai=jdbc.queryForMap("SELECT COUNT(*) AS n,COALESCE(SUM(success=1),0) AS succeeded,AVG(cost_ms) AS averageMs FROM llm_call_log WHERE create_time>=? AND create_time<?",w.start(),w.until());
        long aiCalls=number(ai.get("n")),aiSucceeded=number(ai.get("succeeded"));
        var summary=new Summary(count("SELECT COUNT(*) FROM sys_user WHERE deleted=0"),count("SELECT COUNT(*) FROM sys_user WHERE deleted=0 AND create_time>=? AND create_time<?",w.start(),w.until()),routes,bookings,cancelled,aiCalls,aiSucceeded,ai.get("averageMs")==null?null:((Number)ai.get("averageMs")).doubleValue());
        Map<String,long[]> daily=new LinkedHashMap<>();
        for(LocalDate day=w.startDate();!day.isAfter(w.endDate());day=day.plusDays(1)) daily.put(day.toString(),new long[4]);
        jdbc.query("SELECT DATE(b.create_time),COUNT(*),COALESCE(SUM(b.status=2),0)"+BOOKINGS+filter+" GROUP BY DATE(b.create_time)",rs->{var v=daily.get(rs.getString(1));if(v!=null){v[0]=rs.getLong(2);v[1]=rs.getLong(3);}},windowArgs);
        jdbc.query("SELECT DATE(create_time),COUNT(*) FROM sys_user WHERE deleted=0 AND create_time>=? AND create_time<? GROUP BY DATE(create_time)",rs->{var v=daily.get(rs.getString(1));if(v!=null)v[2]=rs.getLong(2);},w.start(),w.until());
        jdbc.query("SELECT DATE(create_time),COUNT(*) FROM llm_call_log WHERE create_time>=? AND create_time<? GROUP BY DATE(create_time)",rs->{var v=daily.get(rs.getString(1));if(v!=null)v[3]=rs.getLong(2);},w.start(),w.until());
        var trend=daily.entrySet().stream().map(e->new Day(e.getKey(),e.getValue()[0],e.getValue()[1],e.getValue()[2],e.getValue()[3])).toList();
        var heat=jdbc.query("SELECT d.id,d.name,COUNT(*) AS n"+BOOKINGS+filter+" GROUP BY d.id,d.name ORDER BY n DESC,d.id ASC LIMIT 10",(rs,n)->new Heat(rs.getLong(1),rs.getString(2),rs.getLong(3)),windowArgs);
        // Aggregate bookings before joining: no Cartesian inflation or reliance on cached counters.
        var pointArgs=new ArrayList<Object>(List.of(w.start(),w.until()));
        if(destinationId!=null)pointArgs.add(destinationId);
        var points=jdbc.query("SELECT r.id,r.title,d.id,d.name,r.price,r.days,r.status,COALESCE(a.n,0) AS bookings FROM route r JOIN destination d ON d.id=r.destination_id LEFT JOIN (SELECT route_id,COUNT(*) AS n FROM route_booking WHERE create_time>=? AND create_time<? GROUP BY route_id) a ON a.route_id=r.id WHERE r.deleted=0 AND d.deleted=0"+filter+" ORDER BY bookings DESC,r.id ASC LIMIT 100",(rs,n)->new Point(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getString(4),rs.getBigDecimal(5),rs.getInt(6),rs.getInt(7),rs.getLong(8)),pointArgs.toArray());
        var destinations=jdbc.query("SELECT id,name FROM destination WHERE deleted=0 ORDER BY name,id LIMIT 1000",(rs,n)->new Destination(rs.getLong(1),rs.getString(2)));
        return new Dashboard(ZonedDateTime.now(clock.withZone(ZONE)).toOffsetDateTime().toString(),ZONE.getId(),days,w.startDate().toString(),w.endDate().toString(),destinationId,summary,trend,heat,points,routes,100,bookingStatuses,List.of(new Status(1,"成功",aiSucceeded),new Status(0,"失败",aiCalls-aiSucceeded)),destinations,count("SELECT COUNT(*) FROM destination WHERE deleted=0"),1000);
    }
    private static long number(Object value) { return ((Number)value).longValue(); }
    private long count(String sql,Object... args) { return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args)); }
}
