package com.fishwash.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShapeComparisonResult {

    private BasinResult circleBasin;
    private BasinResult squareBasin;
    private String frequencyRatio;
    private String shapeEffectDescription;
    private String modePhysicalInterpretation;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasinResult {
        private String shapeName;
        private String characteristicLength;
        private Double dryResonanceFreq;
        private Double wetResonanceFreq;
        private Double fluidCouplingFactor;
        private Double dampingRatio;
        private Integer nodesCount;
        private Double bendingStiffness;
        private Double massPerUnitArea;
        private Double modalDensity;
    }
}
