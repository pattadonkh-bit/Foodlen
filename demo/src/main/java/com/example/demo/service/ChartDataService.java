package com.example.demo.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.advice.Level;
import com.example.demo.domain.Child;
import com.example.demo.domain.Meal;
import com.example.demo.domain.MealRepository;
import com.example.demo.domain.MealSource;
import com.example.demo.domain.Measurement;
import com.example.demo.menu.MenuCatalog;
import com.example.demo.nutrition.NutritionStatusService;
import com.example.demo.nutrition.StatusCode;
import com.example.demo.nutrition.StatusResult;
import com.example.demo.nutrition.UnsupportedAgeException;
import com.example.demo.service.ChartData.GrowthCurve;
import com.example.demo.service.ChartData.GrowthPoint;
import com.example.demo.service.ChartData.Labeled;
import com.example.demo.service.ChartData.Stack;

/** เตรียมข้อมูลสำหรับกราฟในหน้าเด็ก หน้าสรุปรายสัปดาห์ และหน้าสถิติรวม */
@Service
@Transactional(readOnly = true)
public class ChartDataService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d/M");
    /** เส้นเกณฑ์ที่วาดในกราฟ: sd, key, ป้ายกำกับ */
    private static final Object[][] CURVES = {
            {-3.0, "m3", "−3 SD (ผอมมาก)"},
            {-2.0, "m2", "−2 SD (ผอม)"},
            {0.0, "median", "ค่ากลาง"},
            {1.0, "p1", "+1 SD (น้ำหนักเกิน)"},
            {2.0, "p2", "+2 SD (อ้วน)"},
            {3.0, "p3", "+3 SD"},
    };

    private final ChildService children;
    private final MealRepository meals;
    private final MenuCatalog menus;
    private final NutritionStatusService status;
    private final WeeklySummaryService weekly;

    public ChartDataService(ChildService children, MealRepository meals, MenuCatalog menus,
                            NutritionStatusService status, WeeklySummaryService weekly) {
        this.children = children;
        this.meals = meals;
        this.menus = menus;
        this.status = status;
        this.weekly = weekly;
    }

    public ChartData.Growth growth(Long childId) {
        Child child = children.get(childId);
        List<Measurement> ms = new ArrayList<>(children.history(child));
        ms.sort(Comparator.comparing(Measurement::getMeasuredOn).thenComparing(Measurement::getId));

        List<GrowthPoint> points = new ArrayList<>();
        for (Measurement m : ms) {
            try {
                StatusResult r = status.assess(child.getSex(), child.getBirthDate(), m.getMeasuredOn(),
                        m.getWeightKg(), m.getHeightCm());
                points.add(new GrowthPoint(r.ageMonths(), round(r.bmi(), 2), round(r.z(), 2),
                        m.getMeasuredOn().toString(), r.status().getLabel(), m.getWeightKg(), m.getHeightCm()));
            } catch (UnsupportedAgeException ignored) {
                // ข้ามการวัดที่อายุนอกตาราง WHO
            }
        }
        int nowMonths = NutritionStatusService.ageInMonths(child.getBirthDate(), LocalDate.now());
        int minAge = points.isEmpty() ? nowMonths : points.get(0).ageMonths();
        int maxAge = Math.max(points.isEmpty() ? nowMonths : points.get(points.size() - 1).ageMonths(), nowMonths);
        int lo = Math.max(NutritionStatusService.MIN_MONTH, minAge - 12);
        int hi = Math.min(NutritionStatusService.MAX_MONTH, Math.max(maxAge + 18, lo + 48));
        lo = Math.max(NutritionStatusService.MIN_MONTH, Math.min(lo, hi - 36));

        List<Integer> months = new ArrayList<>();
        for (int m = lo; m <= hi; m++) {
            months.add(m);
        }
        List<GrowthCurve> curves = new ArrayList<>();
        for (Object[] c : CURVES) {
            double sd = (double) c[0];
            List<Double> values = new ArrayList<>();
            for (int m : months) {
                values.add(round(status.bmiAtZ(child.getSex(), m, sd), 2));
            }
            curves.add(new GrowthCurve((String) c[1], (String) c[2], sd, values));
        }
        return new ChartData.Growth(child.getSex().name(), months, curves, points);
    }

    public ChartData.Weekly weekly(Long childId, LocalDate end) {
        WeeklySummary s = weekly.summarize(childId, end);
        List<String> labels = new ArrayList<>();
        List<Double> kcal = new ArrayList<>();
        List<Integer> r = new ArrayList<>();
        List<Integer> y = new ArrayList<>();
        List<Integer> g = new ArrayList<>();
        List<Integer> u = new ArrayList<>();
        for (WeeklySummary.DayRow d : s.days()) {
            labels.add(d.date().format(DAY));
            kcal.add(round(d.kcal(), 0));
            r.add(d.red());
            y.add(d.yellow());
            g.add(d.green());
            u.add(d.unknown());
        }
        return new ChartData.Weekly(labels, kcal, s.overview().dailyKcal(), r, y, g, u,
                s.levelCounts().get(Level.RED), s.levelCounts().get(Level.YELLOW),
                s.levelCounts().get(Level.GREEN), s.levelCounts().get(Level.UNKNOWN));
    }

    public ChartData.Stats stats() {
        List<ChildOverview> kids = children.listOverviews();
        LocalDate today = LocalDate.now();
        // นับเฉพาะมื้อของเด็กที่ผู้ใช้ปัจจุบันมีสิทธิ์เห็น (ผู้ปกครองเห็นสถิติของลูกตัวเอง ครูเห็นทั้งหมด)
        java.util.Set<Long> visible = new java.util.HashSet<>();
        for (ChildOverview k : kids) {
            visible.add(k.child().getId());
        }
        List<Meal> all = meals.findByEatenAtBetweenOrderByEatenAtAsc(
                today.minusYears(5).atStartOfDay(), today.plusDays(1).atStartOfDay()).stream()
                .filter(m -> visible.contains(m.getChild().getId()))
                .toList();

        // สัดส่วนภาวะโภชนาการ
        Map<StatusCode, Integer> sc = new EnumMap<>(StatusCode.class);
        for (StatusCode c : StatusCode.values()) {
            sc.put(c, 0);
        }
        int unassessed = 0;
        for (ChildOverview k : kids) {
            if (k.status() == null) {
                unassessed++;
            } else {
                sc.merge(k.status().status(), 1, Integer::sum);
            }
        }
        List<Labeled> statusDist = new ArrayList<>();
        for (StatusCode c : StatusCode.values()) {
            statusDist.add(new Labeled(c.name(), c.getLabel(), sc.get(c)));
        }
        if (unassessed > 0) {
            statusDist.add(new Labeled("NONE", "ประเมินไม่ได้", unassessed));
        }

        // ไฟจราจร แหล่งที่มาของเมนู เมนูยอดนิยม
        Map<Level, Integer> lc = new EnumMap<>(Level.class);
        for (Level l : Level.values()) {
            lc.put(l, 0);
        }
        Map<MealSource, Integer> src = new EnumMap<>(MealSource.class);
        for (MealSource s : MealSource.values()) {
            src.put(s, 0);
        }
        Map<String, Integer> menuCount = new LinkedHashMap<>();
        Map<StatusCode, int[]> byStatus = new EnumMap<>(StatusCode.class);
        int last7 = 0;
        int red7 = 0;
        LocalDate from7 = today.minusDays(6);
        for (Meal m : all) {
            lc.merge(m.getLevel(), 1, Integer::sum);
            src.merge(m.getSource(), 1, Integer::sum);
            menuCount.merge(m.getMenuCode(), 1, Integer::sum);
            if (m.getStatusAtMeal() != null) {
                byStatus.computeIfAbsent(m.getStatusAtMeal(), k -> new int[4])[idx(m.getLevel())]++;
            }
            if (!m.getEatenAt().toLocalDate().isBefore(from7)) {
                last7++;
                if (m.getLevel() == Level.RED) {
                    red7++;
                }
            }
        }
        List<Labeled> levelDist = new ArrayList<>();
        for (Level l : Level.values()) {
            levelDist.add(new Labeled(l.name(), l.getLabel(), lc.get(l)));
        }
        List<Labeled> sourceDist = new ArrayList<>();
        for (MealSource s : MealSource.values()) {
            sourceDist.add(new Labeled(s.name(), s.getLabel(), src.get(s)));
        }
        List<Labeled> top = menuCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(e -> new Labeled(e.getKey(),
                        menus.find(e.getKey()).map(mi -> mi.nameTh()).orElse(e.getKey()), e.getValue()))
                .toList();

        List<Stack> byStatusRows = new ArrayList<>();
        for (StatusCode c : StatusCode.values()) {
            int[] a = byStatus.get(c);
            if (a != null) {
                byStatusRows.add(new Stack(c.getLabel(), a[0], a[1], a[2], a[3]));
            }
        }

        // 14 วันล่าสุด
        List<Stack> days = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            int[] a = new int[4];
            for (Meal m : all) {
                if (m.getEatenAt().toLocalDate().equals(d)) {
                    a[idx(m.getLevel())]++;
                }
            }
            days.add(new Stack(d.format(DAY), a[0], a[1], a[2], a[3]));
        }

        int aiBased = src.get(MealSource.AI_AUTO) + src.get(MealSource.AI_CONFIRMED);
        int autoPct = aiBased == 0 ? 0 : (int) Math.round(100.0 * src.get(MealSource.AI_AUTO) / aiBased);
        Integer redPct = last7 == 0 ? null : (int) Math.round(100.0 * red7 / last7);
        return new ChartData.Stats(kids.size(), all.size(), last7, red7, redPct, autoPct,
                statusDist, levelDist, sourceDist, top, byStatusRows, days);
    }

    private static int idx(Level l) {
        return switch (l) {
            case RED -> 0;
            case YELLOW -> 1;
            case GREEN -> 2;
            default -> 3;
        };
    }

    private static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
