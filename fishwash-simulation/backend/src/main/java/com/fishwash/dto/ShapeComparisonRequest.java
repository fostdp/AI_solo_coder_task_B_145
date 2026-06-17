package com.fishwash.dto;

public class ShapeComparisonRequest {

    private Integer modeOrder = 2;
    private Double materialDensity = 8500.0;
    private Double elasticModulus = 1.0e11;
    private Double poissonRatio = 0.34;
    private Double thickness = 0.0025;
    private Double circleRadius = 0.18;
    private Double squareSide = 0.36;
    private Double waterDepth = 0.098;

    public Integer getModeOrder() { return modeOrder; }
    public void setModeOrder(Integer modeOrder) { this.modeOrder = modeOrder; }

    public Double getMaterialDensity() { return materialDensity; }
    public void setMaterialDensity(Double materialDensity) { this.materialDensity = materialDensity; }

    public Double getElasticModulus() { return elasticModulus; }
    public void setElasticModulus(Double elasticModulus) { this.elasticModulus = elasticModulus; }

    public Double getPoissonRatio() { return poissonRatio; }
    public void setPoissonRatio(Double poissonRatio) { this.poissonRatio = poissonRatio; }

    public Double getThickness() { return thickness; }
    public void setThickness(Double thickness) { this.thickness = thickness; }

    public Double getCircleRadius() { return circleRadius; }
    public void setCircleRadius(Double circleRadius) { this.circleRadius = circleRadius; }

    public Double getSquareSide() { return squareSide; }
    public void setSquareSide(Double squareSide) { this.squareSide = squareSide; }

    public Double getWaterDepth() { return waterDepth; }
    public void setWaterDepth(Double waterDepth) { this.waterDepth = waterDepth; }
}
