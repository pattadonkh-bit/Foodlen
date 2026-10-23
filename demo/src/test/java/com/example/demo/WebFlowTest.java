package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * ทดสอบการเรนเดอร์หน้าเว็บทุกหน้าและขั้นตอนหลักตั้งแต่ลงทะเบียนเด็กจนถึงสรุปรายสัปดาห์
 * (ไม่ใช้บริการ AI: บันทึกมื้อด้วยการเลือกเมนูเอง)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebFlowTest {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, Map<String, String> form) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (var e : form.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(sb.toString())).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String text(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
    }

    @Test
    void fullFlowRendersEveryPage() throws Exception {
        assertEquals(200, get("/").statusCode());
        assertEquals(200, get("/children/new").statusCode());
        HttpResponse<String> rules = get("/rules");
        assertEquals(200, rules.statusCode());
        assertTrue(rules.body().contains("R01"));

        LocalDate birth = LocalDate.now().minusYears(9).minusMonths(2);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("name", "ทดสอบ");
        form.put("sex", "F");
        form.put("birthDate", birth.toString());
        form.put("measuredOn", LocalDate.now().toString());
        form.put("weightKg", "38");
        form.put("heightCm", "130");
        form.put("consent", "true");
        HttpResponse<String> created = post("/children", form);
        assertEquals(200, created.statusCode());
        String uri = created.uri().getPath();
        Matcher m = Pattern.compile("/children/(\\d+)").matcher(uri);
        assertTrue(m.find(), "ควร redirect ไปหน้าเด็ก: " + uri);
        String id = m.group(1);
        assertTrue(text(created.body()).contains("ภาวะโภชนาการ"));

        // ต้องยินยอมก่อน
        form.remove("consent");
        assertTrue(post("/children", form).body().contains("ต้องได้รับความยินยอม"));

        assertEquals(200, get("/children/" + id + "/meals/new").statusCode());
        assertEquals(200, get("/children/" + id + "/meals/manual").statusCode());

        // บันทึกมื้อ: เมนูมีพลังงาน / ไม่มีพลังงาน
        HttpResponse<String> kmg = post("/children/" + id + "/meals", Map.of(
                "menuCode", "KhaoManKai", "portion", "1", "mealType", "LUNCH",
                "eatenDate", LocalDate.now().toString()));
        assertEquals(200, kmg.statusCode());
        assertTrue(text(kmg.body()).contains("596"));
        assertTrue(kmg.body().contains("bigLight"));

        HttpResponse<String> somtam = post("/children/" + id + "/meals", Map.of(
                "menuCode", "KhaoNaKai", "portion", "1", "mealType", "SNACK",
                "eatenDate", LocalDate.now().toString()));
        assertEquals(200, somtam.statusCode());
        // ข้าวหน้าไก่เป็นเมนู estimated: ต้องมีคำเตือนค่าประมาณ และโปรตีน/ไขมัน/คาร์บต้องไม่เป็น "ไม่มีข้อมูล"
        assertTrue(text(somtam.body()).contains("ประมาณ"));
        assertFalse(text(somtam.body()).contains("ไม่มีข้อมูล"));
        assertTrue(text(somtam.body()).contains("คำนวณ"));

        HttpResponse<String> detail = get("/children/" + id);
        assertEquals(200, detail.statusCode());
        assertTrue(text(detail.body()).contains("ข้าวมันไก่"));

        HttpResponse<String> weekly = get("/children/" + id + "/weekly");
        assertEquals(200, weekly.statusCode());
        assertTrue(text(weekly.body()).contains("2 มื้อที่บันทึก"), text(weekly.body()));

        // หน้าสถิติรวมและ API ข้อมูลกราฟ
        assertEquals(200, get("/stats").statusCode());
        HttpResponse<String> stats = get("/api/stats");
        assertEquals(200, stats.statusCode());
        assertTrue(stats.body().contains("\"statusDistribution\""), stats.body());
        assertTrue(stats.body().contains("ข้าวมันไก่"), "เมนูที่บันทึกควรอยู่ใน topMenus");
        HttpResponse<String> growth = get("/api/children/" + id + "/growth");
        assertEquals(200, growth.statusCode());
        assertTrue(growth.body().contains("\"points\"") && growth.body().contains("\"median\""), growth.body());
        HttpResponse<String> wk = get("/api/children/" + id + "/weekly");
        assertEquals(200, wk.statusCode());
        assertTrue(wk.body().contains("\"kcal\""), wk.body());

        assertEquals(404, get("/children/999999").statusCode());

        HttpResponse<String> deleted = post("/children/" + id + "/delete", Map.of());
        assertEquals(200, deleted.statusCode());
        assertEquals(404, get("/children/" + id).statusCode());
    }

    @Test
    void analyzeShowsFriendlyErrorWhenAiServiceIsDown() throws Exception {
        // สร้างเด็กก่อน แล้วเรียก analyze โดยไม่มีบริการ AI (พอร์ต 59999 ในค่า test)
        LocalDate birth = LocalDate.now().minusYears(8);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("name", "ทดสอบ AI");
        form.put("sex", "M");
        form.put("birthDate", birth.toString());
        form.put("measuredOn", LocalDate.now().toString());
        form.put("weightKg", "26");
        form.put("heightCm", "128");
        form.put("consent", "true");
        String id = Pattern.compile("/children/(\\d+)").matcher(post("/children", form).uri().getPath())
                .results().findFirst().orElseThrow().group(1);

        String boundary = "----foodlen";
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"a.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n";
        String tail = "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"mealType\"\r\n\r\nLUNCH\r\n--"
                + boundary + "--\r\n";
        byte[] body = new byte[head.length() + png.length + tail.length()];
        System.arraycopy(head.getBytes(StandardCharsets.ISO_8859_1), 0, body, 0, head.length());
        System.arraycopy(png, 0, body, head.length(), png.length);
        System.arraycopy(tail.getBytes(StandardCharsets.ISO_8859_1), 0, body, head.length() + png.length,
                tail.length());
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/children/" + id + "/meals/analyze"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, r.statusCode());
        assertTrue(text(r.body()).contains("บริการ AI"), "ควรแจ้งว่าเชื่อมต่อบริการ AI ไม่ได้");
        assertTrue(text(r.body()).contains("เลือกเมนูเอง"));
    }
}
