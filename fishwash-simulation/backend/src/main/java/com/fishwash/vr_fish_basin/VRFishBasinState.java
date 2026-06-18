package com.fishwash.vr_fish_basin;

import lombok.Data;

@Data
public class VRFishBasinState {
    private Integer deviceId;
    private double velocity;
    private double normalForceN;
    private double effectiveFrictionCoefficient;
    private double tangentialForceN;
    private double frequency;
    private int modeOrder;
    private double sprayHeightCm;
    private double amplitudeMm;
    private double excitationPowerW;
    private double stickSlipFrequencyHz;
    private double resonanceMatchFactor;
    private boolean isDragging;
    private boolean isActive;
    private boolean sprayThresholdCrossed;
    private long timestamp;
}
