package com.fishwash.friction_analyzer;

import com.fishwash.dto.ApiResponse;
import com.fishwash.dto.FrictionAnalysisRequest;
import com.fishwash.entity.FrictionAnalysis;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/friction-analyzer")
@RequiredArgsConstructor
public class FrictionAnalyzerController {

    private final FrictionAnalyzer frictionAnalyzer;

    @PostMapping("/analyze/{deviceId}")
    public ResponseEntity<ApiResponse<FrictionAnalysis>> analyze(
            @PathVariable Integer deviceId,
            @Valid @RequestBody(required = false) FrictionAnalysisRequest request) {
        FrictionAnalysis result = frictionAnalyzer.analyze(deviceId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/analyze/{deviceId}/async")
    public CompletableFuture<ResponseEntity<ApiResponse<FrictionAnalysis>>> analyzeAsync(
            @PathVariable Integer deviceId,
            @Valid @RequestBody(required = false) FrictionAnalysisRequest request) {
        return frictionAnalyzer.analyzeAsync(deviceId, request)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @GetMapping("/history/{deviceId}")
    public ResponseEntity<ApiResponse<List<FrictionAnalysis>>> getHistory(@PathVariable Integer deviceId) {
        return ResponseEntity.ok(ApiResponse.success(frictionAnalyzer.getHistory(deviceId)));
    }

    @GetMapping("/history/{deviceId}/paged")
    public ResponseEntity<ApiResponse<Page<FrictionAnalysis>>> getHistoryPaged(
            @PathVariable Integer deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(frictionAnalyzer.getPaged(deviceId, page, size)));
    }

    @GetMapping("/latest/{deviceId}")
    public ResponseEntity<ApiResponse<FrictionAnalysis>> getLatest(@PathVariable Integer deviceId) {
        FrictionAnalysis result = frictionAnalyzer.getLatest(deviceId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/threshold-check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkSprayThreshold(
            @RequestBody Map<String, Double> params) {
        double power = params.getOrDefault("excitationPowerW", 0.0);
        double threshold = params.getOrDefault("thresholdCm", 10.0);
        double sprayCm = frictionAnalyzer.estimateSprayHeightCm(power);
        boolean reached = sprayCm >= threshold;
        Map<String, Object> result = Map.of(
                "estimatedSprayHeightCm", sprayCm,
                "thresholdCm", threshold,
                "sprayThresholdReached", reached
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
