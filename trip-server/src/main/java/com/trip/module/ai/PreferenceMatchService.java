package com.trip.module.ai;

import com.trip.common.exception.BizException;
import com.trip.module.user.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PreferenceMatchService {
    private final UserSettingsService users;
    private final MatchService matches;

    public MatchService.Result match(long userId,MatchController.Request request){
        if(request.useSavedPreference()!=null&&!(request.useSavedPreference() instanceof Boolean))throw new BizException(400,"useSavedPreference须为JSON布尔值");
        if(request.intent()==null)throw new BizException(400,"intent不能为空");
        var intent=new LinkedHashMap<>(request.intent());var fields=new ArrayList<String>();
        if(Boolean.TRUE.equals(request.useSavedPreference())){
            var p=users.preference(userId);
            if(!intent.containsKey("days")&&p.preferredDays()!=null){intent.put("days",p.preferredDays());fields.add("days");}
            if(!intent.containsKey("budget")&&!intent.containsKey("budgetMin")&&!intent.containsKey("budgetMax")&&(p.budgetMin().signum()>0||p.budgetMax().signum()>0)){
                intent.put("budgetMin",p.budgetMin());intent.put("budgetMax",p.budgetMax());fields.add("budgetRange");
            }
            boolean preferred=!intent.containsKey("preferenceTags"),avoided=!intent.containsKey("avoid");
            if(preferred&&!p.preferenceTags().isEmpty()){intent.put("preferenceTags",p.preferenceTags());fields.add("preferenceTags");}
            if(avoided&&!p.avoidTags().isEmpty()){intent.put("avoid",p.avoidTags());fields.add("avoid");}
            // Explicit preferences beat conflicting saved exclusions, and vice versa.
            if(preferred&&!avoided&&intent.get("avoid") instanceof List<?> list&&intent.get("preferenceTags") instanceof List<?> tags)
                intent.put("preferenceTags",tags.stream().filter(t->!normalized(list).contains(t)).toList());
            if(avoided&&!preferred&&intent.get("preferenceTags") instanceof List<?> list&&intent.get("avoid") instanceof List<?> tags)
                intent.put("avoid",tags.stream().filter(t->!normalized(list).contains(t)).toList());
        }
        return matches.match(MatchCriteria.from(intent,request.topN()),fields);
    }

    private Set<String> normalized(List<?> tags){
        var result=new HashSet<String>();
        for(Object tag:tags)if(tag instanceof String text)result.add(text.strip());
        return result;
    }
}
