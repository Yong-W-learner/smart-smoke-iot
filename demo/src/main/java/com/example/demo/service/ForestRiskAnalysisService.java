package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ForestRiskAnalysisService {
    public Map<String, Object> analyzeFireRisk(Map<String, Object> weather, List<Map<String, Object>> sensors) {
        Double weatherTemperature = number(weather.get("temperature"));
        Double weatherHumidity = number(weather.get("humidity"));
        Double localTemperature = average(sensors, "temperature");
        Double localHumidity = average(sensors, "humidity");
        double temperature = fuse(localTemperature, weatherTemperature);
        double humidity = fuse(localHumidity, weatherHumidity);
        double wind = value(weather.get("windSpeed"));
        double gust = value(weather.get("windGust"));
        double rain = value(weather.get("precipitation"));

        double dryScore = clamp((55D - humidity) / 30D * 30D, 0D, 30D);
        double heatScore = clamp((temperature - 25D) / 15D * 20D, 0D, 20D);
        double windScore = clamp(wind / 40D * 20D, 0D, 20D);
        double gustScore = clamp(gust / 60D * 10D, 0D, 10D);
        double rainReduction = clamp(rain / 5D * 25D, 0D, 25D);
        int score = (int) Math.round(clamp(10D + dryScore + heatScore + windScore + gustScore - rainReduction, 0D, 100D));

        String level = score < 25 ? "蓝色" : score < 45 ? "黄色" : score < 65 ? "橙色" : "红色";
        List<String> factors = new ArrayList<>();
        factors.add("融合温度" + round1(temperature) + "℃");
        factors.add("融合湿度" + round1(humidity) + "%RH");
        factors.add(weather.get("windSpeed") == null ? "风速数据缺失，未计入风力项" : "风速" + round1(wind) + "km/h、阵风" + round1(gust) + "km/h");
        factors.add(weather.get("precipitation") == null ? "降雨数据缺失，未计入降雨修正" : "当前降水" + round1(rain) + "mm");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("level", level);
        result.put("algorithm", "多源加权火险模型 v1.0");
        result.put("factors", factors);
        result.put("summary", "温湿度、风速阵风与降雨加权计算；现场节点和经纬度天气共同参与。当前得分" + score + "分。");
        return result;
    }

    public Map<String, Object> analyzeIncident(Map<String, Object> input, Map<String, Object> sensor, Map<String, Object> weather, int backgroundScore) {
        double smoke = prefer(input.get("smoke"), sensor.get("smoke"));
        double co = prefer(input.get("co"), sensor.get("co"));
        double temperature = prefer(input.get("temperature"), sensor.get("temperature"));
        double humidity = prefer(input.get("humidity"), sensor.get("humidity"));
        double visionConfidence = value(input.get("confidence"));

        double smokePart = clamp((smoke - 15D) / 70D * 30D, 0D, 30D);
        double coPart = clamp((co - 5D) / 30D * 20D, 0D, 20D);
        double heatPart = clamp((temperature - 28D) / 18D * 10D, 0D, 10D);
        double dryPart = clamp((45D - humidity) / 25D * 8D, 0D, 8D);
        double weatherPart = clamp(backgroundScore * 0.12D, 0D, 12D);
        double visionPart = clamp(visionConfidence * 0.2D, 0D, 20D);
        int score = (int) Math.round(clamp(5D + smokePart + coPart + heatPart + dryPart + weatherPart + visionPart, 0D, 100D));
        String level = score >= 75 ? "三级" : score >= 50 ? "二级" : "一级";

        List<String> factors = new ArrayList<>();
        factors.add("烟雾" + round1(smoke) + "ppm（" + Math.round(smokePart) + "分）");
        factors.add("CO " + round1(co) + "ppm（" + Math.round(coPart) + "分）");
        factors.add("温度" + round1(temperature) + "℃、湿度" + round1(humidity) + "%RH");
        factors.add("背景火险" + backgroundScore + "分");
        if (visionConfidence > 0D) factors.add("摄像头/热成像置信度" + Math.round(visionConfidence) + "%");
        String original = String.valueOf(input.get("reason") == null ? "" : input.get("reason"));
        String detail = String.join("；", factors) + (original.isEmpty() ? "" : "；复核线索：" + original);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("level", level);
        result.put("algorithm", "多源警情融合模型 v1.0");
        result.put("detail", detail);
        result.put("reason", "智能分析" + score + "分：" + detail);
        result.put("smoke", smoke);
        result.put("temperature", temperature);
        return result;
    }

    private Double average(List<Map<String, Object>> rows, String key) {
        double total = 0D;
        int count = 0;
        for (Map<String, Object> row : rows) {
            Object online = row.get("online");
            if (online instanceof Number && ((Number) online).intValue() == 0) continue;
            Double value = number(row.get(key));
            if (value != null) { total += value; count++; }
        }
        return count == 0 ? null : total / count;
    }

    private double fuse(Double local, Double weather) {
        if (local != null && weather != null) return local * 0.65D + weather * 0.35D;
        if (local != null) return local;
        return weather == null ? 0D : weather;
    }

    private double prefer(Object first, Object second) {
        Double a = number(first);
        Double b = number(second);
        return a != null ? a : b == null ? 0D : b;
    }

    private Double number(Object value) { return value instanceof Number ? ((Number) value).doubleValue() : null; }
    private double value(Object value) { Double number = number(value); return number == null ? 0D : number; }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private double round1(double value) { return Math.round(value * 10D) / 10D; }
}
