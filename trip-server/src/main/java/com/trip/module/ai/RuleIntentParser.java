package com.trip.module.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Deliberately conservative extraction, not a substitute for semantic model parsing. */
@Component
public class RuleIntentParser {
    private static final List<String> TAGS = List.of("自然风光", "历史文化", "美食", "亲子", "摄影", "探险",
            "古城", "慢生活", "海边", "徒步", "温泉", "滑雪", "夜游", "露营", "民俗", "购物");

    public IntentResult parse(String query, List<String> destinationNames) {
        // Reject ambiguous/negated clauses rather than turning exclusions into preferences.
        var positive = new ArrayList<String>();
        var negative = new ArrayList<String>();
        for (String clause : query.split("[，,。；;！!？?\\n]")) {
            if (clause.matches(".*(不|别|避免|排除|除了).*")) negative.add(clause);
            else positive.add(clause);
        }
        String text = String.join("，", positive);
        List<String> destinations = destinationNames.stream().filter(s -> s != null && !s.isBlank())
                .distinct().filter(text::contains).limit(3).toList();
        List<String> tags = TAGS.stream().filter(text::contains).limit(5).toList();
        List<String> avoid = TAGS.stream().filter(t -> negative.stream().anyMatch(s -> s.contains(t))).toList();
        Integer days = number(text, "(?<![\\d.到至~—-])([1-9]\\d{0,2})\\s*天", 365);
        Integer budget = number(text, "预算\\s*([1-9]\\d{0,6})(?![\\d.万千kK到至~—-])(?:\\s*(?:元|左右|以内|上下)|(?=[，,。；;！!？?\\s]|$))", 1000000);
        Integer month = number(text, "(?<![\\d.到至~—-])([1-9]|1[0-2])\\s*月", 12);
        if (range(text, "天")) days = null;
        if (range(text, "月")) month = null;
        if (Pattern.compile("预算\\s*\\d+\\s*元?\\s*[到至~—-]\\s*\\d+").matcher(text).find()) budget = null;
        String companions = unique(text, new String[][]{{"single", "独自", "一个人"}, {"couple", "情侣", "夫妻"},
                {"family", "亲子", "带孩子", "全家"}, {"group", "朋友", "团队"}});
        String pace = unique(text, new String[][]{{"relaxed", "慢生活", "休闲", "轻松"}, {"intense", "紧凑", "特种兵"}});
        if (pace == null && query.matches(".*(不要太赶|不想太赶|不赶时间).*")) pace = "relaxed";
        String level = budget == null ? null : budget < 1000 ? "low" : budget <= 5000 ? "medium" : "high";
        var intent = new IntentResult.Intent(destinations, days, budget, level, tags, companions, pace, month, List.of(), avoid);
        boolean found = !destinations.isEmpty() || days != null || budget != null || !tags.isEmpty()
                || companions != null || pace != null || month != null || !avoid.isEmpty();
        return new IntentResult(intent, found ? 0.3 : 0.1, "RULE_FALLBACK");
    }

    private static Integer number(String text, String regex, int max) {
        var matcher = Pattern.compile(regex).matcher(text);
        Integer result = null;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value > max || (result != null && result != value)) return null;
            result = value;
        }
        return result;
    }

    private static boolean range(String text, String unit) {
        return Pattern.compile("\\d+\\s*" + unit + "?\\s*[到至~—-]\\s*\\d+\\s*" + unit).matcher(text).find();
    }

    private static String unique(String text, String[][] groups) {
        String result = null;
        for (String[] group : groups) {
            for (int i = 1; i < group.length; i++) {
                if (text.contains(group[i])) {
                    if (result != null && !result.equals(group[0])) return null;
                    result = group[0];
                    break;
                }
            }
        }
        return result;
    }
}
