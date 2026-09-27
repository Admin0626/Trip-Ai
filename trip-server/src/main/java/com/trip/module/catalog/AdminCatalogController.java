package com.trip.module.catalog;

import com.trip.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {
    private final CatalogService service;

    @GetMapping("/destinations") public R<?> destinations(@RequestParam(defaultValue="1") long current, @RequestParam(defaultValue="20") long size,
            @RequestParam(required=false) String keyword, @RequestParam(required=false) Integer status) { return R.ok(service.destinations(current, size, keyword, status)); }
    @GetMapping("/destinations/{id}") public R<?> destination(@PathVariable long id) { return R.ok(service.destination(id)); }
    @PostMapping("/destinations") public R<Long> createDestination(@Valid @RequestBody CatalogSaveDTO.DestinationInput input) { return R.ok(service.saveDestination(null, input)); }
    @PutMapping("/destinations/{id}") public R<?> updateDestination(@PathVariable long id, @Valid @RequestBody CatalogSaveDTO.DestinationInput input) { service.saveDestination(id, input); return R.ok(); }
    @DeleteMapping("/destinations/{id}") public R<?> deleteDestination(@PathVariable long id) { service.deleteDestination(id); return R.ok(); }
    @PutMapping("/destinations/{id}/status") public R<?> destinationStatus(@PathVariable long id, @Valid @RequestBody CatalogSaveDTO.StatusInput input) { service.destinationStatus(id, input.status()); return R.ok(); }
    @GetMapping("/destinations/{id}/attractions") public R<?> attractions(@PathVariable long id) { return R.ok(service.attractions(id)); }
    @PostMapping("/destinations/{id}/attractions") public R<Long> createAttraction(@PathVariable long id, @Valid @RequestBody CatalogSaveDTO.AttractionInput input) { return R.ok(service.saveAttraction(id, null, input)); }
    @PutMapping("/attractions/{id}") public R<?> updateAttraction(@PathVariable long id, @Valid @RequestBody CatalogSaveDTO.AttractionInput input) { service.saveAttraction(null, id, input); return R.ok(); }
    @DeleteMapping("/attractions/{id}") public R<?> deleteAttraction(@PathVariable long id) { service.deleteAttraction(id); return R.ok(); }

    @GetMapping("/routes") public R<?> routes(@RequestParam(defaultValue="1") long current, @RequestParam(defaultValue="20") long size,
            @RequestParam(required=false) String keyword, @RequestParam(required=false) Integer status, @RequestParam(required=false) Long destinationId) { return R.ok(service.routes(current, size, keyword, status, destinationId)); }
    @GetMapping("/routes/{id}") public R<?> route(@PathVariable long id) { return R.ok(service.route(id)); }
    @PostMapping("/routes") public R<Long> createRoute(@AuthenticationPrincipal Long userId, @Valid @RequestBody CatalogSaveDTO.RouteInput input) { return R.ok(service.saveRoute(null, userId, input)); }
    @PutMapping("/routes/{id}") public R<?> updateRoute(@PathVariable long id, @AuthenticationPrincipal Long userId, @Valid @RequestBody CatalogSaveDTO.RouteInput input) { service.saveRoute(id, userId, input); return R.ok(); }
    @DeleteMapping("/routes/{id}") public R<?> deleteRoute(@PathVariable long id) { service.deleteRoute(id); return R.ok(); }
    @PutMapping("/routes/{id}/status") public R<?> routeStatus(@PathVariable long id, @Valid @RequestBody CatalogSaveDTO.StatusInput input) { service.routeStatus(id, input.status()); return R.ok(); }
    @PutMapping("/routes/{id}/top") public R<?> top(@PathVariable long id, @Valid @RequestBody CatalogSaveDTO.TopInput input) { service.top(id, input.isTop()); return R.ok(); }
    @PutMapping("/routes/{id}/weight") public R<?> weight(@PathVariable long id, @Valid @RequestBody CatalogSaveDTO.WeightInput input) { service.weight(id, input.recommendWeight()); return R.ok(); }
}
