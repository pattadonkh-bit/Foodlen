package com.example.demo.advice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.example.demo.menu.MenuItem;
import com.example.demo.nutrition.StatusCode;

/**
 * ฐานกฎคำแนะนำ (resources/data/recommendation_rules.psv) ใช้ประเมินมื้ออาหารเป็นไฟจราจร
 *
 * <p>กฎทั้งหมดเป็นค่าเริ่มต้นสำหรับสาธิต ยังไม่ผ่านการตรวจสอบโดยผู้เชี่ยวชาญด้านโภชนาการ
 * การเลือกกฎ: กฎที่ตรงกับกลุ่มภาวะโภชนาการ + ชนิดมื้อ จะถูกใช้ โดยกฎสัดส่วนพลังงานแต่ละกลุ่มใช้กฎแรกที่ตรง
 * (เรียงจากเข้มสุดก่อน) และกฎ TAG แต่ละ tag ใช้กฎแรกที่ตรง ระดับสุดท้ายคือระดับที่รุนแรงที่สุดของกฎที่ตรง
 */
@Service
public class RecommendationService {

    public record Rule(String id, String statusGroup, String mealKind, String condition,
                       String value, Level level, String message) {
    }

    private final List<Rule> rules = new ArrayList<>();

    public RecommendationService() throws IOException {
        try (InputStream in = new ClassPathResource("data/recommendation_rules.psv").getInputStream();
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = r.readLine(); // header
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] p = line.split("\\|", -1);
                rules.add(new Rule(p[0].trim(), p[1].trim(), p[2].trim(), p[3].trim(), p[4].trim(),
                        Level.valueOf(p[5].trim()), p[6].trim()));
            }
        }
    }

    public List<Rule> rules() {
        return List.copyOf(rules);
    }

    public Advice evaluate(StatusCode status, MealType mealType, MenuItem menu, double portion, Integer dailyTarget) {
        Double energy = menu.energyKcal() == null ? null : menu.energyKcal() * portion;
        Double share = (energy != null && dailyTarget != null && dailyTarget > 0) ? energy / dailyTarget : null;
        String kind = mealType.isMain() ? "MAIN" : "SNACK";

        Level level = null;
        List<String> messages = new ArrayList<>();
        List<String> fired = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();

        for (Rule r : rules) {
            if (!(r.statusGroup().equals("ALL") || r.statusGroup().equals(status.getGroup()))) {
                continue;
            }
            if (!(r.mealKind().equals("ANY") || r.mealKind().equals(kind))) {
                continue;
            }
            boolean match;
            String key;
            switch (r.condition()) {
                case "SHARE_GT" -> {
                    match = share != null && share > Double.parseDouble(r.value());
                    key = r.statusGroup() + "|" + r.mealKind() + "|GT";
                }
                case "SHARE_LT" -> {
                    match = share != null && share < Double.parseDouble(r.value());
                    key = r.statusGroup() + "|" + r.mealKind() + "|LT";
                }
                case "TAG" -> {
                    match = menu.tags().contains(r.value());
                    key = "TAG:" + r.value();
                }
                default -> throw new IllegalStateException("ไม่รู้จักเงื่อนไขกฎ: " + r.condition());
            }
            if (!match || usedKeys.contains(key)) {
                continue;
            }
            usedKeys.add(key);
            level = level == null ? r.level() : Level.worst(level, r.level());
            messages.add(r.message());
            fired.add(r.id());
        }

        if (energy == null) {
            messages.add("เมนูนี้ยังไม่มีข้อมูลพลังงาน จึงประเมินตามสัดส่วนพลังงานไม่ได้");
        } else if (dailyTarget == null) {
            messages.add("อายุของเด็กอยู่นอกช่วง 6-12 ปี จึงไม่มีค่าพลังงานอ้างอิงต่อวันสำหรับประเมิน");
        }
        if (level == null) {
            if (share != null) {
                level = Level.GREEN;
                messages.add("มื้อนี้อยู่ในเกณฑ์ที่เหมาะสมตามกฎเบื้องต้น");
            } else {
                level = Level.UNKNOWN;
            }
        }
        return new Advice(level, energy, share, dailyTarget, messages, fired);
    }
}
