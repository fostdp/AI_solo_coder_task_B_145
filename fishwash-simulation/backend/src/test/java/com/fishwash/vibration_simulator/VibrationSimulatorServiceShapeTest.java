package com.fishwash.vibration_simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishwash.config.FishWashProperties;
import com.fishwash.dto.ShapeComparisonRequest;
import com.fishwash.dto.ShapeComparisonResult;
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
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("形状对比共振频率验证")
class VibrationSimulatorServiceShapeTest {

    @Mock private VibrationModeRepository vibrationModeRepository;
    @Mock private FishWashDeviceRepository fishWashDeviceRepository;
    @Mock private FishWashProperties fishWashProperties;
    @Mock private ObjectMapper objectMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private VibrationSimulatorService service;

    private FishWashProperties.AleProps aleProps;
    private FishWashProperties.FluidProps fluidProps;
    private FishWashProperties.MaterialProps materialProps;
    private FishWashProperties.MaterialProfile bronzeProfile;

    @BeforeEach
    void setUp() {
        aleProps = new FishWashProperties.AleProps();
        fluidProps = new FishWashProperties.FluidProps();
        materialProps = new FishWashProperties.MaterialProps();
        bronzeProfile = new FishWashProperties.MaterialProfile();
        bronzeProfile.setDensity(8500.0);
        bronzeProfile.setElasticModulus(1.0e11);
        bronzeProfile.setPoissonRatio(0.34);

        materialProps.setProfiles(Map.of("bronze-standard", bronzeProfile));

        lenient().when(fishWashProperties.getAle()).thenReturn(aleProps);
        lenient().when(fishWashProperties.getFluid()).thenReturn(fluidProps);
        lenient().when(fishWashProperties.getMaterial()).thenReturn(materialProps);
    }

    private FishWashDevice createCircleDevice() {
        FishWashDevice d = new FishWashDevice();
        d.setId(1);
        d.setDeviceCode("FW-TEST-CIRCLE");
        d.setDeviceName("测试圆形鱼洗");
        d.setBasinShape("CIRCLE");
        d.setMaterialParams("{\"thickness\":0.004}");
        d.setGeometryParams("{\"radius\":0.19,\"height\":0.14}");
        d.setStatus("ACTIVE");
        return d;
    }

    private FishWashDevice createSquareDevice() {
        FishWashDevice d = new FishWashDevice();
        d.setId(2);
        d.setDeviceCode("FW-TEST-SQUARE");
        d.setDeviceName("测试方形鱼洗");
        d.setBasinShape("SQUARE");
        d.setMaterialParams("{\"thickness\":0.004}");
        d.setGeometryParams("{\"sideLength\":0.36,\"height\":0.14}");
        d.setStatus("ACTIVE");
        return d;
    }

    private void mockDeviceGeo(FishWashDevice device) throws Exception {
        when(fishWashDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        Map<String, Object> geoMap = new HashMap<>();
        Map<String, Object> matMap = new HashMap<>();
        if ("SQUARE".equals(device.getBasinShape())) {
            geoMap.put("sideLength", 0.36);
        }
        geoMap.put("radius", 0.19);
        geoMap.put("height", 0.14);
        matMap.put("thickness", 0.004);
        when(objectMapper.readValue(eq(device.getGeometryParams()), eq(Map.class))).thenReturn(geoMap);
        when(objectMapper.readValue(eq(device.getMaterialParams()), eq(Map.class))).thenReturn(matMap);
    }

    @Nested
    @DisplayName("正常用例：圆形 vs 方形共振频率对比")
    class NormalShapeComparison {

        @Test
        @DisplayName("compareShapeVibration：方形频率应高于同材质圆形")
        void testSquareFreqHigherThanCircle() {
            ShapeComparisonRequest req = new ShapeComparisonRequest();
            req.setModeOrder(2);
            req.setMaterialDensity(8500.0);
            req.setElasticModulus(1.0e11);
            req.setPoissonRatio(0.34);
            req.setThickness(0.004);
            req.setCircleRadius(0.19);
            req.setSquareSide(0.36);
            req.setWaterDepth(0.098);

            ShapeComparisonResult result = service.compareShapeVibration(req);

            assertNotNull(result);
            assertNotNull(result.getCircleBasin());
            assertNotNull(result.getSquareBasin());
            assertNotNull(result.getCircleBasin().getWetResonanceFreq());
            assertNotNull(result.getSquareBasin().getWetResonanceFreq());

            double circleFreq = result.getCircleBasin().getWetResonanceFreq();
            double squareFreq = result.getSquareBasin().getWetResonanceFreq();
            assertTrue(circleFreq > 0, "圆形共振频率应大于0");
            assertTrue(squareFreq > 0, "方形共振频率应大于0");

            double ratio = Double.parseDouble(result.getFrequencyRatio());
            assertTrue(ratio > 1.0,
                    "方形频率比应>1.0（角部刚度集中效应），实际 ratio=" + ratio);
        }

        @Test
        @DisplayName("compareShapeVibration：频率比应随模态阶数变化")
        void testFrequencyRatioVariesWithMode() {
            ShapeComparisonRequest req2 = new ShapeComparisonRequest();
            req2.setModeOrder(2);
            req2.setMaterialDensity(8500.0);
            req2.setElasticModulus(1.0e11);
            req2.setPoissonRatio(0.34);
            req2.setThickness(0.004);
            req2.setCircleRadius(0.19);
            req2.setSquareSide(0.36);
            req2.setWaterDepth(0.098);

            ShapeComparisonRequest req4 = new ShapeComparisonRequest();
            req4.setModeOrder(4);
            req4.setMaterialDensity(8500.0);
            req4.setElasticModulus(1.0e11);
            req4.setPoissonRatio(0.34);
            req4.setThickness(0.004);
            req4.setCircleRadius(0.19);
            req4.setSquareSide(0.36);
            req4.setWaterDepth(0.098);

            ShapeComparisonResult result2 = service.compareShapeVibration(req2);
            ShapeComparisonResult result4 = service.compareShapeVibration(req4);

            double ratio2 = Double.parseDouble(result2.getFrequencyRatio());
            double ratio4 = Double.parseDouble(result4.getFrequencyRatio());

            assertNotEquals(ratio2, ratio4, 0.01,
                    "不同模态阶数的频率比应有差异");
            assertTrue(ratio2 > 1.0 && ratio4 > 1.0,
                    "各阶频率比均应>1.0");
        }

        @Test
        @DisplayName("compareShapeVibration：阻尼比随模态阶数递增")
        void testDampingRatioIncreasesWithMode() {
            ShapeComparisonRequest req2 = new ShapeComparisonRequest();
            req2.setModeOrder(2);
            req2.setMaterialDensity(8500.0);
            req2.setElasticModulus(1.0e11);
            req2.setPoissonRatio(0.34);
            req2.setThickness(0.004);
            req2.setCircleRadius(0.19);
            req2.setSquareSide(0.36);
            req2.setWaterDepth(0.098);

            ShapeComparisonRequest req5 = new ShapeComparisonRequest();
            req5.setModeOrder(5);
            req5.setMaterialDensity(8500.0);
            req5.setElasticModulus(1.0e11);
            req5.setPoissonRatio(0.34);
            req5.setThickness(0.004);
            req5.setCircleRadius(0.19);
            req5.setSquareSide(0.36);
            req5.setWaterDepth(0.098);

            ShapeComparisonResult r2 = service.compareShapeVibration(req2);
            ShapeComparisonResult r5 = service.compareShapeVibration(req5);

            assertTrue(r5.getCircleBasin().getDampingRatio() > r2.getCircleBasin().getDampingRatio(),
                    "高阶模态阻尼比应大于低阶");
            assertTrue(r5.getSquareBasin().getDampingRatio() > r2.getSquareBasin().getDampingRatio(),
                    "方形高阶阻尼比同样递增");
        }

        @Test
        @DisplayName("圆形设备：共振频率落在100-800Hz声学范围")
        void testCircleFreqInAcousticRange() throws Exception {
            FishWashDevice device = createCircleDevice();
            mockDeviceGeo(device);
            when(vibrationModeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            VibrationMode mode = service.calculateResonanceFrequency(1, 2);

            assertNotNull(mode);
            assertTrue(mode.getResonanceFreq() >= 100 && mode.getResonanceFreq() <= 800,
                    "圆形2阶共振频率应在100-800Hz范围，实际=" + mode.getResonanceFreq());
        }

        @Test
        @DisplayName("方形设备：共振频率应高于圆形")
        void testSquareFreqHigherThanCircleDevice() throws Exception {
            FishWashDevice circleDev = createCircleDevice();
            FishWashDevice squareDev = createSquareDevice();

            mockDeviceGeo(circleDev);
            when(vibrationModeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            VibrationMode circleMode = service.calculateResonanceFrequency(1, 2);

            mockDeviceGeo(squareDev);
            VibrationMode squareMode = service.calculateSquareBasinResonance(2, 2);

            assertTrue(squareMode.getResonanceFreq() > circleMode.getResonanceFreq(),
                    "方形2阶频率(" + squareMode.getResonanceFreq() + ")应高于圆形(" + circleMode.getResonanceFreq() + ")");
        }
    }

    @Nested
    @DisplayName("边界用例")
    class BoundaryShapeComparison {

        @Test
        @DisplayName("极高弹性模量：两种形状频率均应极高")
        void testVeryHighElasticModulus() {
            ShapeComparisonRequest req = new ShapeComparisonRequest();
            req.setModeOrder(2);
            req.setMaterialDensity(8500.0);
            req.setElasticModulus(1.0e14);
            req.setPoissonRatio(0.34);
            req.setThickness(0.004);
            req.setCircleRadius(0.19);
            req.setSquareSide(0.36);
            req.setWaterDepth(0.098);

            ShapeComparisonResult result = service.compareShapeVibration(req);

            assertTrue(result.getCircleBasin().getWetResonanceFreq() > 1000,
                    "极高弹性模量下圆形频率应超过1kHz");
            assertTrue(result.getSquareBasin().getWetResonanceFreq() > 1000,
                    "极高弹性模量下方形频率应超过1kHz");
        }

        @Test
        @DisplayName("极薄板厚t=0.001m：频率应较低")
        void testVeryThinPlate() {
            ShapeComparisonRequest req = new ShapeComparisonRequest();
            req.setModeOrder(2);
            req.setMaterialDensity(8500.0);
            req.setElasticModulus(1.0e11);
            req.setPoissonRatio(0.34);
            req.setThickness(0.001);
            req.setCircleRadius(0.19);
            req.setSquareSide(0.36);
            req.setWaterDepth(0.098);

            ShapeComparisonResult result = service.compareShapeVibration(req);

            assertTrue(result.getCircleBasin().getWetResonanceFreq() > 0,
                    "极薄板圆形频率应>0");
            assertTrue(result.getCircleBasin().getWetResonanceFreq() < 200,
                    "极薄板圆形频率应较低<200Hz，实际=" + result.getCircleBasin().getWetResonanceFreq());
        }

        @Test
        @DisplayName("高阶模态modeOrder=6：频率应显著高于2阶")
        void testHighModeOrder() {
            ShapeComparisonRequest reqLow = new ShapeComparisonRequest();
            reqLow.setModeOrder(2);
            reqLow.setMaterialDensity(8500.0);
            reqLow.setElasticModulus(1.0e11);
            reqLow.setPoissonRatio(0.34);
            reqLow.setThickness(0.004);
            reqLow.setCircleRadius(0.19);
            reqLow.setSquareSide(0.36);
            reqLow.setWaterDepth(0.098);

            ShapeComparisonRequest reqHigh = new ShapeComparisonRequest();
            reqHigh.setModeOrder(6);
            reqHigh.setMaterialDensity(8500.0);
            reqHigh.setElasticModulus(1.0e11);
            reqHigh.setPoissonRatio(0.34);
            reqHigh.setThickness(0.004);
            reqHigh.setCircleRadius(0.19);
            reqHigh.setSquareSide(0.36);
            reqHigh.setWaterDepth(0.098);

            ShapeComparisonResult rLow = service.compareShapeVibration(reqLow);
            ShapeComparisonResult rHigh = service.compareShapeVibration(reqHigh);

            assertTrue(rHigh.getCircleBasin().getWetResonanceFreq() >
                    rLow.getCircleBasin().getWetResonanceFreq() * 3,
                    "6阶频率应远高于2阶");
        }

        @Test
        @DisplayName("极大尺寸方形side=1.0m：频率应极低")
        void testLargeSquareSide() {
            ShapeComparisonRequest req = new ShapeComparisonRequest();
            req.setModeOrder(2);
            req.setMaterialDensity(8500.0);
            req.setElasticModulus(1.0e11);
            req.setPoissonRatio(0.34);
            req.setThickness(0.004);
            req.setCircleRadius(0.19);
            req.setSquareSide(1.0);
            req.setWaterDepth(0.098);

            ShapeComparisonResult result = service.compareShapeVibration(req);
            assertTrue(result.getSquareBasin().getWetResonanceFreq() < 100,
                    "极大方形频率应很低<100Hz，实际=" + result.getSquareBasin().getWetResonanceFreq());
        }
    }

    @Nested
    @DisplayName("异常用例")
    class AbnormalShapeComparison {

        @Test
        @DisplayName("设备不存在：calculateResonanceFrequency应抛异常")
        void testDeviceNotFound() {
            when(fishWashDeviceRepository.findById(9999)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.calculateResonanceFrequency(9999, 2));
            assertTrue(ex.getMessage().contains("Device not found"));
        }

        @Test
        @DisplayName("方形设备不存在：calculateSquareBasinResonance应抛异常")
        void testSquareDeviceNotFound() {
            when(fishWashDeviceRepository.findById(9999)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.calculateSquareBasinResonance(9999, 2));
            assertTrue(ex.getMessage().contains("Device not found"));
        }

        @Test
        @DisplayName("默认值请求：compareShapeVibration使用默认参数仍可计算")
        void testDefaultRequestValues() {
            ShapeComparisonRequest req = new ShapeComparisonRequest();
            ShapeComparisonResult result = service.compareShapeVibration(req);

            assertNotNull(result);
            assertNotNull(result.getCircleBasin());
            assertNotNull(result.getSquareBasin());
            assertTrue(result.getCircleBasin().getWetResonanceFreq() > 0);
            assertTrue(result.getSquareBasin().getWetResonanceFreq() > 0);
        }

        @Test
        @DisplayName("basinShape为null时默认按圆形处理")
        void testNullBasinShapeDefaultsToCircle() throws Exception {
            FishWashDevice device = createCircleDevice();
            device.setBasinShape(null);
            mockDeviceGeo(device);
            when(vibrationModeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            VibrationMode mode = service.calculateResonanceFrequency(1, 2);
            assertNotNull(mode);
            assertTrue(mode.getResonanceFreq() > 0);
        }

        @Test
        @DisplayName("geometryParams解析失败：应抛出RuntimeException")
        void testInvalidGeometryParams() throws Exception {
            FishWashDevice device = createCircleDevice();
            when(fishWashDeviceRepository.findById(1)).thenReturn(Optional.of(device));
            when(objectMapper.readValue(eq(device.getGeometryParams()), eq(Map.class)))
                    .thenThrow(new RuntimeException("JSON parse error"));

            assertThrows(RuntimeException.class,
                    () -> service.calculateResonanceFrequency(1, 2));
        }
    }
}
