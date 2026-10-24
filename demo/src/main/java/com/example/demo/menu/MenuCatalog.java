package com.example.demo.menu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** โหลดตารางเมนูและโภชนาการจาก resources/data/menu_nutrition.csv (แก้ไฟล์นี้เพื่อเพิ่ม/ตรวจสอบค่า) */
@Service
public class MenuCatalog {

    private final Map<String, MenuItem> items = new LinkedHashMap<>();

    public MenuCatalog() throws IOException {
        this("data/menu_nutrition.csv");
    }

    public MenuCatalog(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream();
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = r.readLine(); // header
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] p = line.split(",", -1);
                if (p.length < 13) {
                    throw new IllegalStateException("แถวในตารางเมนูมีคอลัมน์ไม่ครบ: " + line);
                }
                MenuItem m = new MenuItem(
                        p[0].trim(), p[1].trim(), p[2].trim(), p[3].trim(),
                        num(p[4]), num(p[5]), num(p[6]), num(p[7]), num(p[8]),
                        set(p[9]), list(p[10]), p[11].trim(), p[12].trim());
                items.put(m.code(), m);
            }
        }
    }

    private static Double num(String s) {
        return s == null || s.isBlank() ? null : Double.valueOf(s.trim());
    }

    private static Set<String> set(String s) {
        return s.isBlank() ? Set.of() : new LinkedHashSet<>(Arrays.asList(s.trim().split(";")));
    }

    private static List<String> list(String s) {
        return s.isBlank() ? List.of() : new ArrayList<>(Arrays.asList(s.trim().split(";")));
    }

    public List<MenuItem> all() {
        return List.copyOf(items.values());
    }

    public Optional<MenuItem> find(String code) {
        return Optional.ofNullable(items.get(code));
    }

    public MenuItem require(String code) {
        return find(code).orElseThrow(() -> new IllegalArgumentException("ไม่พบเมนู: " + code));
    }
}
