package com.example.demo.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasurementRepository extends JpaRepository<Measurement, Long> {

    Optional<Measurement> findFirstByChildOrderByMeasuredOnDescIdDesc(Child child);

    List<Measurement> findByChildOrderByMeasuredOnDescIdDesc(Child child);

    void deleteByChild(Child child);
}
