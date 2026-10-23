package com.example.demo.nutrition;

public enum Sex {
    M("ชาย"),
    F("หญิง");

    private final String label;

    Sex(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
