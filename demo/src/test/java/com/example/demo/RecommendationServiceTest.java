package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.demo.advice.Advice;
import com.example.demo.advice.EnergyTargetService;
import com.example.demo.advice.Level;
import com.example.demo.advice.MealType;
import com.example.demo.advice.RecommendationService;
import com.example.demo.menu.MenuCatalog;
import com.example.demo.menu.MenuItem;
import com.example.demo.nutrition.Sex;
import com.example.demo.nutrition.StatusCode;

class RecommendationServiceTest {

    private final MenuCatalog menus;
    private final RecommendationService rules;
    private final EnergyTargetService targets;

    RecommendationServiceTest() throws Exception {
        menus = new MenuCatalog();
        rules = new RecommendationService();
        targets = new EnergyTargetService();
    }

    @Test
    void menuCatalogLoadsAllMenus() {
        assertEquals(101, menus.all().size());
        MenuItem kmg = menus.require("KhaoManKai");
        assertEquals("ข้าวมันไก่", kmg.nameTh());
        assertEquals(596.0, kmg.energyKcal());
        assertTrue(menus.require("KaiThot").tags().contains("fried"));
        assertEquals("estimated", menus.require("KhaoNaKai").status());
        assertEquals(495.0, menus.require("KhaoNaKai").energyKcal());
        assertTrue(menus.all().stream().allMatch(m -> m.energyKcal() != null), "ทุกเมนูควรมีพลังงาน");
    }

    @Test
    void energyTargetsByAgeAndSex() {
        assertEquals(Optional.of(1400), targets.dailyKcal(Sex.M, 7 * 12));
        assertEquals(Optional.of(1400), targets.dailyKcal(Sex.F, 8 * 12 + 11));
        assertEquals(Optional.of(1700), targets.dailyKcal(Sex.M, 10 * 12));
        assertEquals(Optional.of(1600), targets.dailyKcal(Sex.F, 12 * 12 + 11));
        assertEquals(Optional.empty(), targets.dailyKcal(Sex.M, 13 * 12));
    }

    @Test
    void overweightChildBigMealIsRed() {
        // ข้าวขาหมู 690 kcal x 1 จาน / 1400 = 49% ของพลังงานต่อวัน -> เกิน 40%
        Advice a = rules.evaluate(StatusCode.OBESITY, MealType.LUNCH, menus.require("KhaoKhaMu"), 1.0, 1400);
        assertEquals(Level.RED, a.level());
        assertEquals(690.0, a.energyKcal());
        assertEquals(690.0 / 1400, a.shareOfDaily(), 1e-9);
        assertTrue(a.firedRules().contains("R01"));
        assertFalse(a.firedRules().contains("R02"), "ใช้เฉพาะกฎที่เข้มที่สุดของกลุ่มเดียวกัน");
    }

    @Test
    void overweightChildFriedChickenGetsFriedWarning() {
        Advice a = rules.evaluate(StatusCode.OVERWEIGHT, MealType.SNACK, menus.require("KaiThot"), 1.0, 1400);
        assertEquals(Level.YELLOW, a.level());
        assertTrue(a.firedRules().contains("R04"));
        assertTrue(a.firedRules().contains("R05")); // 345/1400 = 24.6% > 15% สำหรับของว่าง
    }

    @Test
    void underweightChildSmallMealIsRed() {
        // ต้มยำกุ้ง 65 kcal / 1400 = 4.6% ต่ำกว่า 20%
        Advice a = rules.evaluate(StatusCode.THINNESS, MealType.LUNCH, menus.require("TomYam"), 1.0, 1400);
        assertEquals(Level.RED, a.level());
        assertTrue(a.firedRules().contains("R06"));
        assertFalse(a.firedRules().contains("R07"));
    }

    @Test
    void normalChildReasonableMealIsGreen() {
        // ผัดไทย 577 / 1700 = 33.9% อยู่ในช่วง 15-45%
        Advice a = rules.evaluate(StatusCode.NORMAL, MealType.LUNCH, menus.require("PhatThai"), 1.0, 1700);
        assertEquals(Level.GREEN, a.level());
        assertTrue(a.firedRules().isEmpty());
    }

    @Test
    void portionScalesEnergy() {
        Advice half = rules.evaluate(StatusCode.NORMAL, MealType.LUNCH, menus.require("PhatThai"), 0.5, 1700);
        assertEquals(288.5, half.energyKcal(), 1e-9);
    }

    @Test
    void unknownEnergyMenuIsUnknownUnlessTagRuleFires() {
        MenuItem noData = new MenuItem("X", "ทดสอบ", "Test", "1 จาน", null, null, null, null, null,
                java.util.Set.of(), java.util.List.of(), "-", "no_data");
        Advice somtam = rules.evaluate(StatusCode.NORMAL, MealType.LUNCH, noData, 1.0, 1400);
        assertEquals(Level.UNKNOWN, somtam.level());
        assertNull(somtam.energyKcal());
        assertNotNull(somtam.messages());

        // ข้าวเหนียวมะม่วงไม่มีค่าพลังงาน แต่มี tag sweet -> เตือนเรื่องน้ำตาลได้
        MenuItem sweetNoData = new MenuItem("Y", "หวานทดสอบ", "Sweet", "1 จาน", null, null, null, null, null,
                java.util.Set.of("sweet"), java.util.List.of(), "-", "no_data");
        Advice mango = rules.evaluate(StatusCode.OVERWEIGHT, MealType.SNACK, sweetNoData, 1.0, 1400);
        assertEquals(Level.YELLOW, mango.level());
        assertEquals(1L, mango.firedRules().stream().filter(id -> id.equals("R03") || id.equals("R10")).count(),
                "กฎ sweet ต้องไม่ซ้ำ");
    }

    @Test
    void noDailyTargetOutsideAgeRange() {
        Advice a = rules.evaluate(StatusCode.NORMAL, MealType.LUNCH, menus.require("PhatThai"), 1.0, null);
        assertEquals(Level.UNKNOWN, a.level());
        assertNull(a.shareOfDaily());
    }
}
