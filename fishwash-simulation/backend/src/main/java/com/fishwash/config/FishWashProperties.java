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
        private double defaultFrictionCoefficient = 0.35;
        private double defaultVelocity = 1.2;
        private double stickSlipStiffness = 1.0e6;
        private double stickSlipBreakawayCoeff = 0.45;
        private double kineticReductionFactor = 0.80;
        private double energyLossCoefficient = 0.65;
        private double handResonanceCoupling = 0.38;
        private double handleDiameterMeters = 0.06;
        private double typicalStrokeLengthMeters = 0.08;
        private double skinWaterFilmLubrication = 0.22;
    }

    @Data
    public static class UltrasonicProps {
        private double defaultFrequency = 1.7e6;
        private double defaultPowerW = 20.0;
        private double defaultParticleSizeMicrons = 3.0;
        private double cavitationThreshold = 0.12;
        private double nebulizationEfficiency = 0.72;
        private double typicalPiezoThicknessMm = 0.12;
        private double crystalVelocityMps = 6800.0;
        private double waterVaporizationHeatKJ = 2260.0;
        private double acousticImpedance = 1.5e6;
        private double standingWaveRatioUltrasonic = 1.15;
        private String workingPrinciple = "压电陶瓷逆压电效应→高频机械振动→水膜空化→微米级雾粒飞散";
    }
}
