package com.fishwash.friction_analyzer;

import com.fishwash.config.FishWashProperties;
import com.fishwash.dto.FrictionAnalysisRequest;
import com.fishwash.entity.FrictionAnalysis;
import com.fishwash.repository.FishWashDeviceRepository;
import com.fishwash.repository.FrictionAnalysisRepository;
import com.fishwash.repository.VibrationModeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FrictionAnalyzer — 摩擦力分析模块")
class FrictionAnalyzerTest {

    @Mock
    private FrictionAnalysisRepository frictionAnalysisRepository;
    @Mock
    private FishWashDeviceRepository fishWashDeviceRepository;
    @Mock
    private VibrationModeRepository vibrationModeRepository;

    private FrictionAnalyzer frictionAnalyzer;

    @BeforeEach
    void setUp() {
        FishWashProperties props = buildTestProperties();
        frictionAnalyzer = new FrictionAnalyzer(
                frictionAnalysisRepository,
                fishWashDeviceRepository,
                vibrationModeRepository,
                props,
                new ObjectMapper());
        when(fishWashDeviceRepository.findById(any()))
                .thenReturn(Optional.of(buildTestDevice()));
        when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(any()))
                .thenReturn(Collections.emptyList());
        when(frictionAnalysisRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private FishWashProperties buildTestProperties() {
        FishWashProperties props = new FishWashProperties();
        FishWashProperties.FrictionProps fp = new FishWashProperties.FrictionProps();
        fp.setDefaultNormalForce(20.0);
        fp.setDefaultFrictionCoefficient(0.29);
        fp.setDefaultVelocity(1.2);
        fp.setStickSlipBreakawayCoeff(0.42);
        fp.setKineticReductionFactor(0.75);
        fp.setSkinWaterFilmLubrication(0.22);
        fp.setMinimumEffectiveMu(0.08);
        fp.setTypicalStrokeLengthMeters(0.08);
        fp.setHandleDiameterMeters(0.06);
        fp.setStickSlipStiffness(5000.0);
        fp.setExperimentReference("摩擦学学报2022");
        fp.setWaterFilmLubricationReference("水润滑减摩22%");
        fp.setFrictionCoefficientExperimental(0.29);
        fp.setEnergyLossCoefficient(0.37);
        fp.setHandResonanceCoupling(0.85);
        props.setFriction(fp);
        FishWashProperties.MaterialProfiles mp = new FishWashProperties.MaterialProfiles();
        FishWashProperties.MaterialProfile bp = new FishWashProperties.MaterialProfile();
        bp.setDensity(8570.0);
        mp.setProfiles(Map.of("bronze-standard", bp));
        props.setMaterial(mp);
        return props;
    }

    private com.fishwash.entity.FishWashDevice buildTestDevice() {
        com.fishwash.entity.FishWashDevice d = new com.fishwash.entity.FishWashDevice();
        d.setId(1);
        d.setName("国博鱼洗");
        return d;
    }

    @Nested
    @DisplayName("库仑摩擦定律验证")
    class CoulombsLaw {

        @Test
        @DisplayName("F_t = μ_eff × N，误差<0.01N")
        void tangentialForceEqualsMuTimesN() {
            FrictionAnalysisRequest req = new FrictionAnalysisRequest();
            req.setNormalForceN(20.0);
            req.setFrictionCoefficient(0.29);
            req.setFrictionVelocityMps(1.2);

            FrictionAnalysis r = frictionAnalyzer.analyze(1, req);

            double muEff = r.getFrictionCoefficient();
            double expected = muEff * r.getNormalForceN();
            assertEquals(expected, r.getTangentialForceN(), 0.01,
                    "F_t ≈ μ_eff × N，实际=" + r.getTangentialForceN() +
                            "，期望=" + expected);
        }

        @Test
        @DisplayName("法向力线性递增→切向力线性递增")
        void normalForceLinearScale() {
            FrictionAnalysisRequest req10 = new FrictionAnalysisRequest();
            req10.setNormalForceN(10.0);
            req10.setFrictionCoefficient(0.29);
            req10.setFrictionVelocityMps(1.0);

            FrictionAnalysisRequest req50 = new FrictionAnalysisRequest();
            req50.setNormalForceN(50.0);
            req50.setFrictionCoefficient(0.29);
            req50.setFrictionVelocityMps(1.0);

            FrictionAnalysis r10 = frictionAnalyzer.analyze(1, req10);
            FrictionAnalysis r50 = frictionAnalyzer.analyze(1, req50);

            double ratio = r50.getTangentialForceN() / r10.getTangentialForceN();
            assertTrue(ratio > 4.5 && ratio < 5.5,
                    "5倍N→约5倍F_t，实际比=" + ratio);
        }
    }

    @Nested
    @DisplayName("喷水阈值检测")
    class SprayThreshold {

        @Test
        @DisplayName("estimateSprayHeightCm：功率越大喷水越高")
        void sprayHeightMonotonic() {
            double hLow = frictionAnalyzer.estimateSprayHeightCm(3.0);
            double hHigh = frictionAnalyzer.estimateSprayHeightCm(15.0);
            assertTrue(hHigh > hLow, "高功率→高喷水");
            assertTrue(hHigh > 15, "15W→>15cm");
            assertEquals(0, frictionAnalyzer.estimateSprayHeightCm(0.0));
        }

        @Test
        @DisplayName("checkSprayThresholdReached 独立方法")
        void thresholdMethod() {
            FrictionAnalysis aHigh = new FrictionAnalysis();
            aHigh.setExcitationPowerW(10.0);
            assertTrue(frictionAnalyzer.checkSprayThresholdReached(aHigh, 10.0));

            FrictionAnalysis aLow = new FrictionAnalysis();
            aLow.setExcitationPowerW(2.0);
            assertFalse(frictionAnalyzer.checkSprayThresholdReached(aLow, 10.0));

            assertFalse(frictionAnalyzer.checkSprayThresholdReached(null, 10.0));
        }
    }

    @Nested
    @DisplayName("Stribeck曲线")
    class Stribeck {

        @Test
        @DisplayName("buildStickSlipThresholdMap 返回9个速度点，摩擦力递减")
        void ninePointsCurve() {
            Map<String, Object> curve = frictionAnalyzer.buildStickSlipThresholdMap(
                    20.0, 0.42, 0.23, 1.0);
            assertNotNull(curve);
            @SuppressWarnings("unchecked")
            List<Map<String, Double>> points =
                    (List<Map<String, Double>>) curve.get("stribeckTransitionCurve");
            assertNotNull(points);
            assertEquals(9, points.size(), "9个Stribeck点");
            for (int i = 1; i < points.size(); i++) {
                assertTrue(points.get(i).get("friction_force_n") <=
                        points.get(i - 1).get("friction_force_n") + 0.01,
                        "Stribeck曲线非递增，点" + i);
            }
        }
    }

    @Nested
    @DisplayName("受力分析图")
    class FreeBodyDiagram {

        @Test
        @DisplayName("buildFreeBodyDiagram 返回完整受力要素")
        void diagramComplete() {
            Map<String, Object> diag = frictionAnalyzer.buildFreeBodyDiagram(
                    20.0, 0.29, 1.2, 0.03);
            assertNotNull(diag);
            assertTrue(diag.containsKey("freeBodyHandSide"));
            assertTrue(diag.containsKey("freeBodyHandleSide"));
            assertTrue(diag.containsKey("energyFlowBreakdown"));
        }
    }

    @Nested
    @DisplayName("粘滑频率边界")
    class StickSlip {

        @Test
        @DisplayName("calculateStickSlipFrequency：极低速→基础频率≈280Hz，极高速→≤1200Hz上限")
        void frequencyBoundaries() {
            double fLow = FrictionAnalyzer.calculateStickSlipFrequency(0.0, 0.42, 20.0, 5000.0);
            assertEquals(280.0, fLow, 0.01);

            double fHigh = FrictionAnalyzer.calculateStickSlipFrequency(100.0, 0.42, 20.0, 5000.0);
            assertTrue(fHigh <= 1200.0);
        }
    }

    @Nested
    @DisplayName("静态方法：切向力计算")
    class StaticForce {

        @Test
        @DisplayName("calculateTangentialForce：水膜润滑减摩")
        void effectiveLubrication() {
            double Ft = FrictionAnalyzer.calculateTangentialForce(20.0, 0.29, 0.22);
            double muEff = Math.max(0.08, 0.29 * (1.0 - 0.22 * 0.4));
            assertEquals(muEff * 20.0, Ft, 0.001);
            assertTrue(Ft < 0.29 * 20.0, "润滑后<原始μ*N");
        }
    }
}
