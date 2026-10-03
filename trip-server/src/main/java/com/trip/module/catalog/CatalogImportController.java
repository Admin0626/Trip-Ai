package com.trip.module.catalog;

import com.trip.common.exception.BizException;
import com.trip.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/catalog/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CatalogImportController {
    private final CatalogImportService service;
    private byte[] bytes(MultipartFile file) {
        if(file.isEmpty() || file.getSize()>1048576)throw new BizException(400,"请选择1MiB以内的JSON文件");
        try {return file.getBytes();}catch(Exception e){throw new BizException(400,"无法读取导入文件");}
    }
    @PostMapping("/preview") public R<?> preview(@RequestParam MultipartFile file) {return R.ok(service.preview(bytes(file)));}
    @PostMapping("/commit") public R<?> commit(@AuthenticationPrincipal Long uid,@RequestParam MultipartFile file,
            @RequestParam String requestId,@RequestParam String confirmation) {return R.ok(service.commit(uid,bytes(file),requestId,confirmation));}
}
