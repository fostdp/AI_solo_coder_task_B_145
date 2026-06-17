package com.fishwash.repository;

import com.fishwash.entity.FrictionAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FrictionAnalysisRepository extends JpaRepository<FrictionAnalysis, Long> {

    List<FrictionAnalysis> findByDeviceIdOrderByAnalyzedAtDesc(Integer deviceId);

    Page<FrictionAnalysis> findByDeviceIdOrderByAnalyzedAtDesc(Integer deviceId, Pageable pageable);

    FrictionAnalysis findTopByDeviceIdOrderByAnalyzedAtDesc(Integer deviceId);
}
