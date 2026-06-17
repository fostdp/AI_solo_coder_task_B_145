package com.fishwash.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrossEraComparisonResult {

    private DeviceProfile ancientFishWash;
    private DeviceProfile modernUltrasonic;
    private List<RadarDataPoint> radarComparison;
    private String eraInterpretation;
    private String vibrationParadigmDifference;
    private EnergyEfficiencyComparison energyEfficiency;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceProfile {
        private String name;
        private String era;
        private String workingPrinciple;
        private Double frequencyHz;
        private Double particleSizeMicrons;
        private Double energyInputW;
        private Double waterSprayHeightCm;
        private Double waterFlowRateMlMin;
        private String activationMethod;
        private String historicalSignificance;
        private String material;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RadarDataPoint {
        private String label;
        private String dimension;
        private Double ancientValueNormalized;
        private Double modernValueNormalized;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnergyEfficiencyComparison {
        private Double ancientJoulesPerMl;
        private Double modernJoulesPerMl;
        private Double efficiencyRatio;
        private String interpretation;
    }
}
