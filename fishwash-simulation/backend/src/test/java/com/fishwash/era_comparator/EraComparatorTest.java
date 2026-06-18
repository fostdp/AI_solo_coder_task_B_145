package com.fishwash.era_comparator;

import com.fishwash.crossera.dto.CrossEraComparisonResult;
import com.fishwash.crossera.dto.EnergyEfficiency;
import com.fishwash.crossera.dto.RadarDataPoint;
import com.fishwash.crossera.dto.VibrationDeviceProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EraComparator — 跨时代对比模块")
class EraComparatorTest {

    private EraComparator eraComparator;

    @BeforeEach
    void setUp() {
        eraComparator = new EraComparator();
    }

    @Nested
    @DisplayName("compareAncientVsModern 综合对比")
    class Compare {

        @Test
        @DisplayName("古代鱼洗：百Hz级频率，显著喷水")
        void ancientProfile() {
            CrossEraComparisonResult r = eraComparator.compareAncientVsModern(1);
            VibrationDeviceProfile ancient = r.getAncientFishWash();
            assertNotNull(ancient);
            assertTrue(ancient.getFrequencyHz() >= 100,
                    "古代频率≥100Hz，实际=" + ancient.getFrequencyHz());
            assertTrue(ancient.getFrequencyHz() <= 800,
                    "古代频率≤800Hz");
            assertTrue(ancient.getWaterSprayHeightCm() > 20,
                    "古代喷水>20cm");
            assertTrue(ancient.getParticleSizeMicrons() > 100,
                    "古代粒子>100μm（水滴级）");
        }

        @Test
        @DisplayName("现代雾化器：MHz级频率，细雾化")
        void modernProfile() {
            CrossEraComparisonResult r = eraComparator.compareAncientVsModern(1);
            VibrationDeviceProfile modern = r.getModernUltrasonic();
            assertNotNull(modern);
            assertTrue(modern.getFrequencyHz() >= 1_000_000,
                    "现代频率≥1MHz，实际=" + modern.getFrequencyHz());
            assertTrue(modern.getParticleSizeMicrons() >= 1 &&
                            modern.getParticleSizeMicrons() <= 10,
                    "现代粒子1-10μm（干雾级）");
            assertTrue(modern.getWaterSprayHeightCm() < 10,
                    "现代几乎不喷水（干雾）");
        }

        @Test
        @DisplayName("6维雷达数据完整且归一化在0-100范围")
        void radarDataComplete() {
            CrossEraComparisonResult r = eraComparator.compareAncientVsModern(1);
            List<RadarDataPoint> radar = r.getRadarComparison();
            assertNotNull(radar);
            assertEquals(6, radar.size(), "6个维度");
            for (RadarDataPoint pt : radar) {
                assertTrue(pt.getAncientValueNormalized() >= 0 &&
                                pt.getAncientValueNormalized() <= 100,
                        "古代值" + pt.getLabel() + "=" + pt.getAncientValueNormalized() + "应在0-100");
                assertTrue(pt.getModernValueNormalized() >= 0 &&
                                pt.getModernValueNormalized() <= 100,
                        "现代值" + pt.getLabel() + "=" + pt.getModernValueNormalized() + "应在0-100");
                assertNotNull(pt.getDescription());
            }
        }

        @Test
        @DisplayName("能量效率：现代显著优于古代（效率比=古代/现代<1）")
        void energyEfficiency() {
            CrossEraComparisonResult r = eraComparator.compareAncientVsModern(1);
            EnergyEfficiency eff = r.getEnergyEfficiency();
            assertNotNull(eff);
            assertTrue(eff.getAncientJoulesPerMl() > 0);
            assertTrue(eff.getModernJoulesPerMl() > 0);
            double ratio = eff.getEfficiencyRatio();
            assertTrue(ratio > 0 && ratio < 1,
                    "efficiencyRatio=古代/现代 应在(0,1)，实际=" + ratio);
            assertTrue(1.0 / ratio > 50,
                    "现代效率/古代效率>50倍，实际=" + (1.0 / ratio));
        }

        @Test
        @DisplayName("范式差异与物理解读文本非空")
        void interpretationTexts() {
            CrossEraComparisonResult r = eraComparator.compareAncientVsModern(1);
            assertNotNull(r.getEraInterpretation());
            assertNotNull(r.getVibrationParadigmDifference());
            assertTrue(r.getEraInterpretation().length() > 20);
            assertTrue(r.getVibrationParadigmDifference().length() > 20);
        }

        @Test
        @DisplayName("不同设备ID返回不同古代剖面")
        void differentDeviceIds() {
            CrossEraComparisonResult r1 = eraComparator.compareAncientVsModern(1);
            CrossEraComparisonResult r2 = eraComparator.compareAncientVsModern(2);
            assertNotEquals(r1.getAncientFishWash().getDeviceName(),
                    r2.getAncientFishWash().getDeviceName());
        }

        @Test
        @DisplayName("频率差异：现代/古代>1000倍")
        void frequencyGap() {
            CrossEraComparisonResult r = eraComparator.compareAncientVsModern(1);
            double gap = r.getModernUltrasonic().getFrequencyHz() /
                    r.getAncientFishWash().getFrequencyHz();
            assertTrue(gap > 1000, "频率差>1000倍，实际=" + gap);
        }
    }

    @Nested
    @DisplayName("calculateEnergyEfficiencyRatio 静态方法")
    class Efficiency {

        @Test
        @DisplayName("零雾化量不抛异常返回安全值")
        void zeroMlSafe() {
            double ratio = EraComparator.calculateEnergyEfficiencyRatio(
                    100, 100, 0.0, 0.0);
            assertTrue(Double.isFinite(ratio));
            assertTrue(ratio >= 0);
        }

        @Test
        @DisplayName("现代低功率高雾化→更高效")
        void modernMoreEfficient() {
            double ratio = EraComparator.calculateEnergyEfficiencyRatio(
                    50, 20, 5.0, 10.0);
            assertTrue(ratio < 1, "古代消耗更多功率，雾化更少→ratio<1");
        }
    }
}
