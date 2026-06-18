package com.fishwash.vr_fish_basin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VRFishBasinEngine — 虚拟交互体验模块")
class VRFishBasinEngineTest {

    private VRFishBasinEngine engine;

    @BeforeEach
    void setUp() {
        engine = new VRFishBasinEngine();
    }

    @Nested
    @DisplayName("simulateFriction 模态与频率")
    class Modes {

        @Test
        @DisplayName("低速摩擦 v=0.3 → 2阶模态 ≈216Hz")
        void lowVelocityMode2() {
            VRFishBasinState s = engine.simulateFriction(1, 0.3, 20.0, true, true);
            assertEquals(2, s.getModeOrder());
            assertTrue(s.getFrequency() >= 200 && s.getFrequency() <= 260,
                    "≈216Hz，实际=" + s.getFrequency());
            assertTrue(s.getIsDragging());
        }

        @Test
        @DisplayName("中速摩擦 v=1.0 → 3阶模态 ≈370Hz")
        void midVelocityMode3() {
            VRFishBasinState s = engine.simulateFriction(1, 1.0, 20.0, true, true);
            assertEquals(3, s.getModeOrder());
            assertTrue(s.getFrequency() >= 340 && s.getFrequency() <= 480,
                    "≈370+修正，实际=" + s.getFrequency());
        }

        @Test
        @DisplayName("高速摩擦 v=2.0 → 4阶模态 ≈590Hz")
        void highVelocityMode4() {
            VRFishBasinState s = engine.simulateFriction(1, 2.0, 20.0, true, true);
            assertEquals(4, s.getModeOrder());
            assertTrue(s.getFrequency() >= 560 && s.getFrequency() <= 720,
                    "≈590+修正，实际=" + s.getFrequency());
        }

        @Test
        @DisplayName("零速度 v=0 → 2阶但频率接近基础216Hz")
        void zeroVelocity() {
            VRFishBasinState s = engine.simulateFriction(1, 0.0, 20.0, true, true);
            assertEquals(2, s.getModeOrder());
            assertTrue(s.getFrequency() >= 200 && s.getFrequency() <= 230,
                    "基础频率，实际=" + s.getFrequency());
        }

        @Test
        @DisplayName("负速度自动钳制为0")
        void negativeVelocityClamped() {
            VRFishBasinState s = engine.simulateFriction(1, -1.0, 20.0, true, true);
            assertTrue(s.getVelocity() >= 0, "速度≥0，实际=" + s.getVelocity());
        }

        @Test
        @DisplayName("负法向力自动钳制为0")
        void negativeForceClamped() {
            VRFishBasinState s = engine.simulateFriction(1, 1.0, -5.0, true, true);
            assertTrue(s.getNormalForceN() >= 0, "N≥0");
            assertTrue(s.getTangentialForceN() >= 0, "F_t≥0");
        }
    }

    @Nested
    @DisplayName("喷水高度随速度递增")
    class Spray {

        @Test
        @DisplayName("静止 v=0 → 几乎不喷水")
        void noSprayAtRest() {
            VRFishBasinState s = engine.simulateFriction(1, 0.0, 20.0, true, true);
            assertTrue(s.getSprayHeightCm() < 2.0,
                    "静止喷水<2cm，实际=" + s.getSprayHeightCm());
        }

        @Test
        @DisplayName("中速 v=1.0 → 显著喷水")
        void midSpray() {
            VRFishBasinState s = engine.simulateFriction(1, 1.0, 20.0, true, true);
            assertTrue(s.getSprayHeightCm() > 5.0,
                    "中速>5cm，实际=" + s.getSprayHeightCm());
        }

        @Test
        @DisplayName("高速 v=2.5 → 更高喷水")
        void highSpray() {
            VRFishBasinState sLow = engine.simulateFriction(1, 1.0, 20.0, true, true);
            VRFishBasinState sHigh = engine.simulateFriction(1, 2.5, 20.0, true, true);
            assertTrue(sHigh.getSprayHeightCm() > sLow.getSprayHeightCm(),
                    "高速>中速，高=" + sHigh.getSprayHeightCm() +
                            "，中=" + sLow.getSprayHeightCm());
        }

        @Test
        @DisplayName("未拖拽 → 功率=0，喷水=0")
        void noDragNoSpray() {
            VRFishBasinState s = engine.simulateFriction(1, 2.0, 20.0, false, true);
            assertEquals(0.0, s.getExcitationPowerW(), 1e-9, "未拖拽功率=0");
            assertEquals(0.0, s.getSprayHeightCm(), 1e-9, "未拖拽喷水=0");
        }
    }

    @Nested
    @DisplayName("振幅递增与阈值突破")
    class Amplitude {

        @Test
        @DisplayName("振幅随速度递增")
        void amplitudeIncreases() {
            double[] amps = new double[3];
            double[] vels = {0.3, 1.0, 2.0};
            for (int i = 0; i < 3; i++) {
                VRFishBasinState s = engine.simulateFriction(1, vels[i], 20.0, true, true);
                amps[i] = s.getAmplitudeMm();
            }
            assertTrue(amps[0] < amps[1], "低速<中速");
            assertTrue(amps[1] < amps[2], "中速<高速");
        }

        @Test
        @DisplayName("低功率低速度 → 阈值未突破")
        void thresholdNotCrossed() {
            VRFishBasinState s = engine.simulateFriction(1, 0.3, 10.0, true, true);
            assertFalse(s.isSprayThresholdCrossed());
        }

        @Test
        @DisplayName("高功率高速度 → 阈值突破")
        void thresholdCrossed() {
            VRFishBasinState s = engine.simulateFriction(1, 2.5, 40.0, true, true);
            assertTrue(s.isSprayThresholdCrossed(),
                    "高功率突破阈值，功率=" + s.getExcitationPowerW() +
                            "W，喷水=" + s.getSprayHeightCm() + "cm");
        }
    }

    @Nested
    @DisplayName("触觉反馈模式 computeHapticPattern")
    class Haptic {

        @Test
        @DisplayName("低速 → 微震")
        void lowFeel() {
            VRFishBasinState s = engine.simulateFriction(1, 0.3, 20.0, true, true);
            VRHapticPattern p = engine.computeHapticPattern(s);
            assertEquals("微震", p.getFeelDescription());
            assertTrue(p.getIntensity() >= 0.1 && p.getIntensity() <= 1.0);
            assertNotNull(p.getVibrationPattern());
            assertTrue(p.getVibrationPattern().size() >= 3);
        }

        @Test
        @DisplayName("中速 → 麻酥感")
        void midFeel() {
            VRFishBasinState s = engine.simulateFriction(1, 1.0, 20.0, true, true);
            VRHapticPattern p = engine.computeHapticPattern(s);
            assertEquals("麻酥感", p.getFeelDescription());
        }

        @Test
        @DisplayName("高速 → 强震")
        void highFeel() {
            VRFishBasinState s = engine.simulateFriction(1, 2.5, 20.0, true, true);
            VRHapticPattern p = engine.computeHapticPattern(s);
            assertEquals("强震", p.getFeelDescription());
        }

        @Test
        @DisplayName("强度随速度递增")
        void intensityIncreases() {
            double[] intensities = new double[3];
            double[] vels = {0.3, 1.0, 2.0};
            for (int i = 0; i < 3; i++) {
                VRFishBasinState s = engine.simulateFriction(1, vels[i], 20.0, true, true);
                intensities[i] = engine.computeHapticPattern(s).getIntensity();
            }
            assertTrue(intensities[0] <= intensities[1] + 0.01,
                    "低速强度≤中速，低=" + intensities[0] + "，中=" + intensities[1]);
            assertTrue(intensities[1] <= intensities[2] + 0.01,
                    "中速≤高速");
            assertTrue(intensities[2] <= 1.0, "最大≤1.0");
        }

        @Test
        @DisplayName("阈值突破带庆祝模式")
        void celebrationPatternOnThreshold() {
            VRFishBasinState s = engine.simulateFriction(1, 2.5, 40.0, true, true);
            VRHapticPattern p = engine.computeHapticPattern(s);
            assertTrue(p.isThresholdCrossed());
            assertNotNull(p.getCelebrationPattern());
            List<Integer> cp = p.getCelebrationPattern();
            assertEquals(5, cp.size(), "庆祝模式5段");
            assertEquals(100, cp.get(0));
            assertEquals(50, cp.get(1));
        }

        @Test
        @DisplayName("未突破阈值无庆祝模式")
        void noCelebrationBelowThreshold() {
            VRFishBasinState s = engine.simulateFriction(1, 0.3, 10.0, true, true);
            VRHapticPattern p = engine.computeHapticPattern(s);
            assertFalse(p.isThresholdCrossed());
            assertNull(p.getCelebrationPattern());
        }

        @Test
        @DisplayName("触觉描述文本包含频率、模态、触感")
        void descriptionComplete() {
            VRFishBasinState s = engine.simulateFriction(1, 1.0, 20.0, true, true);
            VRHapticPattern p = engine.computeHapticPattern(s);
            assertNotNull(p.getFullDescription());
            assertTrue(p.getFullDescription().contains("阶"));
            assertTrue(p.getFullDescription().contains("Hz"));
        }
    }

    @Nested
    @DisplayName("摩擦系数与切向力")
    class Force {

        @Test
        @DisplayName("F_t = μ_eff × N，实验测定μ=0.29±0.03")
        void forceRelation() {
            VRFishBasinState s = engine.simulateFriction(1, 1.0, 20.0, true, true);
            assertEquals(0.29, s.getEffectiveFrictionCoefficient(), 0.03,
                    "μ≈0.29，实验来源：摩擦学学报2022");
            double expected = s.getEffectiveFrictionCoefficient() * s.getNormalForceN();
            assertEquals(expected, s.getTangentialForceN(), 0.01,
                    "F_t = μ_eff × N");
        }

        @Test
        @DisplayName("deviceId、timestamp字段完整")
        void metadataComplete() {
            VRFishBasinState s = engine.simulateFriction(42, 1.0, 20.0, true, true);
            assertEquals(Integer.valueOf(42), s.getDeviceId());
            assertNotNull(s.getTimestamp());
            assertTrue(s.getTimestamp() > 0);
        }
    }
}
