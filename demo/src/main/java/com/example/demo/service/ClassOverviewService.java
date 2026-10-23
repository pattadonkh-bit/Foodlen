package com.example.demo.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.advice.Level;
import com.example.demo.domain.Meal;
import com.example.demo.domain.MealRepository;
import com.example.demo.nutrition.StatusCode;

/**
 * ภาพรวมเด็กทั้งหมดสำหรับครู/นักโภชนาการ
 *
 * <p>ระดับความเสี่ยง (เกณฑ์เบื้องต้นของผู้พัฒนา ยังไม่ผ่านการตรวจสอบโดยผู้เชี่ยวชาญ):
 * <ul>
 *   <li>เสี่ยงสูง: ผอมมาก หรืออ้วน หรือ มื้อไฟแดงตั้งแต่ 50% ของมื้อใน 7 วัน (เมื่อมีอย่างน้อย 3 มื้อ)</li>
 *   <li>เฝ้าระวัง: ผอม หรือน้ำหนักเกิน/ท้วม หรือ มื้อไฟแดงตั้งแต่ 30% ใน 7 วัน (เมื่อมีอย่างน้อย 3 มื้อ)</li>
 *   <li>ปกติ: สมส่วนและสัดส่วนไฟแดงต่ำ  · ประเมินไม่ได้: ไม่มีผลภาวะโภชนาการและไม่มีปัจจัยด้านอาหาร</li>
 * </ul>
 * การไม่บันทึกมื้อเกิน 3 วันแสดงเป็นป้ายแยกและกรองได้ แต่ไม่ทำให้ระดับความเสี่ยงสูงขึ้น
 */
@Service
@Transactional(readOnly = true)
public class ClassOverviewService {

    static final int MIN_MEALS_FOR_DIET_RISK = 3;
    static final int RED_HIGH_PCT = 50;
    static final int RED_MEDIUM_PCT = 30;

    private final ChildService children;
    private final MealRepository meals;

    public ClassOverviewService(ChildService children, MealRepository meals) {
        this.children = children;
        this.meals = meals;
    }

    public ClassOverview build() {
        LocalDate today = LocalDate.now();
        LocalDate from7 = today.minusDays(6);
        Map<Long, List<Meal>> byChild = new HashMap<>();
        for (Meal m : meals.findByEatenAtBetweenOrderByEatenAtAsc(
                today.minusDays(29).atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            byChild.computeIfAbsent(m.getChild().getId(), k -> new ArrayList<>()).add(m);
        }

        List<ClassRow> rows = new ArrayList<>();
        TreeSet<String> classrooms = new TreeSet<>();
        for (ChildOverview ov : children.listOverviews()) {
            if (ov.child().getClassroom() != null) {
                classrooms.add(ov.child().getClassroom());
            }
            List<Meal> ms = byChild.getOrDefault(ov.child().getId(), List.of());
            int meals7 = 0;
            int red7 = 0;
            LocalDate last = null;
            for (Meal m : ms) {
                LocalDate d = m.getEatenAt().toLocalDate();
                if (last == null || d.isAfter(last)) {
                    last = d;
                }
                if (!d.isBefore(from7)) {
                    meals7++;
                    if (m.getLevel() == Level.RED) {
                        red7++;
                    }
                }
            }
            Integer redPct = meals7 == 0 ? null : (int) Math.round(100.0 * red7 / meals7);
            Integer since = last == null ? null : (int) ChronoUnit.DAYS.between(last, today);

            List<String> reasons = new ArrayList<>();
            Risk risk = Risk.UNKNOWN;
            if (ov.status() != null) {
                StatusCode s = ov.status().status();
                risk = switch (s) {
                    case SEVERE_THINNESS, OBESITY -> Risk.HIGH;
                    case THINNESS, OVERWEIGHT -> Risk.MEDIUM;
                    case NORMAL -> Risk.LOW;
                };
                if (s != StatusCode.NORMAL) {
                    reasons.add(s.getLabel());
                }
            } else {
                reasons.add("ยังประเมินภาวะโภชนาการไม่ได้");
            }
            if (redPct != null && meals7 >= MIN_MEALS_FOR_DIET_RISK) {
                if (redPct >= RED_HIGH_PCT) {
                    risk = Risk.worst(risk, Risk.HIGH);
                    reasons.add("ไฟแดง " + redPct + "% ใน 7 วัน");
                } else if (redPct >= RED_MEDIUM_PCT) {
                    risk = Risk.worst(risk, Risk.MEDIUM);
                    reasons.add("ไฟแดง " + redPct + "% ใน 7 วัน");
                } else if (risk == Risk.UNKNOWN) {
                    risk = Risk.LOW;
                }
            }
            rows.add(new ClassRow(ov, meals7, red7, redPct, last, since, risk, reasons));
        }

        // เรียงจากเสี่ยงสูงไปต่ำ แล้วตามความผิดปกติของ z-score (ห่างจากค่ากลางมากก่อน) แล้วตามชื่อ
        rows.sort(Comparator.comparingInt((ClassRow r) -> r.risk().getRank()).reversed()
                .thenComparing(Comparator.comparingDouble((ClassRow r) -> Math.abs(r.getZ())).reversed())
                .thenComparing(r -> r.ov().child().getName()));

        int high = (int) rows.stream().filter(r -> r.risk() == Risk.HIGH).count();
        int medium = (int) rows.stream().filter(r -> r.risk() == Risk.MEDIUM).count();
        int notLogged = (int) rows.stream().filter(ClassRow::isNotLogged).count();
        return new ClassOverview(rows, rows.size(), high, medium, notLogged, List.copyOf(classrooms));
    }
}
