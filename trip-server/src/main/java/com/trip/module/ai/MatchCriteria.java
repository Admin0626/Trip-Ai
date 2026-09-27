package com.trip.module.ai;

import com.trip.common.exception.BizException;
import java.util.*;

/** Preserve raw JSON types at this boundary to avoid Jackson scalar coercion. */
public record MatchCriteria(List<String> destinations, Integer days, Integer budget,
                            List<String> tags, List<String> avoid, int topN, List<String> unsupported) {
    public static MatchCriteria from(Map<String, Object> intent, Object topN) {
        if (intent == null) throw new BizException(400, "intent不能为空");
        var destinations = strings(intent.get("destinations"), "destinations", 3, false);
        var tags = strings(intent.get("preferenceTags"), "preferenceTags", 5, true);
        var avoid = strings(intent.get("avoid"), "avoid", 16, true);
        if (tags.stream().anyMatch(avoid::contains)) throw new BizException(400, "偏好与排除标签不能重叠");
        var unsupported = new ArrayList<String>();
        for (String key : intent.keySet()) {
            if (!Set.of("destinations", "days", "budget", "preferenceTags", "avoid").contains(key)) {
                Object value = intent.get(key);
                if (value != null && !"".equals(value) && !(value instanceof Collection<?> c && c.isEmpty())) unsupported.add(key);
            }
        }
        return new MatchCriteria(destinations, integer(intent.get("days"), "days", 365),
                integer(intent.get("budget"), "budget", 1000000), tags, avoid,
                topN == null ? 5 : integer(topN, "topN", 20), List.copyOf(unsupported));
    }

    private static Integer integer(Object value, String field, int max) {
        if (value == null) return null;
        if (!(value instanceof Integer || value instanceof Long) || ((Number)value).longValue() < 1
                || ((Number)value).longValue() > max) throw new BizException(400, field + "须为1—" + max + "的整数");
        return ((Number)value).intValue();
    }

    private static List<String> strings(Object value, String field, int max, boolean tags) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list) || list.size() > max) throw new BizException(400, field + "须为最多" + max + "项的数组");
        var result = new LinkedHashSet<String>();
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank() || text.length() > 100) throw new BizException(400, field + "包含非法文本");
            String trimmed = text.strip();
            if (tags && !RuleIntentParser.TAGS.contains(trimmed)) throw new BizException(400, field + "包含不支持的标签");
            result.add(trimmed);
        }
        return List.copyOf(result);
    }
}
