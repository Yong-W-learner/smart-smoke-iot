package com.example.demo.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.demo.entity.AlarmReview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 复核服务：调用本地 Python YOLO 识别服务，再结合烟雾浓度做融合判定。
 *
 * 融合思路（对应「YOLO + 传感器数据」方案）：
 *   视觉见明火                      -> fire   （最紧急，直接告警）
 *   浓度极高(>=90ppm)               -> smoke  （不管视觉结果，浓度说了算）
 *   视觉见烟雾 + 浓度超标           -> smoke
 *   视觉见水汽/雾 + 浓度不高(<70)   -> steam  （做饭水汽，降低误报）
 *   浓度超告警阈值(>=50)但视觉不定  -> smoke  （保守，按烟雾处理）
 *   其余                            -> normal
 */
@Service
public class AiReviewService {

    private static final Logger log = LoggerFactory.getLogger(AiReviewService.class);

    @Value("${yolo.service.url:http://127.0.0.1:8001/detect}")
    private String yoloUrl;

    private final RestTemplate restTemplate;

    public AiReviewService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public AiResult analyze(AlarmReview review) {
        // 1. 调用 YOLO 视觉识别
        List<Map<String, Object>> detections = new ArrayList<>();
        List<Map<String, Object>> boxes = new ArrayList<>();
        String visualCategory = "none";
        double topConf = 0;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = new HashMap<>();
            body.put("image_base64", review.getImageBase64());
            // 显式发 JSON 字符串，避免 RestTemplate 把 Map 序列化成 XML
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(body), headers);
            String resp = restTemplate.postForObject(yoloUrl, entity, String.class);

            JSONObject root = JSON.parseObject(resp);
            JSONArray dets = root.getJSONArray("detections");
            if (dets != null) {
                for (int i = 0; i < dets.size(); i++) {
                    JSONObject d = dets.getJSONObject(i);
                    String label = d.getString("label");       // 英文类别，如 fire-smoke
                    String name = d.getString("name");         // 中文名，如 火灾烟雾
                    String category = d.getString("category"); // smoke/steam/fire/ignore
                    double conf = d.getDoubleValue("conf");

                    // 前端“检测列表”用：中文名 + 英文类别
                    Map<String, Object> det = new HashMap<>();
                    det.put("label", name + " " + label);
                    det.put("conf", conf);
                    detections.add(det);

                    // 前端“检测框”用：百分比坐标 + 短标签
                    Map<String, Object> box = new HashMap<>();
                    box.put("x", d.getDoubleValue("x") * 100);
                    box.put("y", d.getDoubleValue("y") * 100);
                    box.put("w", d.getDoubleValue("w") * 100);
                    box.put("h", d.getDoubleValue("h") * 100);
                    box.put("label", shortLabel(category, label));
                    box.put("conf", conf);
                    boxes.add(box);

                    if ("ignore".equals(category)) {
                        continue; // 地面等无关类别不参与判定
                    }
                    if (conf > topConf) {
                        topConf = conf;
                        visualCategory = category;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("调用 YOLO 识别服务失败：{}", e.getMessage());
        }

        // 2. 融合判定
        double conc = review.getSmokeConcentration() == null ? 0 : review.getSmokeConcentration();
        String verdict;
        String basis;

        if ("fire".equals(visualCategory) && topConf >= 0.3) {
            verdict = "fire";
            basis = "YOLO 视觉检测到明火（" + pct(topConf) + "），请立即处置";
        } else if (conc >= 90) {
            verdict = "smoke";
            basis = "烟雾浓度极高（" + conc + "ppm ≥ 90），判定为火灾烟雾，建议立即处置";
        } else if ("smoke".equals(visualCategory) && topConf >= 0.4) {
            verdict = "smoke";
            basis = "YOLO 检测到烟雾（" + pct(topConf) + "）且浓度 " + conc + "ppm 超标，判定为疑似烟雾";
        } else if ("steam".equals(visualCategory) && topConf >= 0.4 && conc < 70) {
            verdict = "steam";
            basis = "YOLO 判定为水汽/雾（" + pct(topConf) + "），浓度 " + conc + "ppm 未达火灾阈值，疑似做饭水汽";
        } else if (conc >= 50) {
            verdict = "smoke";
            basis = "浓度 " + conc + "ppm 超过告警阈值，视觉未能确认，仍按疑似烟雾处理";
        } else {
            verdict = "normal";
            basis = "浓度 " + conc + "ppm 正常，视觉未见异常";
        }

        return new AiResult(verdict, basis, detections, boxes);
    }

    private String pct(double c) {
        return Math.round(c * 100) + "%";
    }

    private String shortLabel(String category, String label) {
        if ("fire".equals(category)) return "fire";
        if ("steam".equals(category)) return "steam";
        if ("smoke".equals(category)) return "smoke";
        return label;
    }

    /** 融合判定结果 */
    public static class AiResult {
        public final String verdict;
        public final String basis;
        public final List<Map<String, Object>> detections;
        public final List<Map<String, Object>> boxes;

        public AiResult(String verdict, String basis,
                        List<Map<String, Object>> detections,
                        List<Map<String, Object>> boxes) {
            this.verdict = verdict;
            this.basis = basis;
            this.detections = detections;
            this.boxes = boxes;
        }
    }
}
