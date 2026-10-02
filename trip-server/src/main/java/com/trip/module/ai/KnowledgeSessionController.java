package com.trip.module.ai;

import com.trip.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class KnowledgeSessionController {
    private final KnowledgeSessionService service;

    @GetMapping("session/page")
    public R<?> page(@AuthenticationPrincipal Long uid,@RequestParam(defaultValue="1") long current,
                     @RequestParam(defaultValue="20") long size,@RequestParam(required=false) String keyword) {
        return R.ok(service.page(uid,current,size,keyword));
    }
    @PostMapping("session")
    public R<?> create(@AuthenticationPrincipal Long uid,@Valid @RequestBody KnowledgeSessionInput.Create input) {
        return R.ok(service.create(uid,input));
    }
    @GetMapping("session/{id}")
    public R<?> detail(@AuthenticationPrincipal Long uid,@PathVariable long id) { return R.ok(service.detail(uid,id)); }
    @PutMapping("session/{id}")
    public R<?> rename(@AuthenticationPrincipal Long uid,@PathVariable long id,@Valid @RequestBody KnowledgeSessionInput.Rename input) {
        return R.ok(service.rename(uid,id,input));
    }
    @DeleteMapping("session/{id}")
    public R<?> delete(@AuthenticationPrincipal Long uid,@PathVariable long id,@RequestParam long expectedRevision) {
        service.delete(uid,id,expectedRevision);return R.ok();
    }
    @GetMapping("message/page")
    public R<?> messages(@AuthenticationPrincipal Long uid,@RequestParam long sessionId,
                         @RequestParam(defaultValue="20") int size,@RequestParam(required=false) Long beforeId) {
        return R.ok(service.messages(uid,sessionId,size,beforeId));
    }
    @PostMapping("search")
    public R<?> search(@AuthenticationPrincipal Long uid,@Valid @RequestBody KnowledgeSessionInput.Search input) {
        return R.ok(service.search(uid,input));
    }
}
