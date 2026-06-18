package com.fishwash.vr_fish_basin;

import lombok.Data;
import java.util.List;

@Data
public class VRHapticPattern {
    private double intensity;
    private List<Integer> vibrationPattern;
    private List<Integer> celebrationPattern;
    private String feelDescription;
    private String modeDescription;
    private String sprayDescription;
    private boolean thresholdCrossed;
}
