package com.example.demo.advice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.example.demo.nutrition.Sex;

/** พลังงานอ้างอิงต่อวันของเด็กตามอายุและเพศ (resources/data/energy_targets.csv) */
@Service
public class EnergyTargetService {

    public record Target(String sex, int minYears, int maxYears, int kcalPerDay, String source) {
    }

    private final List<Target> targets = new ArrayList<>();

    public EnergyTargetService() throws IOException {
        try (InputStream in = new ClassPathResource("data/energy_targets.csv").getInputStream();
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = r.readLine(); // header
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] p = line.split(",", -1);
                targets.add(new Target(p[0].trim(), Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()), p[4].trim()));
            }
        }
    }

    public List<Target> targets() {
        return List.copyOf(targets);
    }

    public Optional<Integer> dailyKcal(Sex sex, int ageMonths) {
        int years = ageMonths / 12;
        return targets.stream()
                .filter(t -> (t.sex().equals("ALL") || t.sex().equals(sex.name()))
                        && years >= t.minYears() && years <= t.maxYears())
                .map(Target::kcalPerDay)
                .findFirst();
    }
}
