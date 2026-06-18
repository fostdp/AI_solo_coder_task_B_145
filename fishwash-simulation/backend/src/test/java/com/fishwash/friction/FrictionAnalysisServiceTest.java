package com.fishwash.friction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishwash.config.FishWashProperties;
import com.fishwash.dto.FrictionAnalysisRequest;
import com.fishwash.entity.FishWashDevice;
import com.fishwash.entity.FrictionAnalysis;
import com.fishwash.entity.VibrationMode;
import com.fishwash.repository.FishWashDeviceRepository;
import com.fishwash.repository.FrictionAnalysisRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("双耳摩擦力分析：喷水阈值验证")
class FrictionAnalysisServiceTest {

    @Mock private FrictionAnalysisRepository frictionAnalysisRepository;
    @Mock private FishWashDeviceRepository fishWashDeviceRepository;
    @Mock private VibrationModeRepository vibrationModeRepository;
    @Mock private FishWashProperties fishWashProperties;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private FrictionAnalysisService service;

    private FishWashProperties.FrictionProps frictionProps;
    private FishWashProperties.MaterialProps materialProps;
    private FishWashProperties.MaterialProfile bronzeProfile;

    @BeforeEach
    void setUp() {
        frictionProps = new FishWashProperties.FrictionProps();
        materialProps = new FishWashProperties.MaterialProps();
        bronzeProfile = new FishWashProperties.MaterialProfile();
        bronzeProfile.setDensity(8500.0);

        materialProps.setProfiles(Map.of("bronze-standard", bronzeProfile));

        lenient().when(fishWashProperties.getFriction()).thenReturn(frictionProps);
        lenient().when(fishWashProperties.getMaterial()).thenReturn(materialProps);
    }

    private FishWashDevice createDevice() {
        FishWashDevice d = new FishWashDevice();
        d.setId(1);
        d.setDeviceCode("FW-HAN-001");
        d.setDeviceName("汉代双鱼纹鱼洗");
        d.setBasinShape("CIRCLE");
        d.setStatus("ACTIVE");
        return d;
    }

    private VibrationMode createVibrationMode(double freq) {
        VibrationMode m = new VibrationMode();
        m.setModeOrder(2);
        m.setResonanceFreq(freq);
        m.setFluidCouplingFactor(0.7);
        return m;
    }

    @Nested
    @DisplayName("正常用例：摩擦力驱动喷水阈值验证")
    class NormalFrictionAnalysis {

        @Test
        @DisplayName("切向力F_t = μ_eff × N，基本库仑摩擦定律")
        void testTangentialForceFollowsCoulombsLaw() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createVibrationMode(280.0)));
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysisRequest req = new FrictionAnalysisRequest();
            req.setNormalForceN(25.0);
            req.setFrictionCoefficient(0.35);

            FrictionAnalysis result = service.analyzeFrictionMechanics(1, req);

            assertNotNull(result);
            double Ft = result.getTangentialForceN();
            double muEff = result.getFrictionCoefficient();
            assertTrue(Ft > 0, "切向力应为正值");
            assertTrue(Ft <= 25.0 * 0.35 * 1.5,
                    "F_t不应远超 μ×N（水膜润滑修正后应减小）");
            assertTrue(muEff < 0.35,
                    "有效摩擦系数应小于原始μ（水膜润滑降低）");
        }

        @Test
        @DisplayName("激励功率应与摩擦速度成正比")
        void testExcitationPowerProportionalToVelocity() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createVibrationMode(280.0)));
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysisRequest reqLow = new FrictionAnalysisRequest();
            reqLow.setNormalForceN(20.0);
            reqLow.setFrictionCoefficient(0.35);
            reqLow.setFrictionVelocityMps(0.3);

            FrictionAnalysisRequest reqHigh = new FrictionAnalysisRequest();
            reqHigh.setNormalForceN(20.0);
            reqHigh.setFrictionCoefficient(0.35);
            reqHigh.setFrictionVelocityMps(1.5);

            FrictionAnalysis resultLow = service.analyzeFrictionMechanics(1, reqLow);
            FrictionAnalysis resultHigh = service.analyzeFrictionMechanics(1, reqHigh);

            assertTrue(resultHigh.getExcitationPowerW() > resultLow.getExcitationPowerW(),
                    "高速摩擦的激励功率应大于低速");
        }

        @Test
        @DisplayName("喷水阈值：粘滑频率接近共振频率时效率最高")
        void testSprayThresholdFrequencyMatch() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createVibrationMode(280.0)));
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysisRequest req = new FrictionAnalysisRequest();
            req.setNormalForceN(20.0);
            req.setFrictionCoefficient(0.35);
            req.setFrictionVelocityMps(1.0);

            FrictionAnalysis result = service.analyzeFrictionMechanics(1, req);

            double stickSlipFreq = result.getStickSlipFrequencyHz();
            assertTrue(stickSlipFreq >= 50 && stickSlipFreq <= 1200,
                    "粘滑频率应在50-1200Hz范围，实际=" + stickSlipFreq);

            double efficiency = result.getExcitationEfficiency();
            assertTrue(efficiency > 0 && efficiency < 1.0,
                    "传递效率应在0-1之间，实际=" + efficiency);
        }

        @Test
        @DisplayName("共振耦合因子应在0-1范围")
        void testResonanceCouplingFactorRange() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createVibrationMode(280.0)));
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysisRequest req = new FrictionAnalysisRequest();
            FrictionAnalysis result = service.analyzeFrictionMechanics(1, req);

            double coupling = result.getResonanceCouplingFactor();
            assertTrue(coupling >= 0 && coupling <= 1.0,
                    "共振耦合因子应在0-1，实际=" + coupling);
        }

        @Test
        @DisplayName("累计能量与持续时间成正比")
        void testCumulativeEnergyWithDuration() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createVibrationMode(280.0)));
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysisRequest req30 = new FrictionAnalysisRequest();
            req30.setDurationSeconds(30.0);

            FrictionAnalysisRequest req60 = new FrictionAnalysisRequest();
            req60.setDurationSeconds(60.0);

            FrictionAnalysis r30 = service.analyzeFrictionMechanics(1, req30);
            FrictionAnalysis r60 = service.analyzeFrictionMechanics(1, req60);

            assertTrue(r60.getCumulativeEnergyJ() > r30.getCumulativeEnergyJ(),
                    "60s累计能量应大于30s");
            assertTrue(r60.getCumulativeEnergyJ() / r30.getCumulativeEnergyJ() > 1.5,
                    "能量比应接近2倍");
        }

        @Test
        @DisplayName("法向力越大，切向力和功率越大")
        void testHigherNormalForceGivesHigherTangentialForce() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createVibrationMode(280.0)));
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysisRequest reqLight = new FrictionAnalysisRequest();
            reqLight.setNormalForceN(10.0);

            FrictionAnalysisRequest reqHeavy = new FrictionAnalysisRequest();
            reqHeavy.setNormalForceN(40.0);

            FrictionAnalysis rLight = service.analyzeFrictionMechanics(1, reqLight);
            FrictionAnalysis rHeavy = service.analyzeFrictionMechanics(1, reqHeavy);

            assertTrue(rHeavy.getTangentialForceN() > rLight.getTangentialForceN(),
                    "大法向力→更大切向力");
            assertTrue(rHeavy.getExcitationPowerW() > rLight.getExcitationPowerW(),
                    "大法向力→更大激励功率");
        }
    }

    @Nested
    @DisplayName("边界用例")
    class BoundaryFrictionAnalysis {

        @Test
        @DisplayName("极小法向力N=0.1N：仍可计算且切向力极小")
        void testVerySmallNormalForce() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createVibrationMode(280.0)));
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysisRequest req = new FrictionAnalysisRequest();
            req.setNormalForceN(0.1);
            req.setFrictionCoefficient(0.35);

            FrictionAnalysis result = service.analyzeFrictionMechanics(1, req);

            assertTrue(result.getTangentialForceN() > 0, "极小N下切向力应>0");
            assertTrue(result.getTangentialForceN() < 1.0, "极小N下切向力应很小");
        }

        @Test
        @DisplayName("极大法向力N=500N：切向力成比例增大")
        void testVeryLargeNormalForce() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createVibrationMode(280.0)));
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysisRequest req = new FrictionAnalysisRequest();
            req.setNormalForceN(500.0);
            req.setFrictionCoefficient(0.35);

            FrictionAnalysis result = service.analyzeFrictionMechanics(1, req);

            assertTrue(result.getTangentialForceN() > 100.0,
                    "大法向力下切向力应显著增大");
        }

        @Test
        @DisplayName("摩擦速度v=0.001m/s极慢：粘滑频率应接近下限")
        void testVeryLowFrictionVelocity() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createVibrationMode(280.0)));
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysisRequest req = new FrictionAnalysisRequest();
            req.setFrictionVelocityMps(0.001);

            FrictionAnalysis result = service.analyzeFrictionMechanics(1, req);

            assertTrue(result.getStickSlipFrequencyHz() >= 50,
                    "极慢速度粘滑频率应≥50Hz下限");
        }

        @Test
        @DisplayName("摩擦系数μ=0.01极低：有效摩擦系数不低于0.08下限")
        void testVeryLowFrictionCoefficient() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createVibrationMode(280.0)));
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysisRequest req = new FrictionAnalysisRequest();
            req.setFrictionCoefficient(0.01);
            req.setNormalForceN(20.0);

            FrictionAnalysis result = service.analyzeFrictionMechanics(1, req);

            assertTrue(result.getFrictionCoefficient() >= 0.08,
                    "有效摩擦系数应≥0.08下限");
        }

        @Test
        @DisplayName("阻尼比ζ=0.1（高阻尼）：传递效率应降低")
        void testHighDampingRatio() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createVibrationMode(280.0)));
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysisRequest reqLowDamp = new FrictionAnalysisRequest();
            reqLowDamp.setDampingRatio(0.005);

            FrictionAnalysisRequest reqHighDamp = new FrictionAnalysisRequest();
            reqHighDamp.setDampingRatio(0.1);

            FrictionAnalysis rLow = service.analyzeFrictionMechanics(1, reqLowDamp);
            FrictionAnalysis rHigh = service.analyzeFrictionMechanics(1, reqHighDamp);

            assertTrue(rHigh.getExcitationEfficiency() < rLow.getExcitationEfficiency(),
                    "高阻尼应降低传递效率");
        }
    }

    @Nested
    @DisplayName("异常用例")
    class AbnormalFrictionAnalysis {

        @Test
        @DisplayName("设备不存在：应抛出异常")
        void testDeviceNotFound() {
            when(fishWashDeviceRepository.findById(9999)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class,
                    () -> service.analyzeFrictionMechanics(9999, new FrictionAnalysisRequest()));
        }

        @Test
        @DisplayName("请求为null：应使用默认参数正常计算")
        void testNullRequest() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(List.of(createVibrationMode(280.0)));
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysis result = service.analyzeFrictionMechanics(1, null);

            assertNotNull(result);
            assertTrue(result.getTangentialForceN() > 0);
        }

        @Test
        @DisplayName("历史记录查询：空列表不影响")
        void testEmptyHistory() {
            when(frictionAnalysisRepository.findByDeviceIdOrderByAnalyzedAtDesc(1))
                    .thenReturn(Collections.emptyList());

            List<FrictionAnalysis> history = service.getAnalysisHistory(1);
            assertNotNull(history);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("无模态数据：使用默认280Hz仍可完成分析")
        void testNoVibrationModesData() {
            FishWashDevice device = createDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(Collections.emptyList());
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FrictionAnalysis result = service.analyzeFrictionMechanics(1, new FrictionAnalysisRequest());

            assertNotNull(result);
            assertTrue(result.getExcitationPowerW() > 0);
        }
    }
}
