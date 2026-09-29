package com.trip.module.ai;
import com.trip.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/ai/knowledge") @RequiredArgsConstructor
public class KnowledgeController {
    private final KnowledgeService service;
    @PostMapping("search") public R<?> search(@Valid @RequestBody KnowledgeInput.Search input){return R.ok(service.search(input));}
    @GetMapping("documents/{id}") public R<?> document(@PathVariable long id){return R.ok(service.publicDetail(id));}
}
