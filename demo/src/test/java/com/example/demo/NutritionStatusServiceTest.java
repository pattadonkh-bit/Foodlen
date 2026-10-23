package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.example.demo.nutrition.NutritionStatusService;
import com.example.demo.nutrition.Sex;
import com.example.demo.nutrition.StatusCode;
import com.example.demo.nutrition.StatusResult;
import com.example.demo.nutrition.UnsupportedAgeException;

/** ค่าอ้างอิงมาจากสคริปต์ Python ในโครงงานทดลอง (food-nutrition-project/src/nutrition_status.py) */
class NutritionStatusServiceTest {

    private final NutritionStatusService service;

    NutritionStatusServiceTest() throws Exception {
        this.service = new NutritionStatusService();
    }

    @Test
    void matchesPythonReferenceCases() {
        check(Sex.M, 100, 40, 130, 3.10015, StatusCode.OBESITY);
        check(Sex.F, 120, 22, 135, -3.3212, StatusCode.SEVERE_THINNESS);
        check(Sex.M, 127, 28, 130, -0.08488, StatusCode.NORMAL);
        check(Sex.F, 72, 18, 115, -1.20125, StatusCode.NORMAL);
        check(Sex.M, 155, 60, 160, 1.73593, StatusCode.OVERWEIGHT);
    }

    private void check(Sex sex, int months, double w, double h, double z, StatusCode code) {
        StatusResult r = service.assess(sex, months, w, h);
        assertEquals(z, r.z(), 0.001);
        assertEquals(code, r.status());
    }

    @Test
    void ageInMonthsCountsFullMonths() {
        assertEquals(127, NutritionStatusService.ageInMonths(LocalDate.of(2016, 3, 1), LocalDate.of(2026, 10, 22)));
        assertEquals(126, NutritionStatusService.ageInMonths(LocalDate.of(2016, 3, 23), LocalDate.of(2026, 10, 22)));
    }

    @Test
    void rejectsAgeOutsideWhoTable() {
        assertThrows(UnsupportedAgeException.class, () -> service.assess(Sex.M, 60, 18, 105));
        assertThrows(UnsupportedAgeException.class, () -> service.assess(Sex.F, 229, 50, 160));
    }

    @Test
    void rejectsNonPositiveMeasurements() {
        assertThrows(IllegalArgumentException.class, () -> service.assess(Sex.M, 100, 0, 130));
        assertThrows(IllegalArgumentException.class, () -> service.assess(Sex.M, 100, 30, -1));
    }
}
