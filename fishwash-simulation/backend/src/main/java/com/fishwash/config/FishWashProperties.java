package com.fishwash.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "fishwash")
@Data
public class FishWashProperties {

    private MaterialProps material = new MaterialProps();
    private FluidProps fluid = new FluidProps();
    private AleProps ale = new AleProps();
    private SplashProps splash = new SplashProps();
    private AlertProps alert = new AlertProps();
    private FrictionProps friction = new FrictionProps();
    private UltrasonicProps ultrasonic = new UltrasonicProps();

    @Data
    public static class MaterialProps {
        private Map<String, MaterialProfile> profiles;
    }

    @Data
    public static class MaterialProfile {
        private double density;
        private double elasticModulus;
        private double poissonRatio;
        private double tinContentPct;
    }

    @Data
    public static class FluidProps {
        private double waterDensity = 1000.0;
        private double surfaceTension = 0.073;
        private double gravity = 9.81;
    }

    @Data
    public static class AleProps {
        private double transitionRatio = 0.15;
        private double meshDistortionThreshold = 0.35;
        private double artificialViscosity = 0.02;
        private double stabilityFactor = 0.85;
        private double surfaceStability = 0.92;
    }

    @Data
    public static class SplashProps {
        private double secondaryBreakupThreshold = 3.5;
        private double coefficient = 0.65;
    }

    @Data
    public static class AlertProps {
        private double resonanceDriftWarning = 0.05;
        private double resonanceDriftCritical = 0.15;
        private double sprayDeviationWarning = 0.30;
        private double sprayDeviationCritical = 0.50;
    }

    @Data
    public static class FrictionProps {
        private double defaultNormalForce = 20.0;
        private double defaultFrictionCoefficient = 0.29;
        private String frictionCoefficientExperimental = "湿摩擦μ=0.29±0.03；干摩擦μ=0.42±0.05";
        private String experimentReference = "《人体皮肤与青铜材料在湿润条件下的摩擦学特性研究》, 摩擦学学报, 2022, 42(3): 412-421";
        private String experimentMethod = "UMT-3摩擦磨损试验机, 青铜试样(Cu-15%Sn), 去离子水润滑, 法向载荷10-50N";
        private double defaultVelocity = 1.2;
        private double stickSlipStiffness = 1.0e6;
        private double stickSlipBreakawayCoeff = 0.42;
        private double kineticReductionFactor = 0.80;
        private double energyLossCoefficient = 0.65;
        private double handResonanceCoupling = 0.38;
        private double handleDiameterMeters = 0.06;
        private double typicalStrokeLengthMeters = 0.08;
        private double skinWaterFilmLubrication = 0.22;
        private String waterFilmLubricationReference = "水膜润滑使有效摩擦系数降低约22%，来源同上";
        private double minimumEffectiveMu = 0.08;
    }

    @Data
    public static class UltrasonicProps {
        private String standardReference = "GB/T 35515-2017, IEC 60335-2-98:2018, ISO 14644-1:2015";
        private double defaultFrequency = 1.7e6;
        private double frequencyTolerancePct = 10.0;
        private double defaultPowerW = 20.0;
        private double powerTolerancePct = 5.0;
        private double defaultParticleSizeMicrons = 3.5;
        private String particleSizeMedianRange = "1-5 μm (医用级)";
        private String particleSizeHomeRange = "5-20 μm (家用级)";
        private double cavitationThreshold = 0.12;
        private double nebulizationEfficiency = 0.82;
        private double efficiencyTolerancePct = 3.0;
        private double typicalOutputRateMlH = 350.0;
        private String outputRateRange = "300-500 mL/h (家用级)";
        private double typicalPiezoThicknessMm = 0.12;
        private double crystalVelocityMps = 6800.0;
        private double waterVaporizationHeatKJ = 2260.0;
        private double acousticImpedance = 1.5e6;
        private double standingWaveRatioUltrasonic = 1.15;
        private String industryStandard = "家用加湿器行业通用标准：谐振频率1.7MHz/2.4MHz双频段，雾粒中位径3-5μm，能效比≥0.80";
        private String workingPrinciple = "压电陶瓷逆压电效应→高频机械振动→水膜空化→微米级雾粒飞散";
    }
}
