package com.fishwash.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "friction_analysis")
public class FrictionAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Integer deviceId;

    @Column(name = "normal_force_n")
    private Double normalForceN;

    @Column(name = "friction_coefficient")
    private Double frictionCoefficient;

    @Column(name = "friction_velocity_mps")
    private Double frictionVelocityMps;

    @Column(name = "tangential_force_n")
    private Double tangentialForceN;

    @Column(name = "excitation_power_w")
    private Double excitationPowerW;

    @Column(name = "cumulative_energy_j")
    private Double cumulativeEnergyJ;

    @Column(name = "stick_slip_frequency_hz")
    private Double stickSlipFrequencyHz;

    @Column(name = "excitation_efficiency")
    private Double excitationEfficiency;

    @Column(name = "resonance_coupling_factor")
    private Double resonanceCouplingFactor;

    @Column(name = "mechanical_params", columnDefinition = "jsonb")
    private String mechanicalParams;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;
}
