package com.trip.module.ai;

import com.trip.module.plan.dto.PlanSaveDTO;
import com.trip.module.plan.dto.PlanItemDTO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import java.util.*;

@Component
@RequiredArgsConstructor
public class PlannerOutputValidator {
    private final ObjectMapper json;
    public record Draft(String title, List<PlanSaveDTO.DayDTO> dayList,String answer) {}
    public Draft validate(String content, int days) {
        try {
            String clean = content.strip();
            if (clean.startsWith("```json\n") && clean.endsWith("```")) clean = clean.substring(8, clean.length()-3).strip();
            var root = json.readTree(clean);
            String title = text(root, "title", 200, true);
            var dayNodes = root.path("dayList");
            if (!root.isObject() || !dayNodes.isArray() || dayNodes.size() != days) throw invalid();
            var result = new ArrayList<PlanSaveDTO.DayDTO>();
            for (JsonNode node : dayNodes) {
                if (!node.isObject()) throw invalid();
                var day = new PlanSaveDTO.DayDTO();
                day.setTitle(text(node, "title", 100, true)); day.setSummary(text(node, "summary", 255, false));
                day.setDayIndex(result.size()+1);
                var items = node.path("items");
                if (!items.isArray() || items.isEmpty() || items.size() > 8) throw invalid();
                var list = new ArrayList<PlanItemDTO>();
                for (JsonNode value : items) {
                    if (!value.isObject()) throw invalid();
                    var item = new PlanItemDTO();
                    item.setTitle(text(value, "title", 100, true));
                    item.setTimePoint(text(value, "timePoint", 10, false));
                    item.setActivity(text(value, "activity", 255, false));
                    item.setTransport(text(value, "transport", 50, false));
                    item.setHotel(text(value, "hotel", 100, false)); item.setMeal(text(value, "meal", 100, false));
                    item.setTips(text(value, "tips", 255, false));
                    var cost = value.path("cost");
                    if (!cost.isMissingNode() && !cost.isNull()) {
                        if (!cost.isNumber() || cost.decimalValue().signum() < 0 || cost.decimalValue().compareTo(new java.math.BigDecimal("1000000")) > 0 || cost.decimalValue().scale() > 2) throw invalid();
                        item.setCost(cost.decimalValue());
                    }
                    // Never trust model-supplied IDs or ownership fields.
                    item.setAttractionId(0L); item.setSortNo(list.size()+1); list.add(item);
                }
                day.setItems(list); result.add(day);
            }
            return new Draft(title, result,text(root,"answer",4000,false));
        } catch (Exception e) { throw invalid(); }
    }
    private String text(JsonNode object, String name, int max, boolean required) {
        JsonNode value = object.path(name);
        if (!required && (value.isMissingNode() || value.isNull())) return null;
        if (!value.isString() || value.asString().length() > max || (required && value.asString().isBlank())) throw invalid();
        return value.asString();
    }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("INVALID_ITINERARY_JSON"); }
}
