package com.fishwash.vr_fish_basin;

import com.fishwash.config.FishWashProperties;
import com.fishwash.entity.FishWashDevice;
import com.fishwash.entity.VibrationMode;
import com.fishwash.repository.FishWashDeviceRepository;
import com.fishwash.repository.VibrationModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class VRFishBasinEngine {

    private final FishWashDeviceRepository fishWashDeviceRepository;
    private final VibrationModeRepository vibrationModeRepository;
    private final FishWashProperties fishWashProperties;

    private static final double BASE_FREQ_2ND = 216.0;
    private static final double BASE_FREQ_3RD = 370.0;
    private static final double BASE_FREQ_4TH = 590.0;
    private static final double SPRY_THRESHOLD_POWER_W = 4.5;

    public VRFishBasinState simulateFriction(Integer deviceId, double velocity, double normalForce,
                                              boolean isDragging, boolean isActive) {
        FishWashDevice device = deviceId != null
                ? fishWashDeviceRepository.findById(deviceId).orElse(null)
                : null;

        double v = Math.max(0.0, velocity);
        double N = Math.max(0.0, normalForce);

        FishWashProperties.FrictionProps fp = fishWashProperties.getFriction();
        double mu = fp.getDefaultFrictionCoefficient();
        double waterLubrication = fp.getSkinWaterFilmLubrication();
        double muStatic = fp.getStickSlipBreakawayCoeff();
        double kStick = fp.getStickSlipStiffness();
        double minEffectiveMu = fp.getMinimumEffectiveMu();

        double effectiveMu = Math.max(minEffectiveMu, mu * (1.0 - waterLubrication * 0.4));
        double Ft = effectiveMu * N;
        double power = isDragging ? Ft * v * 2.0 : 0.0;

        int modeOrder;
        double frequency;
        if (v < 0.4) {
            modeOrder = 2;
            frequency = BASE_FREQ_2ND + v * 80.0;
        } else if (v < 1.5) {
            modeOrder = 3;
            frequency = BASE_FREQ_3RD + (v - 0.4) * 140.0;
        } else {
            modeOrder = 4;
            frequency = BASE_FREQ_4TH + Math.min(4.0, v - 1.5) * 100.0;
        }

        if (device != null && device.getBaselineResonanceFreq() != null) {
            double devFreq = device.getBaselineResonanceFreq();
            frequency = devFreq + (frequency - 280.0) * 0.3;
        }

        List<VibrationMode> modes = deviceId != null
                ? vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(deviceId)
                : Collections.emptyList();
        if (!modes.isEmpty()) {
            final double finalFreq = frequency;
            VibrationMode nearest = modes.stream()
                    .min(Comparator.comparingDouble(m -> Math.abs(m.getResonanceFreq() - finalFreq)))
                    .orElse(null);
            if (nearest != null) {
                modeOrder = nearest.getModeOrder();
                double freqMatch = 1.0 / (1.0 + Math.pow((frequency - nearest.getResonanceFreq()) / nearest.getResonanceFreq(), 2));
                frequency = nearest.getResonanceFreq() * freqMatch + frequency * (1.0 - freqMatch);
            }
        }

        double sprayHeightCm = 0.0;
        double amplitudeMm = 0.0;
        if (power > 0) {
            sprayHeightCm = Math.pow(power / SPRY_THRESHOLD_POWER_W, 0.6) * 10.0;
            amplitudeMm = Math.sqrt(power / Math.max(1.0, frequency)) * 1.5;
        }

        double stickSlipFreq = Ft > 0 && N > 0
                ? Math.min(1200.0, Math.max(50.0, v / Math.max(1e-6, (muStatic * N) / kStick)))
                : 0.0;

        double freqMatchFactor = 0.0;
        if (!modes.isEmpty()) {
            final double finalFreq2 = frequency;
            VibrationMode nearest = modes.stream()
                    .min(Comparator.comparingDouble(m -> Math.abs(m.getResonanceFreq() - finalFreq2)))
                    .orElse(null);
            if (nearest != null) {
                freqMatchFactor = 1.0 / (1.0 + Math.pow((stickSlipFreq - nearest.getResonanceFreq()) / nearest.getResonanceFreq(), 2));
            }
        }

        boolean sprayThresholdCrossed = power >= SPRY_THRESHOLD_POWER_W && sprayHeightCm >= 10.0;

        VRFishBasinState state = new VRFishBasinState();
        state.setDeviceId(deviceId);
        state.setVelocity(v);
        state.setNormalForceN(N);
        state.setEffectiveFrictionCoefficient(effectiveMu);
        state.setTangentialForceN(Ft);
        state.setFrequency(frequency);
        state.setModeOrder(modeOrder);
        state.setSprayHeightCm(sprayHeightCm);
        state.setAmplitudeMm(amplitudeMm);
        state.setExcitationPowerW(power);
        state.setStickSlipFrequencyHz(stickSlipFreq);
        state.setResonanceMatchFactor(freqMatchFactor);
        state.setDragging(isDragging);
        state.setActive(isActive);
        state.setSprayThresholdCrossed(sprayThresholdCrossed);
        state.setTimestamp(System.currentTimeMillis());

        return state;
    }

    @Async("vrInteractionExecutor")
    public CompletableFuture<VRFishBasinState> simulateFrictionAsync(Integer deviceId, double velocity,
                                                                      double normalForce,
                                                                      boolean isDragging, boolean isActive) {
        return CompletableFuture.completedFuture(simulateFriction(deviceId, velocity, normalForce, isDragging, isActive));
    }

    public VRHapticPattern computeHapticPattern(VRFishBasinState state) {
        VRHapticPattern pattern = new VRHapticPattern();

        double v = state.getVelocity();
        int mode = state.getModeOrder();
        double spray = state.getSprayHeightCm();
        double freq = state.getFrequency();

        double intensity = Math.max(0.1, Math.min(1.0, v / 2.5));
        pattern.setIntensity(intensity);

        int basePulseMs = Math.max(10, Math.min(80, 120 - (int) (freq / 20.0)));
        int gapMs = Math.max(5, Math.min(60, 100 - (int) (freq / 15.0)));

        int pulseDuration = (int) Math.round(basePulseMs * intensity);
        int gapDuration = (int) Math.round(gapMs * (1.2 - intensity * 0.5));

        if (spray > 30) {
            pulseDuration = (int) Math.round(pulseDuration * 1.5);
            gapDuration = (int) Math.round(gapDuration * 0.6);
        } else if (spray > 10) {
            pulseDuration = (int) Math.round(pulseDuration * 1.2);
            gapDuration = (int) Math.round(gapDuration * 0.8);
        }

        List<Integer> vibrationPattern = new ArrayList<>();
        if (mode >= 4 && freq > 500) {
            vibrationPattern.addAll(Arrays.asList(pulseDuration, gapDuration, pulseDuration, gapDuration, pulseDuration * 2, gapDuration));
        } else if (mode == 3 && freq > 300) {
            vibrationPattern.addAll(Arrays.asList(pulseDuration, gapDuration, pulseDuration * 2, gapDuration));
        } else {
            vibrationPattern.addAll(Arrays.asList(pulseDuration * 2, gapDuration, pulseDuration, gapDuration));
        }
        if (state.getAmplitudeMm() > 0.5) {
            vibrationPattern.add((int) Math.round(pulseDuration * 1.5));
        }

        pattern.setVibrationPattern(vibrationPattern);

        String feelDescription;
        if (v < 0.5) feelDescription = "微震";
        else if (v < 1.5) feelDescription = "麻酥感";
        else if (v < 2.5) feelDescription = "强震";
        else feelDescription = "剧烈震动";
        pattern.setFeelDescription(feelDescription);

        pattern.setModeDescription(mode + "阶-" + String.format(Locale.US, "%.0f", freq) + "Hz");
        pattern.setSprayDescription(spray > 5 ? "喷水" + String.format(Locale.US, "%.0f", spray) + "cm" : "");
        pattern.setThresholdCrossed(state.isSprayThresholdCrossed());

        if (state.isSprayThresholdCrossed()) {
            pattern.setCelebrationPattern(Arrays.asList(100, 50, 100, 50, 200));
        }

        return pattern;
    }

    public static double estimateSprayFromPower(double powerW) {
        if (powerW <= 0) return 0.0;
        return Math.pow(powerW / SPRY_THRESHOLD_POWER_W, 0.6) * 10.0;
    }

    public static int estimateModeFromVelocity(double velocity) {
        if (velocity < 0.4) return 2;
        if (velocity < 1.5) return 3;
        return 4;
    }
}
