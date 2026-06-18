package com.fishwash.shape_comparator;

import com.fishwash.config.FishWashProperties;
import com.fishwash.dto.ShapeComparisonRequest;
import com.fishwash.dto.ShapeComparisonResult;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class ShapeComparator {

    private final FishWashProperties fishWashProperties;

    public ShapeComparisonResult compare(ShapeComparisonRequest request) {
        int modeOrder = request.getModeOrder() != null ? request.getModeOrder() : 2;
        double E = request.getElasticModulus();
        double nu = request.getPoissonRatio();
        double rho = request.getMaterialDensity();
        double t = request.getThickness();
        double R = request.getCircleRadius();
        double a = request.getSquareSide();
        double h = request.getWaterDepth();

        FishWashProperties.AleProps ale = fishWashProperties.getAle();
        FishWashProperties.FluidProps fluid = fishWashProperties.getFluid();
        double rhoWater = fluid.getWaterDensity();

        double D = E * Math.pow(t, 3) / (12.0 * (1.0 - nu * nu));

        double lambdaN_circle = Math.pow(modeOrder, 2) * (Math.pow(modeOrder, 2) - 1);
        double fDry_circle = (lambdaN_circle / (2.0 * Math.PI)) *
                Math.sqrt(D / (rho * t * R * R));
        double coupling_circle = 1.0 / Math.sqrt(1.0 + rhoWater * R / (rho * t * modeOrder));
        double aleFactor_circle = 1.0 - ale.getTransitionRatio() * 0.5;
        double fWet_circle = fDry_circle * coupling_circle * aleFactor_circle;
        double damp_circle = 0.01 + 0.005 * modeOrder;
        double area_circle = Math.PI * R * R;
        double massPerArea_circle = rho * t;
        double modalDensity_circle = (area_circle * Math.sqrt(rho / D)) / 1.85;

        double m = modeOrder;
        double n = modeOrder;
        double lambdaSquare = Math.pow((m * Math.PI / a), 2) + Math.pow((n * Math.PI / a), 2);
        double lambdaMN_square = lambdaSquare * Math.sqrt(D / (rho * t));
        double fDry_square = lambdaMN_square / (2.0 * Math.PI);
        double perimeter = 4.0 * a;
        double area_square = a * a;
        double addedMassFactor_square = rhoWater * h * perimeter / (rho * t * area_square * modeOrder);
        double coupling_square = 1.0 / Math.sqrt(1.0 + Math.abs(addedMassFactor_square));
        double cornerStiffnessFactor = 1.0 + 0.05 * Math.log(modeOrder + 1);
        double fWet_square = fDry_square * coupling_square * aleFactor_circle * cornerStiffnessFactor;
        double damp_square = 0.012 + 0.0045 * modeOrder;
        double massPerArea_square = rho * t;
        double modalDensity_square = (area_square * Math.sqrt(rho / D)) / 2.10;

        ShapeComparisonResult.BasinResult circle = new ShapeComparisonResult.BasinResult();
        circle.setShapeName("圆形鱼洗 (Circular Basin)");
        circle.setCharacteristicLength(String.format(Locale.US, "R = %.4f m (D = %.1f cm)", R, R * 200));
        circle.setDryResonanceFreq(fDry_circle);
        circle.setWetResonanceFreq(fWet_circle);
        circle.setFluidCouplingFactor(coupling_circle * aleFactor_circle);
        circle.setDampingRatio(damp_circle);
        circle.setNodesCount(modeOrder * 2);
        circle.setBendingStiffness(D);
        circle.setMassPerUnitArea(massPerArea_circle);
        circle.setModalDensity(modalDensity_circle);

        ShapeComparisonResult.BasinResult square = new ShapeComparisonResult.BasinResult();
        square.setShapeName("方形鱼洗 (Square Basin)");
        square.setCharacteristicLength(String.format(Locale.US, "a = %.4f m (%.1f × %.1f cm)", a, a * 100, a * 100));
        square.setDryResonanceFreq(fDry_square);
        square.setWetResonanceFreq(fWet_square);
        square.setFluidCouplingFactor(coupling_square * aleFactor_circle * cornerStiffnessFactor);
        square.setDampingRatio(damp_square);
        square.setNodesCount(modeOrder * 2 + 4);
        square.setBendingStiffness(D);
        square.setMassPerUnitArea(massPerArea_square);
        square.setModalDensity(modalDensity_square);

        double freqRatio = fWet_square / fWet_circle;

        String shapeDescription = String.format(Locale.US,
                "当板厚t=%.2fmm、圆形R=%.1fcm等效方形a=%.1fcm、%d阶对称模态时：" +
                "圆形f=%.1fHz vs 方形f=%.1fHz，频率比f_sq/f_cir=%.3f。" +
                "方形由于角部刚度集中（角点位移约束）和边界条件不同，共振频率%s于圆形鱼洗；" +
                "方形节线为正交直线分布，圆形为节径放射形。",
                t * 1000, R * 100, a * 100, modeOrder,
                fWet_circle, fWet_square, freqRatio,
                freqRatio > 1.0 ? "普遍高" : "普遍低");

        String modeInterpretation = String.format(
                "物理意义：圆形n=%d阶对应%d条节径（穿过中心），呈现2n波腹/2n波谷交替；" +
                "方形(m,n)=(%d,%d)对应%d条水平节线和%d条垂直节线，形成%d×%d的方形分区振型。" +
                "相同面积下圆形因对称性更高、周长/面积比最小，振动能量耗散更低。",
                modeOrder, modeOrder,
                modeOrder, modeOrder, modeOrder, modeOrder, modeOrder, modeOrder);

        ShapeComparisonResult result = new ShapeComparisonResult();
        result.setCircleBasin(circle);
        result.setSquareBasin(square);
        result.setFrequencyRatio(String.format(Locale.US, "%.4f", freqRatio));
        result.setShapeEffectDescription(shapeDescription);
        result.setModePhysicalInterpretation(modeInterpretation);

        return result;
    }

    @Async("femComputationExecutor")
    public CompletableFuture<ShapeComparisonResult> compareAsync(ShapeComparisonRequest request) {
        return CompletableFuture.completedFuture(compare(request));
    }

    public static double calculateCircleWetFrequency(double E, double nu, double rho, double t, double R,
                                                      int modeOrder, double rhoWater, double aleTransitionRatio) {
        double D = E * Math.pow(t, 3) / (12.0 * (1.0 - nu * nu));
        double lambdaN = Math.pow(modeOrder, 2) * (Math.pow(modeOrder, 2) - 1);
        double fDry = (lambdaN / (2.0 * Math.PI)) * Math.sqrt(D / (rho * t * R * R));
        double coupling = 1.0 / Math.sqrt(1.0 + rhoWater * R / (rho * t * modeOrder));
        double aleFactor = 1.0 - aleTransitionRatio * 0.5;
        return fDry * coupling * aleFactor;
    }

    public static double calculateSquareWetFrequency(double E, double nu, double rho, double t, double a,
                                                      double h, int modeOrder, double rhoWater, double aleTransitionRatio) {
        double D = E * Math.pow(t, 3) / (12.0 * (1.0 - nu * nu));
        double lambdaSquare = Math.pow((modeOrder * Math.PI / a), 2) + Math.pow((modeOrder * Math.PI / a), 2);
        double lambdaMN = lambdaSquare * Math.sqrt(D / (rho * t));
        double fDry = lambdaMN / (2.0 * Math.PI);
        double perimeter = 4.0 * a;
        double area = a * a;
        double addedMassFactor = rhoWater * h * perimeter / (rho * t * area * modeOrder);
        double coupling = 1.0 / Math.sqrt(1.0 + Math.abs(addedMassFactor));
        double aleFactor = 1.0 - aleTransitionRatio * 0.5;
        double cornerStiffness = 1.0 + 0.05 * Math.log(modeOrder + 1);
        return fDry * coupling * aleFactor * cornerStiffness;
    }
}
