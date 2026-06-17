package com.fishwash.dto;

public class FrictionAnalysisRequest {

    private Double normalForceN = 20.0;
    private Double frictionCoefficient = 0.35;
    private Double frictionVelocityMps = 1.2;
    private Double strokeLengthMm = 80.0;
    private Double strokeFrequencyHz = 2.0;
    private Double handleDiameterMm = 60.0;
    private Double skinContactAreaCm2 = 12.0;
    private Double durationSeconds = 30.0;
    private Double dampingRatio = 0.01;

    public Double getNormalForceN() { return normalForceN; }
    public void setNormalForceN(Double normalForceN) { this.normalForceN = normalForceN; }

    public Double getFrictionCoefficient() { return frictionCoefficient; }
    public void setFrictionCoefficient(Double frictionCoefficient) { this.frictionCoefficient = frictionCoefficient; }

    public Double getFrictionVelocityMps() { return frictionVelocityMps; }
    public void setFrictionVelocityMps(Double frictionVelocityMps) { this.frictionVelocityMps = frictionVelocityMps; }

    public Double getStrokeLengthMm() { return strokeLengthMm; }
    public void setStrokeLengthMm(Double strokeLengthMm) { this.strokeLengthMm = strokeLengthMm; }

    public Double getStrokeFrequencyHz() { return strokeFrequencyHz; }
    public void setStrokeFrequencyHz(Double strokeFrequencyHz) { this.strokeFrequencyHz = strokeFrequencyHz; }

    public Double getHandleDiameterMm() { return handleDiameterMm; }
    public void setHandleDiameterMm(Double handleDiameterMm) { this.handleDiameterMm = handleDiameterMm; }

    public Double getSkinContactAreaCm2() { return skinContactAreaCm2; }
    public void setSkinContactAreaCm2(Double skinContactAreaCm2) { this.skinContactAreaCm2 = skinContactAreaCm2; }

    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; }

    public Double getDampingRatio() { return dampingRatio; }
    public void setDampingRatio(Double dampingRatio) { this.dampingRatio = dampingRatio; }
}
