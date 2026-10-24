package com.example.demo.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.example.demo.advice.Level;

/** สรุปมื้ออาหารรายสัปดาห์ (7 วันสิ้นสุดที่ end) */
public record WeeklySummary(
        ChildOverview overview,
        LocalDate start,
        LocalDate end,
        List<DayRow> days,
        int totalMeals,
        int daysLogged,
        Double avgKcalPerLoggedDay,
        Double avgPercentOfTarget,
        Map<Level, Integer> levelCounts,
        List<GroupStatus> groups,
        int mealsWithoutEnergy) {

    public record DayRow(LocalDate date, int meals, double kcal, int mealsWithoutEnergy,
                         int red, int yellow, int green, int unknown) {
    }

    public record GroupStatus(String code, String label, boolean present) {
    }
}
