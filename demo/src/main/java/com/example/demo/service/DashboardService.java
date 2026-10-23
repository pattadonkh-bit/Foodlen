package com.example.demo.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.advice.Level;
import com.example.demo.domain.Meal;
import com.example.demo.domain.MealRepository;

/** ข้อมูลการ์ดเด็กบนหน้าแรก: สรุปสั้นๆ ของวันนี้และ 7 วันล่าสุด ของเด็กที่ผู้ใช้มีสิทธิ์เห็น */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ChildService children;
    private final MealRepository meals;

    public DashboardService(ChildService children, MealRepository meals) {
        this.children = children;
        this.meals = meals;
    }

    public List<ChildCard> cards() {
        LocalDate today = LocalDate.now();
        Map<Long, List<Meal>> byChild = new HashMap<>();
        for (Meal m : meals.findByEatenAtBetweenOrderByEatenAtAsc(
                today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            byChild.computeIfAbsent(m.getChild().getId(), k -> new ArrayList<>()).add(m);
        }
        List<ChildCard> out = new ArrayList<>();
        for (ChildOverview ov : children.listOverviews()) {
            List<Meal> ms = byChild.getOrDefault(ov.child().getId(), List.of());
            List<Level> levels = new ArrayList<>();
            LocalDate last = null;
            for (Meal m : ms) {
                LocalDate d = m.getEatenAt().toLocalDate();
                if (d.equals(today)) {
                    levels.add(m.getLevel());
                }
                if (last == null || d.isAfter(last)) {
                    last = d;
                }
            }
            out.add(new ChildCard(ov, levels, ms.size(), last));
        }
        return out;
    }
}
