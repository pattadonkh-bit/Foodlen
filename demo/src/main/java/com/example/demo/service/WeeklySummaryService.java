package com.example.demo.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.advice.Level;
import com.example.demo.domain.Child;
import com.example.demo.domain.Meal;
import com.example.demo.domain.MealRepository;
import com.example.demo.menu.MenuCatalog;
import com.example.demo.menu.MenuItem;

@Service
@Transactional(readOnly = true)
public class WeeklySummaryService {

    /** กลุ่มอาหาร 5 หมู่ตามธงโภชนาการ (การจัดกลุ่มเมนูอยู่ใน menu_nutrition.csv และเป็นค่าเบื้องต้น) */
    public static final Map<String, String> FOOD_GROUPS = new LinkedHashMap<>();

    static {
        FOOD_GROUPS.put("starch", "ข้าว-แป้ง");
        FOOD_GROUPS.put("protein", "เนื้อสัตว์ ไข่ ถั่ว");
        FOOD_GROUPS.put("vegetable", "ผัก");
        FOOD_GROUPS.put("fruit", "ผลไม้");
        FOOD_GROUPS.put("milk", "นม");
    }

    private final ChildService children;
    private final MealRepository meals;
    private final MenuCatalog menus;

    public WeeklySummaryService(ChildService children, MealRepository meals, MenuCatalog menus) {
        this.children = children;
        this.meals = meals;
        this.menus = menus;
    }

    public WeeklySummary summarize(Long childId, LocalDate end) {
        ChildOverview ov = children.overview(childId);
        Child child = ov.child();
        LocalDate start = end.minusDays(6);
        List<Meal> list = meals.findByChildAndEatenAtBetweenOrderByEatenAtAsc(
                child, start.atStartOfDay(), end.plusDays(1).atStartOfDay().minusNanos(1));

        List<WeeklySummary.DayRow> days = new ArrayList<>();
        Map<Level, Integer> counts = new EnumMap<>(Level.class);
        for (Level l : Level.values()) {
            counts.put(l, 0);
        }
        Set<String> groupsSeen = new HashSet<>();
        int withoutEnergy = 0;
        double totalKcal = 0;
        int daysLogged = 0;

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            final LocalDate day = d;
            List<Meal> ofDay = list.stream().filter(m -> m.getEatenAt().toLocalDate().equals(day)).toList();
            double kcal = 0;
            int noEnergy = 0;
            int r = 0;
            int y = 0;
            int g = 0;
            int u = 0;
            for (Meal m : ofDay) {
                if (m.getEnergyKcal() == null) {
                    noEnergy++;
                } else {
                    kcal += m.getEnergyKcal();
                }
                switch (m.getLevel()) {
                    case RED -> r++;
                    case YELLOW -> y++;
                    case GREEN -> g++;
                    default -> u++;
                }
                counts.merge(m.getLevel(), 1, Integer::sum);
                menus.find(m.getMenuCode()).map(MenuItem::foodGroups).ifPresent(groupsSeen::addAll);
            }
            if (!ofDay.isEmpty()) {
                daysLogged++;
                totalKcal += kcal;
            }
            withoutEnergy += noEnergy;
            days.add(new WeeklySummary.DayRow(d, ofDay.size(), kcal, noEnergy, r, y, g, u));
        }

        Double avg = daysLogged == 0 ? null : totalKcal / daysLogged;
        Double pct = (avg != null && ov.dailyKcal() != null) ? avg / ov.dailyKcal() : null;
        List<WeeklySummary.GroupStatus> groups = FOOD_GROUPS.entrySet().stream()
                .map(e -> new WeeklySummary.GroupStatus(e.getKey(), e.getValue(), groupsSeen.contains(e.getKey())))
                .toList();
        return new WeeklySummary(ov, start, end, days, list.size(), daysLogged, avg, pct, counts, groups,
                withoutEnergy);
    }
}
