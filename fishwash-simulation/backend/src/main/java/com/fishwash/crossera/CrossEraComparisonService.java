package com.fishwash.crossera;

import com.fishwash.dto.CrossEraComparisonResult;
import com.fishwash.era_comparator.EraComparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrossEraComparisonService {

    private final EraComparator eraComparator;

    public CrossEraComparisonResult compareAncientVsUltrasonic(Integer ancientDeviceId) {
        return eraComparator.compareAncientVsModern(ancientDeviceId);
    }
}

