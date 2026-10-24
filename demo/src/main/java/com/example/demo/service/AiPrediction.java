package com.example.demo.service;

/** ผลทำนายหนึ่งอันดับจากบริการ AI (code ตรงกับรหัสเมนูใน menu_nutrition.csv) */
public record AiPrediction(String code, double confidence) {
}
