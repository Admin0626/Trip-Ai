package com.trip.module.destination.controller;

import com.trip.common.result.PageResult;
import com.trip.common.result.R;
import com.trip.module.destination.service.DestinationService;
import com.trip.module.destination.vo.AttractionVO;
import com.trip.module.destination.vo.DestinationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 目的地接口（api/02 · GET /destination/** 游客可读）
 */
@RestController
@RequestMapping("/destination")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService destinationService;

    @GetMapping("/page")
    public R<PageResult<DestinationVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer status) {
        size = Math.min(size, 100);
        return R.ok(destinationService.page(current, size, keyword, province, tag, status));
    }

    @GetMapping("/list")
    public R<List<DestinationVO>> list() {
        return R.ok(destinationService.list());
    }

    @GetMapping("/{id}")
    public R<DestinationVO> detail(@PathVariable Long id) {
        return R.ok(destinationService.detail(id));
    }

    @GetMapping("/hot")
    public R<List<DestinationVO>> hot(@RequestParam(defaultValue = "5") int limit) {
        return R.ok(destinationService.hot(limit));
    }

    @GetMapping("/provinces")
    public R<List<String>> provinces() {
        return R.ok(destinationService.provinces());
    }

    @GetMapping("/{id}/attractions")
    public R<List<AttractionVO>> attractions(@PathVariable Long id) {
        return R.ok(destinationService.attractions(id));
    }
}