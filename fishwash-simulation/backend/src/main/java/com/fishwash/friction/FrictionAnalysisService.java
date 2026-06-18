package com.fishwash.friction;

import com.fishwash.dto.FrictionAnalysisRequest;
import com.fishwash.entity.FrictionAnalysis;
import com.fishwash.friction_analyzer.FrictionAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FrictionAnalysisService {

    private final FrictionAnalyzer frictionAnalyzer;

    public FrictionAnalysis analyzeFrictionMechanics(Integer deviceId, FrictionAnalysisRequest req) {
        return frictionAnalyzer.analyze(deviceId, req);
    }

    public List<FrictionAnalysis> getAnalysisHistory(Integer deviceId) {
        return frictionAnalyzer.getHistory(deviceId);
    }

    public FrictionAnalysis getLatestAnalysis(Integer deviceId) {
        return frictionAnalyzer.getLatest(deviceId);
    }

    public Page<FrictionAnalysis> getAnalysisPaged(Integer deviceId, int page, int size) {
        return frictionAnalyzer.getPaged(deviceId, page, size);
    }

    public Map<String, Object> buildStickSlipThresholdMap(double N, double muS, double muK, double v) {
        return frictionAnalyzer.buildStickSlipThresholdMap(N, muS, muK, v);
    }

    public Map<String, Object> buildFreeBodyDiagram(double N, double muEff, double v, double R_handle) {
        return frictionAnalyzer.buildFreeBodyDiagram(N, muEff, v, R_handle);
    }
}

