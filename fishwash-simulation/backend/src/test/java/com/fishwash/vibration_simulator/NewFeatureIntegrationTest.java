package com.fishwash.vibration_simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishwash.config.FishWashProperties;
import com.fishwash.dto.ShapeComparisonRequest;
import com.fishwash.dto.ShapeComparisonResult;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("新功能API端点集成测试")
class NewFeatureIntegrationTest {

    @Mock private VibrationModeRepository vibrationModeRepository;
    @Mock private FishWashDeviceRepository fishWashDeviceRepository;
    @Mock private FishWashProperties fishWashProperties;
    @Mock private ObjectMapper objectMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private FrictionAnalysisRepository frictionAnalysisRepository;

    @InjectMocks
    private VibrationSimulatorService vibrationService;

    private FishWashProperties.AleProps aleProps;
    private FishWashProperties.FluidProps fluidProps;
    private FishWashProperties.MaterialProps materialProps;
    private FishWashProperties.MaterialProfile bronzeProfile;
    private FishWashProperties.FrictionProps frictionProps;

    @BeforeEach
    void setUp() {
        aleProps = new FishWashProperties.AleProps();
        fluidProps = new FishWashProperties.FluidProps();
        materialProps = new FishWashProperties.MaterialProps();
        bronzeProfile = new FishWashProperties.MaterialProfile();
        bronzeProfile.setDensity(8500.0);
        bronzeProfile.setElasticModulus(1.0e11);
        bronzeProfile.setPoissonRatio(0.34);
        frictionProps = new FishWashProperties.FrictionProps();
        materialProps.setProfiles(Map.of("bronze-standard", bronzeProfile));

        lenient().when(fishWashProperties.getAle()).thenReturn(aleProps);
        lenient().when(fishWashProperties.getFluid()).thenReturn(fluidProps);
        lenient().when(fishWashProperties.getMaterial()).thenReturn(materialProps);
        lenient().when(fishWashProperties.getFriction()).thenReturn(frictionProps);
    }

    private FishWashDevice createCircleDevice() {
        FishWashDevice d = new FishWashDevice();
        d.setId(1);
        d.setDeviceCode("FW-HAN-001");
        d.setDeviceName("汉代双鱼纹鱼洗");
        d.setBasinShape("CIRCLE");
        d.setMaterialParams("{\"thickness\":0.004}");
        d.setGeometryParams("{\"radius\":0.19,\"height\":0.14}");
        d.setStatus("ACTIVE");
        return d;
    }

    private FishWashDevice createSquareDevice() {
        FishWashDevice d = new FishWashDevice();
        d.setId(3);
        d.setDeviceCode("FW-TANG-003");
        d.setDeviceName("唐代方洗");
        d.setBasinShape("SQUARE");
        d.setMaterialParams("{\"thickness\":0.004}");
        d.setGeometryParams("{\"sideLength\":0.32,\"height\":0.12}");
        d.setStatus("ACTIVE");
        return d;
    }

    private void mockDeviceGeo(FishWashDevice device) throws Exception {
        when(fishWashDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        Map<String, Object> geoMap = new HashMap<>();
        Map<String, Object> matMap = new HashMap<>();
        if ("SQUARE".equals(device.getBasinShape())) {
            geoMap.put("sideLength", 0.32);
        }
        geoMap.put("radius", 0.19);
        geoMap.put("height", 0.14);
        matMap.put("thickness", 0.004);
        when(objectMapper.readValue(eq(device.getGeometryParams()), eq(Map.class))).thenReturn(geoMap);
        when(objectMapper.readValue(eq(device.getMaterialParams()), eq(Map.class))).thenReturn(matMap);
    }

    @Nested
    @DisplayName("形状对比API端点集成")
    class ShapeComparisonIntegration {

        @Test
        @DisplayName("圆形设备 → calculateResonanceFrequency → 正确路由到圆形公式")
        void testCircleDeviceRoutesCorrectly() throws Exception {
            FishWashDevice device = createCircleDevice();
            mockDeviceGeo(device);
            when(vibrationModeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            VibrationMode mode = vibrationService.calculateResonanceFrequency(1, 2);

            assertNotNull(mode);
            assertTrue(mode.getResonanceFreq() > 0);
            assertTrue(mode.getResonanceFreq() < 1000,
                    "圆形2阶频率应在1kHz以下");
        }

        @Test
        @DisplayName("方形设备 → calculateResonanceFrequency → 自动路由到方形公式")
        void testSquareDeviceAutoRoutes() throws Exception {
            FishWashDevice device = createSquareDevice();
            mockDeviceGeo(device);
            when(vibrationModeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            VibrationMode mode = vibrationService.calculateResonanceFrequency(3, 2);

            assertNotNull(mode);
            assertTrue(mode.getResonanceFreq() > 0);
        }

        @Test
        @DisplayName("compareShapeVibration → 完整结果含描述和物理解读")
        void testCompleteShapeComparisonResult() {
            ShapeComparisonRequest req = new ShapeComparisonRequest();
            ShapeComparisonResult result = vibrationService.compareShapeVibration(req);

            assertNotNull(result.getCircleBasin());
            assertNotNull(result.getSquareBasin());
            assertNotNull(result.getFrequencyRatio());
            assertNotNull(result.getShapeEffectDescription());
            assertNotNull(result.getModePhysicalInterpretation());
            assertFalse(result.getShapeEffectDescription().isEmpty());
            assertFalse(result.getModePhysicalInterpretation().isEmpty());
        }
    }

    @Nested
    @DisplayName("摩擦力分析API端点集成")
    class FrictionAnalysisIntegration {

        @Test
        @DisplayName("完整分析流程：请求→计算→保存→返回")
        void testFullFrictionAnalysisFlow() {
            FishWashDevice device = createCircleDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(Collections.emptyList());
            when(frictionAnalysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            com.fishwash.friction.FrictionAnalysisService frictionService =
                    new com.fishwash.friction.FrictionAnalysisService(
                            frictionAnalysisRepository, fishWashDeviceRepository,
                            vibrationModeRepository, fishWashProperties, objectMapper);

            FrictionAnalysisRequest req = new FrictionAnalysisRequest();
            req.setNormalForceN(25.0);
            req.setFrictionCoefficient(0.35);
            req.setFrictionVelocityMps(0.8);

            FrictionAnalysis result = frictionService.analyzeFrictionMechanics(1, req);

            assertNotNull(result);
            assertTrue(result.getTangentialForceN() > 0);
            assertTrue(result.getExcitationPowerW() > 0);
            assertTrue(result.getCumulativeEnergyJ() > 0);
            assertTrue(result.getStickSlipFrequencyHz() > 0);
            assertNotNull(result.getMechanicalParams());
        }

        @Test
        @DisplayName("分页查询：返回Page对象")
        void testPagedHistoryQuery() {
            FrictionAnalysis a1 = new FrictionAnalysis();
            a1.setId(1L);
            a1.setDeviceId(1);
            a1.setTangentialForceN(8.5);

            when(frictionAnalysisRepository.findByDeviceIdOrderByAnalyzedAtDesc(anyInt(), any()))
                    .thenReturn(new PageImpl<>(List.of(a1)));

            com.fishwash.friction.FrictionAnalysisService frictionService =
                    new com.fishwash.friction.FrictionAnalysisService(
                            frictionAnalysisRepository, fishWashDeviceRepository,
                            vibrationModeRepository, fishWashProperties, objectMapper);

            Page<FrictionAnalysis> page = frictionService.getAnalysisPaged(1, 0, 10);
            assertNotNull(page);
            assertEquals(1, page.getTotalElements());
        }
    }

    @Nested
    @DisplayName("跨时代对比API端点集成")
    class CrossEraIntegration {

        @Test
        @DisplayName("完整跨时代对比流程：古代→现代→雷达→效率")
        void testFullCrossEraComparison() {
            FishWashDevice device = createCircleDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(vibrationModeRepository.findByDeviceIdOrderByModeOrderAsc(1))
                    .thenReturn(Collections.emptyList());

            FishWashProperties.UltrasonicProps usProps = new FishWashProperties.UltrasonicProps();
            lenient().when(fishWashProperties.getUltrasonic()).thenReturn(usProps);

            com.fishwash.crossera.CrossEraComparisonService crossEraService =
                    new com.fishwash.crossera.CrossEraComparisonService(
                            fishWashDeviceRepository, vibrationModeRepository, fishWashProperties);

            com.fishwash.dto.CrossEraComparisonResult result =
                    crossEraService.compareAncientVsUltrasonic(1);

            assertNotNull(result);
            assertNotNull(result.getAncientFishWash());
            assertNotNull(result.getModernUltrasonic());
            assertNotNull(result.getRadarComparison());
            assertEquals(6, result.getRadarComparison().size());
            assertNotNull(result.getEnergyEfficiency());
            assertNotNull(result.getVibrationParadigmDifference());
        }
    }
}
