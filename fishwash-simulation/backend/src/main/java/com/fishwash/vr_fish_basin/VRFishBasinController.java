package com.fishwash.vr_fish_basin;

import com.fishwash.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/vr-fish-basin")
@RequiredArgsConstructor
public class VRFishBasinController {

    private final VRFishBasinEngine vrEngine;

    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<VRFishBasinState>> simulate(@RequestBody Map<String, Object> params) {
        Integer deviceId = params.get("deviceId") != null ? ((Number) params.get("deviceId")).intValue() : null;
        double velocity = params.get("velocity") != null ? ((Number) params.get("velocity")).doubleValue() : 0.0;
        double normalForce = params.get("normalForce") != null ? ((Number) params.get("normalForce")).doubleValue() : 0.0;
        boolean isDragging = Boolean.TRUE.equals(params.get("isDragging"));
        boolean isActive = params.get("isActive") == null || Boolean.TRUE.equals(params.get("isActive"));

        VRFishBasinState state = vrEngine.simulateFriction(deviceId, velocity, normalForce, isDragging, isActive);
        return ResponseEntity.ok(ApiResponse.success(state));
    }

    @PostMapping("/simulate/async")
    public CompletableFuture<ResponseEntity<ApiResponse<VRFishBasinState>>> simulateAsync(
            @RequestBody Map<String, Object> params) {
        Integer deviceId = params.get("deviceId") != null ? ((Number) params.get("deviceId")).intValue() : null;
        double velocity = params.get("velocity") != null ? ((Number) params.get("velocity")).doubleValue() : 0.0;
        double normalForce = params.get("normalForce") != null ? ((Number) params.get("normalForce")).doubleValue() : 0.0;
        boolean isDragging = Boolean.TRUE.equals(params.get("isDragging"));
        boolean isActive = params.get("isActive") == null || Boolean.TRUE.equals(params.get("isActive"));

        return vrEngine.simulateFrictionAsync(deviceId, velocity, normalForce, isDragging, isActive)
                .thenApply(state -> ResponseEntity.ok(ApiResponse.success(state)));
    }

    @PostMapping("/haptic-pattern")
    public ResponseEntity<ApiResponse<VRHapticPattern>> getHapticPattern(@RequestBody VRFishBasinState state) {
        return ResponseEntity.ok(ApiResponse.success(vrEngine.computeHapticPattern(state)));
    }

    @GetMapping("/estimate-spray")
    public ResponseEntity<ApiResponse<Map<String, Object>>> estimateSpray(
            @RequestParam double powerW) {
        double sprayCm = VRFishBasinEngine.estimateSprayFromPower(powerW);
        int mode = VRFishBasinEngine.estimateModeFromVelocity(powerW > 0 ? Math.sqrt(powerW / 10.0) : 0.0);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "estimatedSprayHeightCm", sprayCm,
                "estimatedModeOrder", mode,
                "excitationPowerW", powerW
        )));
    }
}
