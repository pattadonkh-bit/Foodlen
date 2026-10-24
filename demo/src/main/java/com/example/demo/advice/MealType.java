package com.example.demo.advice;

public enum MealType {
    BREAKFAST("มื้อเช้า", true),
    LUNCH("มื้อกลางวัน", true),
    DINNER("มื้อเย็น", true),
    SNACK("ของว่าง/ขนม", false);

    private final String label;
    private final boolean main;

    MealType(String label, boolean main) {
        this.label = label;
        this.main = main;
    }

    public String getLabel() {
        return label;
    }

    public boolean isMain() {
        return main;
    }
}
