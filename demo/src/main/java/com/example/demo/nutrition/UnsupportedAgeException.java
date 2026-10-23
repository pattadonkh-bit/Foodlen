package com.example.demo.nutrition;

public class UnsupportedAgeException extends IllegalArgumentException {

    public UnsupportedAgeException(int ageMonths) {
        super("อายุ " + ageMonths + " เดือน อยู่นอกช่วงที่ตาราง WHO รองรับ ("
                + NutritionStatusService.MIN_MONTH + "-" + NutritionStatusService.MAX_MONTH + " เดือน)");
    }
}
