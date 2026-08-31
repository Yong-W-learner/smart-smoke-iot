package com.example.demo.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ForestWeatherService {
    private static final long CACHE_MILLIS = 10 * 60 * 1000L;
    private volatile Map<String, Object> cached;
    private volatile long cacheTime;

    public synchronized Map<String, Object> current(double latitude, double longitude, List<Map<String, Object>> sensors) {
        if (cached != null && System.currentTimeMillis() - cacheTime < CACHE_MILLIS) return new LinkedHashMap<>(cached);
        try {
            String endpoint = "https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                    + "&longitude=" + longitude
                    + "&current=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m"
                    + "&timezone=Asia%2FShanghai&forecast_days=1";
            HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            connection.setRequestProperty("User-Agent", "forest-fire-course-design/1.0");
            if (connection.getResponseCode() != 200) throw new IllegalStateException("weather api " + connection.getResponseCode());
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            JSONObject current = JSON.parseObject(body.toString()).getJSONObject("current");
            Map<String, Object> result = new LinkedHashMap<>();
            double windSpeed = current.getDoubleValue("wind_speed_10m");
            double direction = current.getDoubleValue("wind_direction_10m");
            result.put("temperature", round1(current.getDoubleValue("temperature_2m")));
            result.put("humidity", round1(current.getDoubleValue("relative_humidity_2m")));
            result.put("precipitation", round1(current.getDoubleValue("precipitation")));
            result.put("windSpeed", round1(windSpeed));
            result.put("windDirection", round1(direction));
            result.put("windDirectionText", directionText(direction));
            result.put("windForce", windForce(windSpeed));
            result.put("windGust", round1(current.getDoubleValue("wind_gusts_10m")));
            result.put("weatherCode", current.getIntValue("weather_code"));
            result.put("condition", condition(current.getIntValue("weather_code")));
            result.put("observedAt", current.getString("time"));
            result.put("source", "Open-Meteo经纬度网格天气");
            result.put("sourceType", "forecast-grid");
            result.put("stale", false);
            cached = new LinkedHashMap<>(result);
            cacheTime = System.currentTimeMillis();
            return result;
        } catch (Exception ignored) {
            if (cached != null) {
                Map<String, Object> stale = new LinkedHashMap<>(cached);
                stale.put("stale", true);
                stale.put("source", cached.get("source") + "（最近成功值）");
                return stale;
            }
            return sensorFallback(sensors);
        }
    }

    private Map<String, Object> sensorFallback(List<Map<String, Object>> sensors) {
        double temperature = 0D, humidity = 0D;
        int temperatureCount = 0, humidityCount = 0;
        for (Map<String, Object> sensor : sensors) {
            if (!truthy(sensor.get("online"))) continue;
            if (sensor.get("temperature") instanceof Number) {
                temperature += ((Number) sensor.get("temperature")).doubleValue();
                temperatureCount++;
            }
            if (sensor.get("humidity") instanceof Number) {
                humidity += ((Number) sensor.get("humidity")).doubleValue();
                humidityCount++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("temperature", temperatureCount == 0 ? null : round1(temperature / temperatureCount));
        result.put("humidity", humidityCount == 0 ? null : round1(humidity / humidityCount));
        result.put("precipitation", null);
        result.put("windSpeed", null);
        result.put("windDirection", null);
        result.put("windDirectionText", "风向暂无");
        result.put("windForce", null);
        result.put("windGust", null);
        result.put("condition", "天气接口暂不可用");
        result.put("observedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.put("source", "景区监测节点降级数据");
        result.put("sourceType", "park-sensor-fallback");
        result.put("stale", true);
        return result;
    }

    private boolean truthy(Object value) {
        return value instanceof Boolean ? (Boolean) value : value instanceof Number && ((Number) value).intValue() == 1;
    }

    private String directionText(double degree) {
        String[] directions = {"北风", "东北偏北风", "东北风", "东北偏东风", "东风", "东南偏东风", "东南风", "东南偏南风", "南风", "西南偏南风", "西南风", "西南偏西风", "西风", "西北偏西风", "西北风", "西北偏北风"};
        return directions[(int) Math.floor((degree + 11.25D) / 22.5D) % 16];
    }

    private int windForce(double kmh) {
        double[] limits = {1D, 6D, 12D, 20D, 29D, 39D, 50D, 62D, 75D, 89D, 103D, 118D};
        for (int i = 0; i < limits.length; i++) if (kmh < limits[i]) return i;
        return 12;
    }

    private String condition(int code) {
        if (code == 0) return "晴";
        if (code <= 3) return "多云";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 67) return "降雨";
        if (code >= 80 && code <= 82) return "阵雨";
        if (code >= 95) return "雷雨";
        return "阴";
    }

    private double round1(double value) {
        return Math.round(value * 10D) / 10D;
    }
}
