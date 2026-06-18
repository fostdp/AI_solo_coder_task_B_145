package com.fishwash.friction_analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishwash.config.FishWashProperties;
import com.fishwash.dto.FrictionAnalysisRequest;
import com.fishwash.entity.FishWashDevice;
import com.fishwash.entity.FrictionAnalysis;
import com.fishwash.entity.VibrationMode;
import com.fishwash.repository.FishWashDeviceRepository;
import com.fishwash.repository.FrictionAnalysisRepository;
import com.fishwash.repository.VibrationModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class FrictionAnalyzer {

    private final FrictionAnalysisRepository frictionAnalysisRepository;
    private final FishWashDeviceRepository fishWashDeviceRepository;
    private final VibrationModeRepository vibrationModeRepository;
    private final FishWashProperties fishWashProperties;
    private final ObjectMapper objectMapper;

    public FrictionAnalysis analyze(Integer deviceId, FrictionAnalysisRequest req) {
        FishWashDevice device = fishWashDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        if (req == null) {
            req = new FrictionAnalysisRequest();
        }

        FishWashProperties.FrictionProps fp = fishWashProperties.getFriction();

        double N = req.getNormalForceN() != null ? req.getNormalForceN() : fp.getDefaultNormalForce();
        double mu = req.getFrictionCoefficient() != null ? req.getFrictionCoefficient() : fp.getDefaultFrictionCoefficient();
        double v = req.getFrictionVelocityMps() != null ? req.getFrictionVelocityMps() : fp.getDefaultVelocity();
        double strokeLen = (req.getStrokeLengthMm() != null ? req.getStrokeLengthMm() : fp.getTypicalStrokeLengthMeters() * 1000) / 1000.0;
        double strokeFreq = req.getStrokeFrequencyHz() != null ? req.getStrokeFrequencyHz() : 2.0;
        double handleDiameter = (req.getHandleDiameterMm() != null ? req.getHandleDiameterMm() : fp.getHandleDiameterMeters() * 1000) / 1000.0;
        double skinAreaCm2 = req.getSkinContactAreaCm2() != null ? req.getSkinContactAreaCm2() : 12.0;
        double duration = req.getDurationSeconds() != null ? req.getDurationSeconds() : 30.0;
        double zetaDevice = req.getDampingRatio() != null ? req.getDampingRatio() : 0.01;

        double muStatic = fp.getStickSlipBreakawayCoeff();
        double muKinetic = mu * fp.getKineticReductionFactor();
        double waterLubrication = fp.getSkinWaterFilmLubrication();
        double effectiveMu = Math.max(fp.getMinimumEffectiveMu(), mu * (1.0 - waterLubrication * 0.4));

        double Ftangential = effectiveMu * N;

        double contactPressurePa = N / (skinAreaCm2 * 1e-4);
        double shearingStressPa = Ftangential / (skinAreaCm2 * 1e-4);

        double contactArcLength = handleDiameter * Math.asin(Math.min(1.0,
                Math.sqrt(skinAreaCm2 * 1e-4) / handleDiameter));
        double handleRadius = handleDiameter / 2.0;

        double excitationTorquePerHandle = Ftangential * handleRadius;
        double totalTorque = 2.0 * excitationTorquePerHandle;

        double powerOneStroke = Ftangential * v;
        double instantaneousPower = 2.0 * powerOneStroke;

        double cycleEnergy = Ftangential * strokeLen * 2.0;
        double totalEnergyJ = cycleEnergy * strokeFreq * duration;

        double k_stick = fp.getStickSlipStiffness();
        double criticalBreakawayDisp = (muStatic * N) / k_stick;
        double bronzeDensity = fishWashProperties.getMaterial()
                .getProfiles().getOrDefault("bronze-standard", new FishWashProperties.MaterialProfile())
                .getDensity();
        double stickSlipFreq = (1.0 / (2.0 * Math.PI)) * Math.sqrt(k_stick /
                (skinAreaCm2 * 1e-4 * 2.0 * bronzeDensity * 0.003));

        if (v > 0.01 && criticalBreakawayDisp > 0) {
            stickSlipFreq = v / criticalBreakawayDisp;
        }
        stickSlipFreq = Math.min(1200.0, Math.max(50.0, stickSlipFreq));

        List<VibrationMode> modes = vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(deviceId);
        double dominantFreq = 280.0;
        double dominantCoupling = 0.7;
        if (!modes.isEmpty()) {
            VibrationMode bestMode = modes.stream()
                    .min(Comparator.comparingDouble(m -> Math.abs(m.getResonanceFreq() - stickSlipFreq)))
                    .orElse(modes.get(0));
            dominantFreq = bestMode.getResonanceFreq();
            dominantCoupling = bestMode.getFluidCouplingFactor() != null
                    ? bestMode.getFluidCouplingFactor() : 0.7;
        }

        double freqMatchFactor = 1.0 / (1.0 + Math.pow((stickSlipFreq - dominantFreq) / dominantFreq, 2));
        double dampingLossFactor = zetaDevice * 2.0;
        double energyLossCoef = fp.getEnergyLossCoefficient();
        double transmissionEfficiency = freqMatchFactor * (1.0 - dampingLossFactor) * energyLossCoef;

        double resonanceCoupling = fp.getHandResonanceCoupling() * freqMatchFactor;

        double mechanicalPowerToBasin = instantaneousPower * transmissionEfficiency;
        double expectedAmplitudeMicrons = 1e6 * Math.sqrt(2.0 * mechanicalPowerToBasin /
                (Math.max(1e-6, dominantFreq) * Math.PI * 2.0 * k_stick * 0.001));

        Map<String, Object> mechParams = buildMechanicalParams(fp, N, mu, muStatic, muKinetic, effectiveMu,
                waterLubrication, v, strokeLen, strokeFreq, handleDiameter, handleRadius, contactArcLength,
                skinAreaCm2, contactPressurePa, shearingStressPa, excitationTorquePerHandle, totalTorque,
                criticalBreakawayDisp, stickSlipFreq, dominantFreq, freqMatchFactor, dampingLossFactor,
                resonanceCoupling, transmissionEfficiency, expectedAmplitudeMicrons, mechanicalPowerToBasin);

        String mechParamsJson;
        try {
            mechParamsJson = objectMapper.writeValueAsString(mechParams);
        } catch (Exception e) {
            mechParamsJson = "{}";
        }

        FrictionAnalysis analysis = new FrictionAnalysis();
        analysis.setDeviceId(deviceId);
        analysis.setNormalForceN(N);
        analysis.setFrictionCoefficient(effectiveMu);
        analysis.setFrictionVelocityMps(v);
        analysis.setTangentialForceN(Ftangential);
        analysis.setExcitationPowerW(instantaneousPower);
        analysis.setCumulativeEnergyJ(totalEnergyJ);
        analysis.setStickSlipFrequencyHz(stickSlipFreq);
        analysis.setExcitationEfficiency(transmissionEfficiency);
        analysis.setResonanceCouplingFactor(resonanceCoupling);
        analysis.setMechanicalParams(mechParamsJson);
        analysis.setAnalyzedAt(LocalDateTime.now());

        return frictionAnalysisRepository.save(analysis);
    }

    @Async("frictionAnalysisExecutor")
    public CompletableFuture<FrictionAnalysis> analyzeAsync(Integer deviceId, FrictionAnalysisRequest req) {
        return CompletableFuture.completedFuture(analyze(deviceId, req));
    }

    public boolean checkSprayThresholdReached(FrictionAnalysis analysis, double thresholdCm) {
        if (analysis == null || analysis.getExcitationPowerW() == null) return false;
        double power = analysis.getExcitationPowerW();
        double sprayEstimateCm = Math.pow(power / 4.5, 0.6) * 10.0;
        return sprayEstimateCm >= thresholdCm;
    }

    public double estimateSprayHeightCm(double excitationPowerW) {
        if (excitationPowerW <= 0) return 0.0;
        return Math.pow(excitationPowerW / 4.5, 0.6) * 10.0;
    }

    public List<FrictionAnalysis> getHistory(Integer deviceId) {
        return frictionAnalysisRepository.findByDeviceIdOrderByAnalyzedAtDesc(deviceId);
    }

    public FrictionAnalysis getLatest(Integer deviceId) {
        return frictionAnalysisRepository.findTopByDeviceIdOrderByAnalyzedAtDesc(deviceId);
    }

    public org.springframework.data.domain.Page<FrictionAnalysis> getPaged(
            Integer deviceId, int page, int size) {
        return frictionAnalysisRepository.findByDeviceIdOrderByAnalyzedAtDesc(
                deviceId, org.springframework.data.domain.PageRequest.of(page, size));
    }

    private Map<String, Object> buildMechanicalParams(
            FishWashProperties.FrictionProps fp, double N, double mu, double muStatic, double muKinetic,
            double effectiveMu, double waterLubrication, double v, double strokeLen, double strokeFreq,
            double handleDiameter, double handleRadius, double contactArcLength, double skinAreaCm2,
            double contactPressurePa, double shearingStressPa, double excitationTorquePerHandle,
            double totalTorque, double criticalBreakawayDisp, double stickSlipFreq, double dominantFreq,
            double freqMatchFactor, double dampingLossFactor, double resonanceCoupling,
            double transmissionEfficiency, double expectedAmplitudeMicrons, double mechanicalPowerToBasin) {

        Map<String, Object> mechParams = new LinkedHashMap<>();
        mechParams.put("experimentReference", fp.getExperimentReference());
        mechParams.put("experimentMethod", fp.getExperimentMethod());
        mechParams.put("frictionCoefficientExperimentalValue", fp.getFrictionCoefficientExperimental());
        mechParams.put("normalForceN", N);
        mechParams.put("frictionCoefficient", mu);
        mechParams.put("effectiveFrictionCoefficientAfterLubrication", effectiveMu);
        mechParams.put("staticFrictionCoefficient", muStatic);
        mechParams.put("kineticFrictionCoefficient", muKinetic);
        mechParams.put("waterFilmLubricationFactor", waterLubrication);
        mechParams.put("waterFilmLubricationReference", fp.getWaterFilmLubricationReference());
        mechParams.put("minimumEffectiveMu", fp.getMinimumEffectiveMu());
        mechParams.put("frictionVelocityMps", v);
        mechParams.put("strokeLengthMeters", strokeLen);
        mechParams.put("strokeFrequencyHz", strokeFreq);
        mechParams.put("handleDiameterMeters", handleDiameter);
        mechParams.put("handleRadiusMeters", handleRadius);
        mechParams.put("contactArcLengthMeters", contactArcLength);
        mechParams.put("skinContactAreaCm2", skinAreaCm2);
        mechParams.put("contactPressurePa", contactPressurePa);
        mechParams.put("skinShearingStressPa", shearingStressPa);
        mechParams.put("excitationTorquePerHandleNm", excitationTorquePerHandle);
        mechParams.put("totalBinauralTorqueNm", totalTorque);
        mechParams.put("criticalBreakawayDisplacementMicrons", criticalBreakawayDisp * 1e6);
        mechParams.put("stickSlipDominantFrequencyHz", stickSlipFreq);
        mechParams.put("deviceResonanceFrequencyHz", dominantFreq);
        mechParams.put("resonanceFrequencyMatchFactor", freqMatchFactor);
        mechParams.put("dampingLossFactor", dampingLossFactor);
        mechParams.put("handBasinCouplingFactor", resonanceCoupling);
        mechParams.put("mechanicalTransmissionEfficiency", transmissionEfficiency);
        mechParams.put("expectedVibrationAmplitudeMicrons", expectedAmplitudeMicrons);
        mechParams.put("mechanicalPowerToBasinW", mechanicalPowerToBasin);
        mechParams.put("stickSlipThresholdMap", buildStickSlipThresholdMap(N, muStatic, muKinetic, v));
        mechParams.put("forceFreeBodyDiagram", buildFreeBodyDiagram(N, effectiveMu, v, handleRadius));
        mechParams.put("analysisNotes", Arrays.asList(
                "F_tangential = μ_effective × N （库仑摩擦定律）",
                "μ默认值取自实验测定：" + fp.getFrictionCoefficientExperimental() + "，来源：" + fp.getExperimentReference(),
                "水膜润滑减摩效应：有效摩擦系数降低约" + String.format("%.0f%%", waterLubrication * 100) + "，" + fp.getWaterFilmLubricationReference(),
                "粘滑频率估算：f_stickslip ≈ v_摩擦 / δ_breakaway，δ_breakaway = μ_s·N/k_stick",
                "激励经双耳支点以扭矩T = F_t × R_handle 方式传递到盆壁",
                "当粘滑主导频率接近某阶模态频率时，频率匹配因子→1，激励效率显著提高",
                "润湿皮肤水膜起润滑作用：μ_effective = μ × (1 - 0.4 × k_water_lub)"));
        return mechParams;
    }

    public Map<String, Object> buildStickSlipThresholdMap(double N, double muS, double muK, double v) {
        Map<String, Object> data = new LinkedHashMap<>();
        double staticForceMax = muS * N;
        double kineticForce = muK * N;

        List<Map<String, Double>> transitionCurve = new ArrayList<>();
        for (double vv : new double[]{0.0, 0.05, 0.1, 0.3, 0.6, 1.0, 1.5, 2.0, 3.0}) {
            Map<String, Double> pt = new LinkedHashMap<>();
            double ratio = Math.min(1.0, vv / 0.5);
            double f_val = staticForceMax - ratio * (staticForceMax - kineticForce);
            pt.put("velocity_mps", vv);
            pt.put("friction_force_n", f_val);
            transitionCurve.add(pt);
        }
        data.put("staticFrictionPeakN", staticForceMax);
        data.put("kineticFrictionN", kineticForce);
        data.put("stribeckTransitionCurve", transitionCurve);
        data.put("currentOperatingVelocityMps", v);
        data.put("operatingForceN", kineticForce + (staticForceMax - kineticForce) *
                Math.exp(-Math.max(0.0, v) / 0.3));
        return data;
    }

    public Map<String, Object> buildFreeBodyDiagram(double N, double muEff, double v, double R_handle) {
        Map<String, Object> diagram = new LinkedHashMap<>();

        Map<String, Object> forcesOnHand = new LinkedHashMap<>();
        forcesOnHand.put("N_hand", Map.of("magnitudeN", N, "direction", "radially_inward_toward_handle_center"));
        forcesOnHand.put("F_tangential_hand", Map.of("magnitudeN", muEff * N, "direction", "opposite_to_stroke_velocity"));
        forcesOnHand.put("F_water_lubrication", Map.of("magnitudeN", Math.min(N, 0.2 * N), "direction", "normal_separation_from_surface"));

        Map<String, Object> forcesOnHandle = new LinkedHashMap<>();
        forcesOnHandle.put("N_handle_reaction", Map.of("magnitudeN", N, "direction", "radially_outward"));
        forcesOnHandle.put("F_friction_on_handle", Map.of("magnitudeN", muEff * N, "direction", "same_as_stroke_direction"));
        forcesOnHandle.put("M_torque_excitation", Map.of("magnitudeNm", muEff * N * R_handle, "axis", "circumferential_tangent"));

        Map<String, Object> energyFlow = new LinkedHashMap<>();
        energyFlow.put("input_biomechanical_power_W", muEff * N * v * 2.0);
        energyFlow.put("heat_dissipated_at_interface_W", muEff * N * v * 0.60);
        energyFlow.put("acoustic_noise_emission_W", muEff * N * v * 0.03);
        energyFlow.put("transmitted_to_structure_W", muEff * N * v * 0.37);

        diagram.put("freeBodyHandSide", forcesOnHand);
        diagram.put("freeBodyHandleSide", forcesOnHandle);
        diagram.put("energyFlowBreakdown", energyFlow);
        return diagram;
    }

    public static double calculateTangentialForce(double N, double mu, double waterLubricationFactor) {
        double effectiveMu = Math.max(0.08, mu * (1.0 - waterLubricationFactor * 0.4));
        return effectiveMu * N;
    }

    public static double calculateStickSlipFrequency(double v, double muStatic, double N, double kStick) {
        double criticalBreakawayDisp = (muStatic * N) / kStick;
        if (v > 0.01 && criticalBreakawayDisp > 0) {
            return Math.min(1200.0, Math.max(50.0, v / criticalBreakawayDisp));
        }
        return 280.0;
    }
}
