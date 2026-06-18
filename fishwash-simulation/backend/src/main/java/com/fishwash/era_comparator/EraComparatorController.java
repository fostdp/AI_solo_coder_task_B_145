package com.fishwash.era_comparator;

import com.fishwash.dto.ApiResponse;
import com.fishwash.dto.CrossEraComparisonResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/era-comparator")
@RequiredArgsConstructor
public class EraComparatorController {

    private final EraComparator eraComparator;

    @GetMapping("/compare")
    public ResponseEntity<ApiResponse<CrossEraComparisonResult>> compare(
            @RequestParam(defaultValue = "1") Integer ancientDeviceId) {
        CrossEraComparisonResult result = eraComparator.compareAncientVsModern(ancientDeviceId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/compare/async")
    public CompletableFuture<ResponseEntity<ApiResponse<CrossEraComparisonResult>>> compareAsync(
            @RequestParam(defaultValue = "1") Integer ancientDeviceId) {
        return eraComparator.compareAncientVsModernAsync(ancientDeviceId)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }
}
