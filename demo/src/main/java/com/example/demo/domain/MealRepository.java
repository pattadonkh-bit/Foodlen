package com.example.demo.domain;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MealRepository extends JpaRepository<Meal, Long> {

    List<Meal> findByChildAndEatenAtBetweenOrderByEatenAtAsc(Child child, LocalDateTime from, LocalDateTime to);

    List<Meal> findTop10ByChildOrderByEatenAtDescIdDesc(Child child);

    List<Meal> findTop200ByChildOrderByEatenAtDescIdDesc(Child child);

    List<Meal> findByEatenAtBetweenOrderByEatenAtAsc(LocalDateTime from, LocalDateTime to);

    void deleteByChild(Child child);
}
