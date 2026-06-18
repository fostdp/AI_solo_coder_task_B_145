package com.fishwash.shape_comparator;

import com.fishwash.dto.ApiResponse;
import com.fishwash.dto.ShapeComparisonRequest;
import com.fishwash.dto.ShapeComparisonResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/shape-comparator")
@RequiredArgsConstructor
public class ShapeComparatorController {

    private final ShapeComparator shapeComparator;

    @PostMapping("/compare")
    public ResponseEntity<ApiResponse<ShapeComparisonResult>> compare(
            @Valid @RequestBody ShapeComparisonRequest request) {
        ShapeComparisonResult result = shapeComparator.compare(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/compare/async")
    public CompletableFuture<ResponseEntity<ApiResponse<ShapeComparisonResult>>> compareAsync(
            @Valid @RequestBody ShapeComparisonRequest request) {
        return shapeComparator.compareAsync(request)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }
}
