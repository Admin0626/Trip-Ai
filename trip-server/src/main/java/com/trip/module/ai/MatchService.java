package com.trip.module.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MatchService {
    private final JdbcTemplate jdbc;
    public record Item(long routeId, String title, String coverImg, String destinationName, int days,
                       BigDecimal price, List<String> tags, double recallScore, Integer llmScore,
                       String reason, List<String> highlightMatch) {}
    public record Result(long totalCandidates, Map<String, Long> recallDetail, long costMs, String source,
                         List<String> unsupportedCriteria, List<Item> list,EffectiveCriteria effectiveCriteria,List<String> savedPreferenceFields) {}
    public record EffectiveCriteria(List<String> destinations,Integer days,BigDecimal budgetMin,BigDecimal budgetMax,List<String> preferenceTags,List<String> avoid) {}

    @Transactional(readOnly = true)
    public Result match(MatchCriteria c,List<String> savedFields) {
        long start = System.nanoTime();
        StringBuilder where = new StringBuilder(" FROM route r JOIN destination d ON d.id=r.destination_id WHERE r.status=1 AND r.deleted=0 AND d.status=1 AND d.deleted=0");
        var args = new ArrayList<Object>();
        if (!c.destinations().isEmpty()) {
            where.append(" AND (");
            var groups = new ArrayList<String>();
            for (String name : c.destinations()) {
                groups.add("(d.name=? OR d.province=? OR d.city=?)");
                args.addAll(List.of(name, name, name));
            }
            where.append(String.join(" OR ", groups)).append(")");
        }
        if (c.days() != null) { where.append(" AND r.days=?"); args.add(c.days()); }
        if (c.budgetMin() != null) { where.append(" AND r.price>=?"); args.add(c.budgetMin()); }
        if (c.budgetMax() != null) { where.append(" AND r.price<=?"); args.add(c.budgetMax()); }
        for (String tag : c.avoid()) { where.append(" AND FIND_IN_SET(?,r.tags)=0"); args.add(tag); }
        long total = Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray()));
        var selectArgs = new ArrayList<Object>();
        var scores = new ArrayList<String>();
        for (String tag : c.tags()) { scores.add("IF(FIND_IN_SET(?,r.tags)>0,1,0)"); selectArgs.add(tag); }
        String score = scores.isEmpty() ? "0" : String.join("+", scores);
        selectArgs.addAll(args); selectArgs.add(c.topN());
        List<Item> items = jdbc.query("SELECT r.id,r.title,r.cover_img,r.days,r.price,r.tags,d.name,(" + score
                        + ") AS matched_tags" + where + " ORDER BY matched_tags DESC,r.avg_score DESC,r.id ASC LIMIT ?", (rs, row) -> {
                    List<String> tags = Arrays.stream(rs.getString("tags").split(",")).filter(s -> !s.isBlank()).toList();
                    var highlights = new ArrayList<String>();
                    if (!c.destinations().isEmpty()) highlights.add("目的地符合");
                    if (c.days() != null) highlights.add("天数一致");
                    if (c.budgetMin() != null||c.budgetMax()!=null) highlights.add("人均参考价在预算内");
                    c.tags().stream().filter(tags::contains).forEach(highlights::add);
                    String reason = highlights.isEmpty() ? "在架路线，按用户评分排序" : String.join("、", highlights);
                    return new Item(rs.getLong("id"), rs.getString("title"), rs.getString("cover_img"), rs.getString("name"),
                            rs.getInt("days"), rs.getBigDecimal("price"), tags,
                            c.tags().isEmpty() ? 0 : rs.getInt("matched_tags") * 1.0 / c.tags().size(), null, reason, highlights);
                }, selectArgs.toArray());
        return new Result(total, Map.of("content", total, "cf", 0L, "hot", 0L, "behavior", 0L),
                (System.nanoTime() - start) / 1000000, "RULE_BASED", c.unsupported(), items,
                new EffectiveCriteria(c.destinations(),c.days(),c.budgetMin(),c.budgetMax(),c.tags(),c.avoid()),List.copyOf(savedFields));
    }
}
