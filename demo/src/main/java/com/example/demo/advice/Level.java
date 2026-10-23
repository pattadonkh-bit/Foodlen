package com.example.demo.advice;

/** ระดับไฟจราจรของมื้ออาหาร (ความรุนแรง: RED > YELLOW > GREEN) */
public enum Level {
    RED("แดง", "red", 3),
    YELLOW("เหลือง", "yellow", 2),
    GREEN("เขียว", "green", 1),
    UNKNOWN("ประเมินไม่ได้", "grey", 0);

    private final String label;
    private final String cssClass;
    private final int severity;

    Level(String label, String cssClass, int severity) {
        this.label = label;
        this.cssClass = cssClass;
        this.severity = severity;
    }

    public String getLabel() {
        return label;
    }

    public String getCssClass() {
        return cssClass;
    }

    public static Level worst(Level a, Level b) {
        return a.severity >= b.severity ? a : b;
    }
}
