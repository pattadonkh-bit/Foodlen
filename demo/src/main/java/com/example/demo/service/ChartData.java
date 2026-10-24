package com.example.demo.service;

import java.util.List;

/** โครงสร้างข้อมูลสำหรับกราฟ (ส่งเป็น JSON ผ่าน /api/...) */
public final class ChartData {

    private ChartData() {
    }

    // ----- กราฟการเจริญเติบโต (BMI-for-age) -----
    public record GrowthPoint(int ageMonths, double bmi, double z, String date, String status,
                              double weightKg, double heightCm) {
    }

    public record GrowthCurve(String key, String label, double sd, List<Double> values) {
    }

    public record Growth(String sex, List<Integer> months, List<GrowthCurve> curves, List<GrowthPoint> points) {
    }

    // ----- กราฟรายสัปดาห์ -----
    public record Weekly(List<String> labels, List<Double> kcal, Integer target,
                         List<Integer> red, List<Integer> yellow, List<Integer> green, List<Integer> unknown,
                         int redTotal, int yellowTotal, int greenTotal, int unknownTotal) {
    }

    // ----- สถิติรวมทุกคน -----
    public record Labeled(String code, String label, int value) {
    }

    public record Stack(String label, int red, int yellow, int green, int unknown) {
    }

    public record Stats(int children, int meals, int mealsLast7, int redLast7, Integer redPercentLast7,
                        int aiAutoPercent,
                        List<Labeled> statusDistribution, List<Labeled> levelDistribution,
                        List<Labeled> sourceDistribution, List<Labeled> topMenus,
                        List<Stack> levelByStatus, List<Stack> days) {
    }
}
