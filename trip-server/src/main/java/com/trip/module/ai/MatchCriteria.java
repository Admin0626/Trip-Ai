package com.trip.module.ai;

import com.trip.common.exception.BizException;
import java.util.*;
import java.math.BigDecimal;

/** Preserve raw JSON types at this boundary to avoid Jackson scalar coercion. */
public record MatchCriteria(List<String> destinations, Integer days, Integer budget,
                            List<String> tags, List<String> avoid, int topN, List<String> unsupported,
                            BigDecimal budgetMin, BigDecimal budgetMax) {
    public static MatchCriteria from(Map<String, Object> intent, Object topN) {
        if (intent == null) throw new BizException(400, "intent不能为空");
        var destinations = strings(intent.get("destinations"), "destinations", 3, false);
        var tags = strings(intent.get("preferenceTags"), "preferenceTags", 8, true);
        var avoid = strings(intent.get("avoid"), "avoid", 21, true);
        if (tags.stream().anyMatch(avoid::contains)) throw new BizException(400, "偏好与排除标签不能重叠");
        var unsupported = new ArrayList<String>();
        for (String key : intent.keySet()) {
            if (!Set.of("destinations", "days", "budget", "budgetMin", "budgetMax", "preferenceTags", "avoid").contains(key)) {
                Object value = intent.get(key);
                if (value != null && !"".equals(value) && !(value instanceof Collection<?> c && c.isEmpty())) unsupported.add(key);
            }
        }
        Integer budget=integer(intent.get("budget"),"budget",1000000);
        BigDecimal min=amount(intent.get("budgetMin")),max=amount(intent.get("budgetMax"));
        if(budget!=null&&(min!=null||max!=null))throw new BizException(400,"budget与预算区间不能同时设置");
        if(budget!=null)max=BigDecimal.valueOf(budget);
        if(min!=null&&max!=null&&min.compareTo(max)>0)throw new BizException(400,"预算下限不能高于上限");
        return new MatchCriteria(destinations, integer(intent.get("days"), "days", 365),
                budget, tags, avoid, topN == null ? 5 : integer(topN, "topN", 20), List.copyOf(unsupported),min,max);
    }

    private static BigDecimal amount(Object value){
        if(value==null)return null;
        if(!(value instanceof Number))throw new BizException(400,"预算区间须为JSON数字");
        BigDecimal n;
        try{n=new BigDecimal(value.toString());}catch(NumberFormatException e){throw new BizException(400,"预算区间格式错误");}
        if(n.signum()<0||n.compareTo(new BigDecimal("99999999.99"))>0||n.stripTrailingZeros().scale()>2)
            throw new BizException(400,"预算区间须为0—99999999.99，最多两位小数");
        return n.signum()==0?null:n;
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
