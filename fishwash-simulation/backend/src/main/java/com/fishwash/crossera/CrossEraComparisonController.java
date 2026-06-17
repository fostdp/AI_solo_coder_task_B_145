package com.fishwash.crossera;

import com.fishwash.dto.ApiResponse;
import com.fishwash.dto.CrossEraComparisonResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cross-era")
@RequiredArgsConstructor
public class CrossEraComparisonController {

    private final CrossEraComparisonService crossEraComparisonService;

    @GetMapping("/comparison")
    public ResponseEntity<ApiResponse<CrossEraComparisonResult>> compareAncientWithUltrasonic(
            @RequestParam(required = false) Integer deviceId) {
        Integer targetId = (deviceId != null) ? deviceId : 1;
        CrossEraComparisonResult result = crossEraComparisonService
                .compareAncientVsUltrasonic(targetId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
