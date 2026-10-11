package com.medifoodai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 天气查询工具（增强版）
 * <p>
 * 用于辅助节气饮食建议。根据用户所在地的实时天气+节气/季节提供食疗建议。
 * </p>
 *
 * <h3>定位策略（优先级从高到低）</h3>
 * <ol>
 *   <li>用户经纬度（lat/lng）→ 最精准，前端获取后传入</li>
 *   <li>城市名称（city）→ 次选</li>
 *   <li>默认北京 → 兜底</li>
 * </ol>
 *
 * <h3>天气数据多级降级</h3>
 * <ol>
 *   <li>和风天气 (QWeather) — 国产稳定</li>
 *   <li>wttr.in — 国外免费</li>
 *   <li>兜底数据 — 季节估算</li>
 * </ol>
 *
 * @author MediFood Team
 */
@Slf4j
@Component
public class WeatherQueryTool {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * ThirdParty 服务地址列表（按优先级尝试）
     * 本地 JAR 用 localhost:1195，Docker 部署用容器名 medi-food-thirdparty:1195
     */
    private static final String[] THIRD_PARTY_URLS = {
            "http://localhost:1195",
            "http://medi-food-thirdparty:1195"
    };

    /**
     * 24 节气日期（简化版，近似值）
     * 格式：MMDD -> 节气名称
     */
    private static final String[][] SOLAR_TERMS = {
            {"0105", "小寒"}, {"0120", "大寒"}, {"0204", "立春"}, {"0219", "雨水"},
            {"0306", "惊蛰"}, {"0321", "春分"}, {"0405", "清明"}, {"0420", "谷雨"},
            {"0506", "立夏"}, {"0521", "小满"}, {"0606", "芒种"}, {"0621", "夏至"},
            {"0707", "小暑"}, {"0723", "大暑"}, {"0807", "立秋"}, {"0823", "处暑"},
            {"0908", "白露"}, {"0923", "秋分"}, {"1008", "寒露"}, {"1023", "霜降"},
            {"1107", "立冬"}, {"1122", "小雪"}, {"1207", "大雪"}, {"1222", "冬至"}
    };

    @Tool(name = "weather_query", description = "查询用户所在地的实时天气+节气信息，用于辅助饮食推荐。参数：city(String, 可选，城市名), lat(Double, 可选，纬度), lng(Double, 可选，经度)。推荐使用经纬度获取更精准的天气。")
    public Map<String, Object> weatherQuery(String city, Double lat, Double lng) {
        String targetCity = (city == null || city.isEmpty()) ? "北京" : city;
        Map<String, Object> result = new HashMap<>();

        try {
            LocalDate today = LocalDate.now();
            int month = today.getMonthValue();
            int day = today.getDayOfMonth();

            // 计算当前节气
            String solarTerm = getSolarTerm(month, day);
            String season = getSeason(month);

            result.put("date", today.format(DateTimeFormatter.ISO_LOCAL_DATE));
            result.put("solarTerm", solarTerm);
            result.put("season", season);

            // 尝试调用 ThirdParty 服务获取实时天气
            Map<String, Object> realWeather = fetchRealWeather(targetCity, lat, lng);
            if (realWeather != null) {
                result.put("city", realWeather.getOrDefault("city", targetCity));
                result.put("weather", realWeather);
                result.put("source", realWeather.getOrDefault("source", "unknown"));
                // 根据实时天气+节气联合推荐饮食
                result.put("dietAdvice", getDietAdviceByWeather(solarTerm, season, realWeather));
            } else {
                result.put("city", targetCity);
                result.put("weather", getSeasonalWeather(season));
                result.put("source", "fallback");
                result.put("dietAdvice", getDietAdvice(solarTerm, season));
            }

            log.info("天气查询完成: city={}, lat={}, lng={}, source={}, term={}",
                    result.get("city"), lat, lng, result.get("source"), solarTerm);

        } catch (Exception e) {
            log.error("天气查询失败: {}", e.getMessage());
            result.put("error", "天气查询服务暂时不可用");
        }

        return result;
    }

    /**
     * 获取实时天气（多级降级）
     * <p>
     * 尝试多个 ThirdParty 服务地址，支持经纬度精确定位
     * </p>
     */
    private Map<String, Object> fetchRealWeather(String city, Double lat, Double lng) {
        // 构建请求参数
        StringBuilder urlBuilder = new StringBuilder();
        if (lat != null && lng != null) {
            urlBuilder.append("?lat=").append(lat).append("&lng=").append(lng);
        } else {
            urlBuilder.append("?city=").append(city);
        }
        String queryParams = urlBuilder.toString();

        // 依次尝试不同的服务地址
        for (String baseUrl : THIRD_PARTY_URLS) {
            try {
                String weatherUrl = baseUrl + "/api/weather/current" + queryParams;
                log.debug("尝试获取天气: {}", weatherUrl);

                ResponseEntity<Map> weatherResp = restTemplate.getForEntity(weatherUrl, Map.class);
                if (weatherResp.getBody() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> weather = (Map<String, Object>) weatherResp.getBody();
                    log.info("实时天气获取成功: source={}, city={}, temp={}",
                            weather.get("source"), weather.get("city"), weather.get("temperature"));
                    return weather;
                }
            } catch (Exception e) {
                log.debug("{} 不可用: {}", baseUrl, e.getMessage());
            }
        }

        log.warn("所有 ThirdParty 服务地址均不可用，使用节气估算");
        return null;
    }

    /**
     * 根据实时天气+节气联合推荐饮食
     * <p>
     * 结合当天实际天气状况（温度、湿度、天气状况）和节气，给出更精准的建议
     * </p>
     */
    private String getDietAdviceByWeather(String solarTerm, String season, Map<String, Object> weather) {
        String condition = String.valueOf(weather.getOrDefault("condition", ""));
        String tempStr = String.valueOf(weather.getOrDefault("temperature", ""));
        String humidityStr = String.valueOf(weather.getOrDefault("humidity", ""));

        // 基于节气的饮食方向
        String baseAdvice = getDietAdvice(solarTerm, season);

        // 根据天气状况微调
        StringBuilder weatherTip = new StringBuilder();
        weatherTip.append("【当前天气】").append(condition)
                .append("，温度").append(tempStr)
                .append("，湿度").append(humidityStr).append("。");

        if (condition.contains("雨")) {
            weatherTip.append("雨天湿气重，建议多吃健脾祛湿的食物，如薏米、赤小豆、冬瓜。");
        } else if (condition.contains("雪")) {
            weatherTip.append("雪天寒冷，建议多吃温补助阳的食物，如羊肉、生姜、桂圆。");
        } else if (condition.contains("晴") || condition.contains("热")) {
            weatherTip.append("天气晴热，建议多补水，吃清热解暑的食物，如绿豆、苦瓜、西瓜。");
        } else if (condition.contains("阴") || condition.contains("云")) {
            weatherTip.append("天气阴沉，宜行气解郁，可饮玫瑰花茶，吃些辛香食物。");
        } else if (condition.contains("风")) {
            weatherTip.append("风大天气，注意防风保暖，可喝姜茶驱寒。");
        }

        weatherTip.append("\n【节气养生】").append(baseAdvice);
        return weatherTip.toString();
    }

    /**
     * 根据日期获取当前节气
     */
    private String getSolarTerm(int month, int day) {
        String dateKey = String.format("%02d%02d", month, day);
        String currentTerm = "未知";

        for (int i = 0; i < SOLAR_TERMS.length; i++) {
            if (dateKey.compareTo(SOLAR_TERMS[i][0]) >= 0) {
                currentTerm = SOLAR_TERMS[i][1];
            } else {
                break;
            }
        }
        return currentTerm;
    }

    /**
     * 根据月份判断季节
     */
    private String getSeason(int month) {
        if (month >= 3 && month <= 5) return "春季";
        if (month >= 6 && month <= 8) return "夏季";
        if (month >= 9 && month <= 11) return "秋季";
        return "冬季";
    }

    /**
     * 根据季节返回典型天气描述
     */
    private Map<String, Object> getSeasonalWeather(String season) {
        Map<String, Object> weather = new HashMap<>();
        switch (season) {
            case "春季" -> {
                weather.put("temperature", "10-20°C");
                weather.put("condition", "温暖多风");
                weather.put("humidity", "适中");
            }
            case "夏季" -> {
                weather.put("temperature", "25-35°C");
                weather.put("condition", "炎热潮湿");
                weather.put("humidity", "较高");
            }
            case "秋季" -> {
                weather.put("temperature", "10-20°C");
                weather.put("condition", "凉爽干燥");
                weather.put("humidity", "较低");
            }
            case "冬季" -> {
                weather.put("temperature", "-5-5°C");
                weather.put("condition", "寒冷干燥");
                weather.put("humidity", "较低");
            }
        }
        return weather;
    }

    /**
     * 根据节气提供饮食建议
     */
    private String getDietAdvice(String solarTerm, String season) {
        return switch (solarTerm) {
            case "立春", "雨水", "惊蛰" -> "春季养肝，宜食辛甘发散之品，如韭菜、葱、蒜、豆芽等。推荐食谱：枸杞猪肝汤。";
            case "春分", "清明", "谷雨" -> "春分阴阳平衡，宜食清淡养肝之品，如菠菜、芹菜、山药。推荐食谱：薏仁山药粥。";
            case "立夏", "小满", "芒种" -> "夏季养心，宜食清热解暑之品，如绿豆、苦瓜、冬瓜。推荐食谱：绿豆百合汤。";
            case "夏至", "小暑", "大暑" -> "盛夏防暑，宜食健脾祛湿之品，如薏米、赤小豆、荷叶。推荐食谱：冬瓜薏米老鸭汤。";
            case "立秋", "处暑", "白露" -> "秋季养肺，宜食滋阴润燥之品，如梨、银耳、百合。推荐食谱：银耳莲子羹。";
            case "秋分", "寒露", "霜降" -> "秋分养肺润燥，宜食温润之品，如蜂蜜、芝麻、核桃。推荐食谱：杏仁粥。";
            case "立冬", "小雪", "大雪" -> "冬季养肾，宜食温补之品，如羊肉、牛肉、核桃。推荐食谱：当归生姜羊肉汤。";
            case "冬至", "小寒", "大寒" -> "寒冬进补，宜食温热滋补之品，如桂圆、红枣、枸杞。推荐食谱：当归黄芪炖鸡。";
            default -> "根据" + season + "气候特点，合理搭配饮食。";
        };
    }
}