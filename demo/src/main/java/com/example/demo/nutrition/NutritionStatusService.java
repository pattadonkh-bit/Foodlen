package com.example.demo.nutrition;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * ประเมินภาวะโภชนาการด้วย BMI-for-age (WHO Growth Reference 2007, อายุ 61-228 เดือน)
 * z = ((BMI / M)^L - 1) / (L * S) ตาราง L, M, S อยู่ใน resources/who/*.csv
 * (แปลงจากไฟล์ expanded tables ของ WHO)
 */
@Service
public class NutritionStatusService {

    public static final int MIN_MONTH = 61;
    public static final int MAX_MONTH = 228;

    /** month -> {L, M, S} */
    private final Map<Sex, Map<Integer, double[]>> tables = new EnumMap<>(Sex.class);

    public NutritionStatusService() throws IOException {
        tables.put(Sex.M, load("who/bmi_boys_z.csv"));
        tables.put(Sex.F, load("who/bmi_girls_z.csv"));
    }

    private static Map<Integer, double[]> load(String path) throws IOException {
        Map<Integer, double[]> rows = new HashMap<>();
        try (InputStream in = new ClassPathResource(path).getInputStream();
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = r.readLine(); // header
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] p = line.split(",");
                rows.put(Integer.parseInt(p[0].trim()),
                        new double[] {Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])});
            }
        }
        return rows;
    }

    public static int ageInMonths(LocalDate birth, LocalDate on) {
        return (int) ChronoUnit.MONTHS.between(birth, on);
    }

    public static double bmi(double weightKg, double heightCm) {
        if (weightKg <= 0 || heightCm <= 0) {
            throw new IllegalArgumentException("น้ำหนักและส่วนสูงต้องมากกว่า 0");
        }
        double m = heightCm / 100.0;
        return weightKg / (m * m);
    }

    /** BMI ที่ตรงกับ z-score ที่กำหนด ณ อายุ (เดือน) หนึ่ง ใช้วาดเส้นเกณฑ์ในกราฟการเจริญเติบโต */
    public double bmiAtZ(Sex sex, int ageMonths, double z) {
        double[] lms = tables.get(sex).get(ageMonths);
        if (lms == null) {
            throw new UnsupportedAgeException(ageMonths);
        }
        double l = lms[0];
        double m = lms[1];
        double s = lms[2];
        return Math.abs(l) < 1e-9 ? m * Math.exp(s * z) : m * Math.pow(1 + l * s * z, 1 / l);
    }

    public StatusResult assess(Sex sex, LocalDate birth, LocalDate measuredOn, double weightKg, double heightCm) {
        return assess(sex, ageInMonths(birth, measuredOn), weightKg, heightCm);
    }

    public StatusResult assess(Sex sex, int ageMonths, double weightKg, double heightCm) {
        double[] lms = tables.get(sex).get(ageMonths);
        if (lms == null) {
            throw new UnsupportedAgeException(ageMonths);
        }
        double bmi = bmi(weightKg, heightCm);
        double l = lms[0];
        double m = lms[1];
        double s = lms[2];
        double z = Math.abs(l) < 1e-9 ? Math.log(bmi / m) / s : (Math.pow(bmi / m, l) - 1) / (l * s);
        return new StatusResult(ageMonths, bmi, z, StatusCode.fromZ(z));
    }
}
