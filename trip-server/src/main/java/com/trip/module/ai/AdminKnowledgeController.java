package com.trip.module.ai;
import com.trip.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/admin/ai/knowledge") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminKnowledgeController {
    private final KnowledgeService service;
    private final KnowledgeMaintenanceService maintenance;
    @GetMapping("page") public R<?> page(@RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="20") long size,@RequestParam(required=false) String keyword,@RequestParam(required=false) Integer status,@RequestParam(required=false) String indexState,@RequestParam(required=false) String sourceState){return R.ok(service.page(current,size,keyword,status,indexState,sourceState));}
    @GetMapping("health") public R<?> health(){return R.ok(service.health());}
    @GetMapping("sources") public R<?> sources(@RequestParam String docType,@RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="20") long size,@RequestParam(required=false) String keyword,@RequestParam(required=false) Long sourceId){return R.ok(service.sources(docType,current,size,keyword,sourceId));}
    @PostMapping("repair") public R<?> repair(@Valid @RequestBody KnowledgeInput.BatchRepair input){return R.ok(maintenance.repair(input));}
    @GetMapping("{id}") public R<?> detail(@PathVariable long id){return R.ok(service.detail(id));}
    @PostMapping public R<?> create(@Valid @RequestBody KnowledgeInput.Save input){return R.ok(service.save(null,input));}
    @PutMapping("{id}") public R<?> update(@PathVariable long id,@Valid @RequestBody KnowledgeInput.Save input){return R.ok(service.save(id,input));}
    @PutMapping("{id}/status") public R<?> state(@PathVariable long id,@Valid @RequestBody KnowledgeInput.State input){return R.ok(service.state(id,input));}
    @PutMapping("{id}/source") public R<?> source(@PathVariable long id,@Valid @RequestBody KnowledgeInput.Source input){return R.ok(service.changeSource(id,input));}
    @DeleteMapping("{id}") public R<?> delete(@PathVariable long id,@RequestParam long expectedRevision){service.delete(id,expectedRevision);return R.ok();}
    @PostMapping("rebuild") public R<?> rebuild(@Valid @RequestBody KnowledgeInput.Rebuild input){return R.ok(service.rebuild(input.id(),input.expectedRevision()));}
    @PostMapping("upload") public R<?> upload(@RequestParam String title,@RequestParam(defaultValue="GUIDE") String docType,@RequestParam(required=false) Long sourceId,@RequestParam(defaultValue="0") int status,@RequestPart MultipartFile file){return R.ok(service.upload(title,docType,sourceId,status,file));}
}
