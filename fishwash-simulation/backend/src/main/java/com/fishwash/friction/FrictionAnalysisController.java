package com.fishwash.friction;

import com.fishwash.dto.ApiResponse;
import com.fishwash.dto.FrictionAnalysisRequest;
import com.fishwash.entity.FrictionAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friction")
@RequiredArgsConstructor
public class FrictionAnalysisController {

    private final FrictionAnalysisService frictionAnalysisService;

    @PostMapping("/analyze/{deviceId}")
    public ResponseEntity<ApiResponse<FrictionAnalysis>> analyzeFriction(
            @PathVariable Integer deviceId,
            @RequestBody(required = false) FrictionAnalysisRequest request) {
        FrictionAnalysisRequest req = (request == null) ? new FrictionAnalysisRequest() : request;
        FrictionAnalysis analysis = frictionAnalysisService.analyzeFrictionMechanics(deviceId, req);
        return ResponseEntity.ok(ApiResponse.success(analysis));
    }

    @GetMapping("/history/{deviceId}")
    public ResponseEntity<ApiResponse<List<FrictionAnalysis>>> getHistory(@PathVariable Integer deviceId) {
        List<FrictionAnalysis> history = frictionAnalysisService.getAnalysisHistory(deviceId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/history/{deviceId}/page")
    public ResponseEntity<ApiResponse<Page<FrictionAnalysis>>> getHistoryPaged(
            @PathVariable Integer deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FrictionAnalysis> result = frictionAnalysisService.getAnalysisPaged(deviceId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/latest/{deviceId}")
    public ResponseEntity<ApiResponse<FrictionAnalysis>> getLatest(@PathVariable Integer deviceId) {
        FrictionAnalysis latest = frictionAnalysisService.getLatestAnalysis(deviceId);
        return ResponseEntity.ok(ApiResponse.success(latest));
    }
}
