package com.fishwash.shape_comparator;

import com.fishwash.shape_comparator.dto.ShapeComparisonRequest;
import com.fishwash.shape_comparator.dto.ShapeComparisonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ShapeComparator — 形状对比模块")
class ShapeComparatorTest {

    private ShapeComparator shapeComparator;

    @BeforeEach
    void setUp() {
        shapeComparator = new ShapeComparator();
    }

    private static final double RHO_WATER = 1000.0;
    private static final double ALE_RATIO = 0.3;

    @Nested
    @DisplayName("圆形鱼洗湿频计算")
    class CircleFrequency {

        @Test
        @DisplayName("默认参数2阶模态：频率在物理合理范围内")
        void defaultMode2() {
            double freq = ShapeComparator.calculateCircleWetFrequency(
                    1.02e11, 0.34, 8570.0, 0.0023, 0.180, 2, RHO_WATER, ALE_RATIO);
            assertTrue(freq > 0, "频率必须>0");
            assertTrue(freq < 10000, "频率<10kHz（薄板近似上限）");
        }

        @Test
        @DisplayName("高阶模态4阶频率 > 低阶2阶频率")
        void higherModeHigherFrequency() {
            double f2 = ShapeComparator.calculateCircleWetFrequency(
                    1.02e11, 0.34, 8570.0, 0.0023, 0.180, 2, RHO_WATER, ALE_RATIO);
            double f4 = ShapeComparator.calculateCircleWetFrequency(
                    1.02e11, 0.34, 8570.0, 0.0023, 0.180, 4, RHO_WATER, ALE_RATIO);
            assertTrue(f4 > f2 * 2, "4阶频率 > 2×2阶频率");
        }

        @Test
        @DisplayName("半径越大频率越低（大盆→低共振）")
        void largerRadiusLowerFreq() {
            double fSmall = ShapeComparator.calculateCircleWetFrequency(
                    1.02e11, 0.34, 8570.0, 0.0023, 0.10, 2, RHO_WATER, ALE_RATIO);
            double fBig = ShapeComparator.calculateCircleWetFrequency(
                    1.02e11, 0.34, 8570.0, 0.0023, 0.30, 2, RHO_WATER, ALE_RATIO);
            assertTrue(fBig < fSmall, "大R→低频");
        }

        @Test
        @DisplayName("板厚越小频率越低（薄板→易弯曲→低共振）")
        void thinnerPlateLowerFreq() {
            double fThick = ShapeComparator.calculateCircleWetFrequency(
                    1.02e11, 0.34, 8570.0, 0.005, 0.180, 2, RHO_WATER, ALE_RATIO);
            double fThin = ShapeComparator.calculateCircleWetFrequency(
                    1.02e11, 0.34, 8570.0, 0.001, 0.180, 2, RHO_WATER, ALE_RATIO);
            assertTrue(fThin < fThick, "薄板→低频");
        }
    }

    @Nested
    @DisplayName("方形鱼洗湿频计算")
    class SquareFrequency {

        @Test
        @DisplayName("方形频率 > 圆形频率（角部刚度集中）")
        void squareHigherThanCircle() {
            double fCircle = ShapeComparator.calculateCircleWetFrequency(
                    1.02e11, 0.34, 8570.0, 0.0023, 0.180, 2, RHO_WATER, ALE_RATIO);
            double fSquare = ShapeComparator.calculateSquareWetFrequency(
                    1.02e11, 0.34, 8570.0, 0.0023, 0.318, 0.08, 2, RHO_WATER, ALE_RATIO);
            assertTrue(fSquare > fCircle, "方形>圆形频率，实际方=" + fSquare + "，圆=" + fCircle);
        }

        @Test
        @DisplayName("高阶模态方形频率 > 低阶")
        void higherModeSquare() {
            double f2 = ShapeComparator.calculateSquareWetFrequency(
                    1.02e11, 0.34, 8570.0, 0.0023, 0.318, 0.08, 2, RHO_WATER, ALE_RATIO);
            double f4 = ShapeComparator.calculateSquareWetFrequency(
                    1.02e11, 0.34, 8570.0, 0.0023, 0.318, 0.08, 4, RHO_WATER, ALE_RATIO);
            assertTrue(f4 > f2 * 2, "4阶>2×2阶");
        }
    }

    @Nested
    @DisplayName("compare 综合对比")
    class Compare {

        @Test
        @DisplayName("默认请求返回完整对比结果")
        void defaultRequestReturnsCompleteResult() {
            ShapeComparisonRequest req = new ShapeComparisonRequest();
            req.setModeOrder(2);

            ShapeComparisonResult res = shapeComparator.compare(req);

            assertNotNull(res);
            assertNotNull(res.getCircleBasin());
            assertNotNull(res.getSquareBasin());
            assertTrue(res.getCircleBasin().getWetResonanceFreq() > 0);
            assertTrue(res.getSquareBasin().getWetResonanceFreq() > 0);
            assertTrue(res.getSquareBasin().getWetResonanceFreq() >
                    res.getCircleBasin().getWetResonanceFreq());
            assertNotNull(res.getShapeEffectDescription());
            assertNotNull(res.getModePhysicalInterpretation());
            assertNotNull(res.getEngineeringRecommendation());
            assertTrue(res.getCircleBasin().getDampingRatio() > 0);
            assertTrue(res.getCircleBasin().getFluidCouplingCoeff() > 0);
        }

        @Test
        @DisplayName("自定义参数请求正确使用")
        void customParameters() {
            ShapeComparisonRequest req = new ShapeComparisonRequest();
            req.setModeOrder(4);
            req.setCircleRadius(0.25);
            req.setSquareSide(0.45);
            req.setThickness(0.003);
            req.setElasticModulus(1.1e11);
            req.setWaterDepth(0.1);
            req.setMaterialDensity(8600.0);
            req.setPoissonRatio(0.35);

            ShapeComparisonResult res = shapeComparator.compare(req);

            assertNotNull(res);
            assertEquals(4, res.getModeOrder());
            assertTrue(res.getCircleBasin().getWetResonanceFreq() > 0);
        }

        @Test
        @DisplayName("modeOrder边界值1→自动修正为2")
        void modeOrderBoundaryMin() {
            ShapeComparisonRequest req = new ShapeComparisonRequest();
            req.setModeOrder(1);
            ShapeComparisonResult res = shapeComparator.compare(req);
            assertTrue(res.getModeOrder() >= 2, "modeOrder≥2");
        }

        @Test
        @DisplayName("modeOrder边界值10→自动修正为上限")
        void modeOrderBoundaryMax() {
            ShapeComparisonRequest req = new ShapeComparisonRequest();
            req.setModeOrder(10);
            ShapeComparisonResult res = shapeComparator.compare(req);
            assertTrue(res.getModeOrder() <= 10, "modeOrder≤10");
        }
    }
}
