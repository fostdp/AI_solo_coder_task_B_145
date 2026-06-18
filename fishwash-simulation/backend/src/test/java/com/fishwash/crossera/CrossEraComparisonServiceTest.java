package com.fishwash.crossera;

import com.fishwash.config.FishWashProperties;
import com.fishwash.dto.CrossEraComparisonResult;
import com.fishwash.entity.FishWashDevice;
import com.fishwash.entity.VibrationMode;
import com.fishwash.repository.FishWashDeviceRepository;
import com.fishwash.repository.VibrationModeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("跨时代振动对比：鱼洗 vs 超声雾化器")
class CrossEraComparisonServiceTest {

    @Mock private FishWashDeviceRepository fishWashDeviceRepository;
    @Mock private VibrationModeRepository vibrationModeRepository;
    @Mock private FishWashProperties fishWashProperties;

    @InjectMocks
    private CrossEraComparisonService service;

    private FishWashProperties.UltrasonicProps ultrasonicProps;
    private FishWashProperties.FluidProps fluidProps;

    @BeforeEach
    void setUp() {
        ultrasonicProps = new FishWashProperties.UltrasonicProps();
        fluidProps = new FishWashProperties.FluidProps();
        lenient().when(fishWashProperties.getUltrasonic()).thenReturn(ultrasonicProps);
        lenient().when(fishWashProperties.getFluid()).thenReturn(fluidProps);
    }

    private FishWashDevice createHanDevice() {
        FishWashDevice d = new FishWashDevice();
        d.setId(1);
        d.setDeviceCode("FW-HAN-001");
        d.setDeviceName("汉代双鱼纹鱼洗");
        d.setEra("西汉");
        d.setBaselineResonanceFreq(280.0);
        d.setBaselineSprayHeight(15.0);
        d.setBasinShape("CIRCLE");
        d.setStatus("ACTIVE");
        return d;
    }

    private VibrationMode createMode(int modeOrder, double freq, double coupling, double damping) {
        VibrationMode m = new VibrationMode();
        m.setModeOrder(modeOrder);
        m.setResonanceFreq(freq);
        m.setFluidCouplingFactor(coupling);
        m.setDampingRatio(damping);
        return m;
    }

    @Nested
    @DisplayName("正常用例：雾化效率验证")
    class NormalCrossEra {

        @Test
        @DisplayName("现代雾化器频率应在MHz级别")
        void testModernFrequencyInMHz() {
            FishWashDevice device = createHanDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createMode(2, 280.0, 0.7, 0.017)));

            CrossEraComparisonResult result = service.compareAncientVsUltrasonic(1);

            assertNotNull(result.getModernUltrasonic());
            double modernFreq = result.getModernUltrasonic().getFrequencyHz();
            assertTrue(modernFreq >= 1.0e6,
                    "现代雾化器频率应≥1MHz，实际=" + modernFreq);
        }

        @Test
        @DisplayName("古代鱼洗频率应在100-800Hz")
        void testAncientFreqInAcousticRange() {
            FishWashDevice device = createHanDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createMode(2, 280.0, 0.7, 0.017)));

            CrossEraComparisonResult result = service.compareAncientVsUltrasonic(1);

            double ancientFreq = result.getAncientFishWash().getFrequencyHz();
            assertTrue(ancientFreq >= 100 && ancientFreq <= 800,
                    "古代频率应在100-800Hz，实际=" + ancientFreq);
        }

        @Test
        @DisplayName("能量效率比：现代应远优于古代（效率比>1）")
        void testModernEfficiencyFarSuperior() {
            FishWashDevice device = createHanDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createMode(2, 280.0, 0.7, 0.017)));

            CrossEraComparisonResult result = service.compareAncientVsUltrasonic(1);

            CrossEraComparisonResult.EnergyEfficiencyComparison eff = result.getEnergyEfficiency();
            assertNotNull(eff);
            assertTrue(eff.getEfficiencyRatio() > 1.0,
                    "现代/古代效率比应>1（现代更高效），实际=" + eff.getEfficiencyRatio());
            assertTrue(eff.getModernJoulesPerMl() < eff.getAncientJoulesPerMl(),
                    "现代每毫升耗能应更低");
        }

        @Test
        @DisplayName("雷达图6维数据完整且归一化到0-100")
        void testRadarDataCompleteAndNormalized() {
            FishWashDevice device = createHanDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createMode(2, 280.0, 0.7, 0.017)));

            CrossEraComparisonResult result = service.compareAncientVsUltrasonic(1);

            List<CrossEraComparisonResult.RadarDataPoint> radar = result.getRadarComparison();
            assertNotNull(radar);
            assertEquals(6, radar.size(), "应有6维雷达数据");

            for (CrossEraComparisonResult.RadarDataPoint pt : radar) {
                assertNotNull(pt.getLabel(), "标签不应为空");
                assertNotNull(pt.getDimension(), "维度不应为空");
                assertTrue(pt.getAncientValueNormalized() >= 0 && pt.getAncientValueNormalized() <= 100,
                        "古代归一化值应在0-100，dim=" + pt.getDimension());
                assertTrue(pt.getModernValueNormalized() >= 0 && pt.getModernValueNormalized() <= 100,
                        "现代归一化值应在0-100，dim=" + pt.getDimension());
            }
        }

        @Test
        @DisplayName("粒子尺寸差异：古代mm级 vs 现代μm级")
        void testParticleSizeDifference() {
            FishWashDevice device = createHanDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createMode(2, 280.0, 0.7, 0.017)));

            CrossEraComparisonResult result = service.compareAncientVsUltrasonic(1);

            double ancientParticle = result.getAncientFishWash().getParticleSizeMicrons();
            double modernParticle = result.getModernUltrasonic().getParticleSizeMicrons();

            assertTrue(ancientParticle > 100,
                    "古代粒子尺寸应>100μm(mm级水滴)，实际=" + ancientParticle);
            assertTrue(modernParticle < 10,
                    "现代粒子尺寸应<10μm，实际=" + modernParticle);
            assertTrue(ancientParticle / modernParticle > 100,
                    "粒子尺寸差异应>100倍");
        }

        @Test
        @DisplayName("喷水高度：古代远大于现代")
        void testSprayHeightAncientHigher() {
            FishWashDevice device = createHanDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createMode(2, 280.0, 0.7, 0.017)));

            CrossEraComparisonResult result = service.compareAncientVsUltrasonic(1);

            assertTrue(result.getAncientFishWash().getWaterSprayHeightCm() >
                    result.getModernUltrasonic().getWaterSprayHeightCm() * 10,
                    "古代喷水高度应远大于现代");
        }

        @Test
        @DisplayName("范式差异文本不为空且包含关键信息")
        void testParadigmDifferenceContainsKeyInfo() {
            FishWashDevice device = createHanDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createMode(2, 280.0, 0.7, 0.017)));

            CrossEraComparisonResult result = service.compareAncientVsUltrasonic(1);

            assertNotNull(result.getVibrationParadigmDifference());
            assertFalse(result.getVibrationParadigmDifference().isEmpty());
            assertTrue(result.getVibrationParadigmDifference().contains("粘滑") ||
                    result.getVibrationParadigmDifference().contains("压电"),
                    "范式差异应包含激励方式对比");
        }
    }

    @Nested
    @DisplayName("边界用例")
    class BoundaryCrossEra {

        @Test
        @DisplayName("极高baseline喷水高度80cm：古代粒子尺寸应被cap")
        void testVeryHighSprayHeight() {
            FishWashDevice device = createHanDevice();
            device.setBaselineSprayHeight(80.0);
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createMode(2, 400.0, 0.7, 0.017)));

            CrossEraComparisonResult result = service.compareAncientVsUltrasonic(1);

            assertTrue(result.getAncientFishWash().getParticleSizeMicrons() <= 5000,
                    "粒子尺寸应被cap在5000μm以内");
        }

        @Test
        @DisplayName("极低baseline喷水高度0.1cm：能量效率仍可计算")
        void testVeryLowSprayHeight() {
            FishWashDevice device = createHanDevice();
            device.setBaselineSprayHeight(0.1);
            device.setBaselineResonanceFreq(100.0);
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createMode(2, 100.0, 0.5, 0.03)));

            CrossEraComparisonResult result = service.compareAncientVsUltrasonic(1);

            assertNotNull(result.getEnergyEfficiency());
            assertTrue(result.getEnergyEfficiency().getEfficiencyRatio() > 0);
        }

        @Test
        @DisplayName("高阶模态5阶作为主频：仍能正常对比")
        void testHighModeOrderDominant() {
            FishWashDevice device = createHanDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createMode(5, 650.0, 0.6, 0.04)));

            CrossEraComparisonResult result = service.compareAncientVsUltrasonic(1);

            assertNotNull(result);
            assertTrue(result.getAncientFishWash().getFrequencyHz() > 500);
        }
    }

    @Nested
    @DisplayName("异常用例")
    class AbnormalCrossEra {

        @Test
        @DisplayName("指定deviceId不存在：应fallback到任意设备")
        void testDeviceNotFoundFallback() {
            FishWashDevice fallback = createHanDevice();
            when(fishWashDeviceRepository.findById(9999)).thenReturn(Optional.empty());
            when(fishWashDeviceRepository.findAll()).thenReturn(List.of(fallback));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createMode(2, 280.0, 0.7, 0.017)));

            CrossEraComparisonResult result = service.compareAncientVsUltrasonic(9999);

            assertNotNull(result);
            assertNotNull(result.getAncientFishWash());
        }

        @Test
        @DisplayName("无振动模态数据：应使用默认频率280Hz")
        void testNoVibrationModes() {
            FishWashDevice device = createHanDevice();
            device.setBaselineResonanceFreq(null);
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(Collections.emptyList());

            CrossEraComparisonResult result = service.compareAncientVsUltrasonic(1);

            assertNotNull(result);
            assertEquals(280.0, result.getAncientFishWash().getFrequencyHz(),
                    "无模态数据时应使用默认280Hz");
        }

        @Test
        @DisplayName("无任何设备数据：应抛出异常")
        void testNoDevicesAtAll() {
            when(fishWashDeviceRepository.findById(anyInt())).thenReturn(Optional.empty());
            when(fishWashDeviceRepository.findAll()).thenReturn(Collections.emptyList());

            assertThrows(RuntimeException.class,
                    () -> service.compareAncientVsUltrasonic(1));
        }
    }
}
