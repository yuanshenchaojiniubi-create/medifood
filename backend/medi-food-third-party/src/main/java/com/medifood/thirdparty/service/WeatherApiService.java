package com.medifood.thirdparty.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * 天气 API 服务（国产化方案）
 * <p>
 * 使用国内稳定的天气服务替代 ip-api.com + wttr.in：
 * <ul>
 *   <li>天气数据：<b>和风天气 (QWeather)</b> — 免费版 1000次/天，国内访问稳定</li>
 *   <li>逆地理编码：<b>高德地图 (Amap)</b> — 免费版 5000次/天，经纬度转城市名</li>
 *   <li>IP定位：<b>高德地图 IP 定位</b> — 无需用户授权，兜底方案</li>
 * </ul>
 * </p>
 *
 * <h3>API Key 申请（免费）</h3>
 * <ol>
 *   <li>和风天气：<a href="https://dev.qweather.com/">https://dev.qweather.com/</a> → 控制台 → 创建应用 → 获取 API Key</li>
 *   <li>高德地图：<a href="https://lbs.amap.com/">https://lbs.amap.com/</a> → 控制台 → 应用管理 → 创建应用 → 获取 Key</li>
 * </ol>
 *
 * <h3>配置方式</h3>
 * 在 application.properties 中添加：
 * <pre>
 * weather.qweather.key=你的和风天气key
 * weather.amap.key=你的高德地图key
 * </pre>
 * 如果未配置 API Key，自动降级为 ip-api.com + wttr.in（旧方案）。
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Service
public class WeatherApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 和风天气 API Key（在 application.properties 中配置） */
    @Value("${weather.qweather.key:}")
    private String qweatherKey;

    /** 和风天气 API Host（在控制台-设置中查看，每个项目专属） */
    @Value("${weather.qweather.host:}")
    private String qweatherHost;

    /** 高德地图 API Key（可在 application.properties 中配置） */
    @Value("${weather.amap.key:}")
    private String amapKey;

    public WeatherApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ==================== 公共接口 ====================

    /**
     * 天气查询结果
     */
    public static class WeatherResult {
        public String city;
        public String country;
        public String temperature;
        public String feelsLike;
        public String condition;
        public String conditionEn;
        public String humidity;
        public boolean success;
        public String source; // "qweather" | "wttr" | "fallback"
    }

    /**
     * 获取实时天气（统一入口）
     *
     * @param city 城市名（可选）
     * @param lat  纬度（可选）
     * @param lng  经度（可选）
     * @param ip   客户端IP（可选）
     * @return 天气信息
     */
    public WeatherResult getWeather(String city, Double lat, Double lng, String ip) {
        log.info("🌤 [WeatherApiService] getWeather 入参: city={}, lat={}, lng={}, ip={}", city, lat, lng, ip);

        // 1. 解析目标城市
        String rawCity = resolveCity(city, lat, lng, ip);
        // 去掉"市/区/县/自治州/地区"等后缀，和风天气和 wttr.in 用简短城市名更准确
        String targetCity = trimCitySuffix(rawCity);
        log.info("🌤 [WeatherApiService] 解析目标城市: {}（清理后: {}）", rawCity, targetCity);

        // 2. 优先使用和风天气（需要同时配置 key 和 host）
        if (isNotEmpty(qweatherKey) && isNotEmpty(qweatherHost)) {
            log.info("🌤 [WeatherApiService] 和风天气 Key 和 Host 已配置，尝试和风天气... host={}", qweatherHost);
            // 优先用经纬度直接查天气（最准确），没有经纬度才用城市名搜索
            WeatherResult result = fetchFromQWeather(targetCity, lat, lng);
            if (result != null) {
                log.info("🌤 [WeatherApiService] ✅ 和风天气成功");
                return result;
            }
            log.warn("🌤 [WeatherApiService] 和风天气获取失败，降级为 wttr.in");
        } else {
            if (isEmpty(qweatherKey)) {
                log.warn("🌤 [WeatherApiService] 和风天气 Key 未配置，跳过");
            }
            if (isEmpty(qweatherHost)) {
                log.warn("🌤 [WeatherApiService] 和风天气 Host 未配置（请在控制台-设置中查看你的 API Host），跳过");
            }
            log.warn("🌤 [WeatherApiService] 降级为 wttr.in");
        }

        // 3. 降级为 wttr.in（旧方案）
        log.info("🌤 [WeatherApiService] 尝试 wttr.in...");
        WeatherResult result = fetchFromWttr(targetCity);
        if (result != null) {
            log.info("🌤 [WeatherApiService] ✅ wttr.in 成功");
            return result;
        }
        log.warn("🌤 [WeatherApiService] wttr.in 获取失败，使用兜底数据");

        // 4. 最终兜底
        WeatherResult fallback = buildFallbackResult(targetCity);
        log.info("🌤 [WeatherApiService] 使用兜底数据: city={}, temp={}", fallback.city, fallback.temperature);
        return fallback;
    }

    // ==================== 城市定位 ====================

    /**
     * 解析目标城市：城市名 > 经纬度反查 > IP定位 > 默认
     */
    private String resolveCity(String city, Double lat, Double lng, String ip) {
        log.info("🗺 [resolveCity] 入参: city={}, lat={}, lng={}, ip={}", city, lat, lng, ip);

        if (isNotEmpty(city)) {
            log.info("🗺 [resolveCity] 使用传入的城市名: {}", city);
            return city;
        }
        if (lat != null && lng != null) {
            log.info("🗺 [resolveCity] 尝试经纬度逆地理编码: lat={}, lng={}", lat, lng);
            String geoCity = null;
            if (isNotEmpty(amapKey)) {
                log.info("🗺 [resolveCity] 高德 Key 已配置，调用高德逆地理编码...");
                geoCity = reverseGeocodeByAmap(lat, lng);
                log.info("🗺 [resolveCity] 高德逆地理编码结果: {}", geoCity);
            } else {
                log.warn("🗺 [resolveCity] 高德 Key 未配置，跳过高德逆地理编码");
            }
            if (isEmpty(geoCity)) {
                log.info("🗺 [resolveCity] 高德未返回结果，降级为 ip-api.com 逆地理编码...");
                geoCity = reverseGeocodeByIpApi(lat, lng);
                log.info("🗺 [resolveCity] ip-api.com 逆地理编码结果: {}", geoCity);
            }
            if (isNotEmpty(geoCity)) {
                return geoCity;
            }
            log.warn("🗺 [resolveCity] 经纬度逆地理编码全部失败");
        } else {
            log.info("🗺 [resolveCity] 未传入经纬度，跳过逆地理编码");
        }
        if (isNotEmpty(ip)) {
            log.info("🗺 [resolveCity] 尝试 IP 定位: ip={}", ip);
            String ipCity = null;
            if (isNotEmpty(amapKey)) {
                log.info("🗺 [resolveCity] 高德 Key 已配置，调用高德 IP 定位...");
                ipCity = locateByAmapIP(ip);
                log.info("🗺 [resolveCity] 高德 IP 定位结果: {}", ipCity);
            } else {
                log.warn("🗺 [resolveCity] 高德 Key 未配置，跳过高德 IP 定位");
            }
            if (isEmpty(ipCity)) {
                log.info("🗺 [resolveCity] 高德未返回结果，降级为 ip-api.com IP 定位...");
                ipCity = locateByIpApi(ip);
                log.info("🗺 [resolveCity] ip-api.com IP 定位结果: {}", ipCity);
            }
            if (isNotEmpty(ipCity)) {
                return ipCity;
            }
            log.warn("🗺 [resolveCity] IP 定位全部失败");
        } else {
            log.warn("🗺 [resolveCity] 未传入 IP，跳过 IP 定位");
        }
        log.warn("🗺 [resolveCity] 所有定位方式均失败，使用默认城市: 北京");
        return "北京";
    }

    // ==================== 和风天气 API ====================

    /**
     * 通过和风天气获取实时天气（新版 API）
     * <p>
     * 新版 API 变更：
     * 1. 使用专属 API Host（{xxx}.qweatherapi.com），不再用 devapi/geoapi.qweather.com
     * 2. 认证方式从 URL 参数 ?key=xxx 改为 HTTP Header X-QW-Api-Key: xxx
     * 3. Geo API 路径从 /v2/city/lookup 改为 /geo/v2/city/lookup
     * </p>
     * 步骤：① 城市搜索获取 LocationID → ② 获取实时天气
     */
    /**
     * 通过和风天气获取实时天气（新版 API）
     * <p>
     * 定位策略：
     * 1. 有经纬度 → 直接用坐标查天气（最准确，跳过城市搜索）
     * 2. 无经纬度 → 城市名搜索获取 LocationID 再查天气
     * </p>
     */
    private WeatherResult fetchFromQWeather(String cityName, Double lat, Double lng) {
        try {
            // 构建请求头（新版 API 使用 Header 认证）
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-QW-Api-Key", qweatherKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 确保 host 格式正确
            String host = qweatherHost.endsWith("/") ? qweatherHost.substring(0, qweatherHost.length() - 1) : qweatherHost;
            if (!host.startsWith("http")) {
                host = "https://" + host;
            }

            String locationParam;
            String displayCity;

            if (lat != null && lng != null) {
                // ① 有经纬度，直接用坐标查天气（和风天气格式：经度,纬度）
                locationParam = lng + "," + lat;
                displayCity = cityName;
                log.info("🌤 [QWeather] 使用经纬度直接查询: location={}", locationParam);
            } else {
                // ② 无经纬度，城市搜索获取 LocationID
                String encodedCity = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
                String lookupUrl = host + "/geo/v2/city/lookup?location=" + encodedCity + "&lang=zh";
                log.info("🌤 [QWeather] 城市搜索: url={}", lookupUrl);

                ResponseEntity<byte[]> lookupResp = restTemplate.exchange(lookupUrl, HttpMethod.GET, entity, byte[].class);
                String lookupBody = decompressGzip(lookupResp.getBody());
                log.info("🌤 [QWeather] 城市搜索返回: {}", lookupBody);
                JsonNode lookupRoot = objectMapper.readTree(lookupBody);

                String code = lookupRoot.path("code").asText();
                if (!"200".equals(code)) {
                    log.warn("🌤 [QWeather] 城市搜索失败: code={}, city={}", code, cityName);
                    return null;
                }

                JsonNode locationList = lookupRoot.path("location");
                if (!locationList.isArray() || locationList.size() == 0) {
                    log.warn("🌤 [QWeather] 未找到城市: {}", cityName);
                    return null;
                }

                // 取第一个中国城市结果（优先 adm1 包含中国或 country 为中国）
                JsonNode chosen = null;
                for (int i = 0; i < locationList.size(); i++) {
                    String country = locationList.get(i).path("country").asText();
                    if (country.contains("中国") || country.contains("China")) {
                        chosen = locationList.get(i);
                        break;
                    }
                }
                if (chosen == null) {
                    chosen = locationList.get(0);
                }

                locationParam = chosen.path("id").asText();
                displayCity = chosen.path("name").asText();
                log.info("🌤 [QWeather] 城市搜索成功: city={}, locationId={}", displayCity, locationParam);
            }

            // 获取实时天气
            String weatherUrl = host + "/v7/weather/now?location=" + locationParam + "&lang=zh";
            log.info("🌤 [QWeather] 获取实时天气: url={}", weatherUrl);

            ResponseEntity<byte[]> weatherResp = restTemplate.exchange(weatherUrl, HttpMethod.GET, entity, byte[].class);
            String weatherBody = decompressGzip(weatherResp.getBody());
            log.info("🌤 [QWeather] 天气返回: {}", weatherBody);
            JsonNode weatherRoot = objectMapper.readTree(weatherBody);

            String weatherCode = weatherRoot.path("code").asText();
            if (!"200".equals(weatherCode)) {
                log.warn("🌤 [QWeather] 天气获取失败: code={}, city={}", weatherCode, cityName);
                return null;
            }

            JsonNode now = weatherRoot.path("now");
            String temp = now.path("temp").asText();
            String feelsLike = now.path("feelsLike").asText();
            String text = now.path("text").asText();
            String humidity = now.path("humidity").asText();

            WeatherResult result = new WeatherResult();
            result.city = displayCity;
            result.country = "中国";
            result.temperature = temp + "°C";
            result.feelsLike = feelsLike + "°C";
            result.condition = text;
            result.conditionEn = text;
            result.humidity = humidity + "%";
            result.success = true;
            result.source = "qweather";

            log.info("🌤 [QWeather] ✅ 天气获取成功: city={}, temp={}, condition={}", displayCity, temp, text);
            return result;

        } catch (Exception e) {
            log.warn("🌤 [QWeather] API调用异常: city={}, error={}", cityName, e.getMessage());
            return null;
        }
    }

    /**
     * 解压 Gzip 响应数据
     * 和风天气 API 强制使用 Gzip 压缩，必须手动解压
     */
    private String decompressGzip(byte[] compressed) throws IOException {
        if (compressed == null || compressed.length == 0) {
            return "";
        }
        // 检查是否为 Gzip（magic number: 0x1F 0x8B）
        if (compressed.length >= 2 && (compressed[0] & 0xFF) == 0x1F && (compressed[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed));
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = gis.read(buffer)) != -1) {
                    bos.write(buffer, 0, len);
                }
                return bos.toString(StandardCharsets.UTF_8);
            }
        }
        // 非 Gzip，直接转字符串
        return new String(compressed, StandardCharsets.UTF_8);
    }

    // ==================== 高德地图 API ====================

    /**
     * 高德地图逆地理编码（经纬度 → 城市名）
     */
    private String reverseGeocodeByAmap(double lat, double lng) {
        try {
            String url = "https://restapi.amap.com/v3/geocode/regeo?location="
                    + lng + "," + lat + "&key=" + amapKey + "&extensions=base";

            String resp = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(resp);

            String status = root.path("status").asText();
            if (!"1".equals(status)) {
                return "";
            }

            JsonNode regeocode = root.path("regeocode");
            JsonNode addressComponent = regeocode.path("addressComponent");

            // 优先取城市名，其次取区县名
            String city = addressComponent.path("city").asText();
            if (city.isEmpty() || "[]".equals(city)) {
                city = addressComponent.path("district").asText();
            }
            if (city.isEmpty() || "[]".equals(city)) {
                city = addressComponent.path("province").asText();
            }

            if (!city.isEmpty()) {
                log.info("高德逆地理编码成功: ({}, {}) → {}", lat, lng, city);
                return city;
            }
        } catch (Exception e) {
            log.warn("高德逆地理编码失败: lat={}, lng={}, error={}", lat, lng, e.getMessage());
        }
        return "";
    }

    /**
     * 高德地图 IP 定位
     */
    private String locateByAmapIP(String ip) {
        try {
            String url = "https://restapi.amap.com/v3/ip?key=" + amapKey + "&ip=" + ip;

            String resp = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(resp);

            String status = root.path("status").asText();
            if (!"1".equals(status)) {
                return "";
            }

            String city = root.path("city").asText();
            if (!city.isEmpty()) {
                log.info("高德IP定位成功: {} → {}", ip, city);
                return city;
            }
        } catch (Exception e) {
            log.warn("高德IP定位失败: ip={}, error={}", ip, e.getMessage());
        }
        return "";
    }

    // ==================== 旧方案降级（ip-api.com + wttr.in） ====================

    /**
     * wttr.in 获取天气（旧方案降级）
     */
    private WeatherResult fetchFromWttr(String cityName) {
        try {
            String encodedCity = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
            String url = "https://wttr.in/" + encodedCity + "?format=j1";

            String jsonResponse = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);

            JsonNode currentCondition = root.path("current_condition").get(0);
            String tempC = currentCondition.path("temp_C").asText();
            String weatherDesc = currentCondition.path("weatherDesc").get(0).path("value").asText();
            String humidity = currentCondition.path("humidity").asText();
            String feelsLikeC = currentCondition.path("FeelsLikeC").asText();

            JsonNode nearestArea = root.path("nearest_area").get(0);
            String areaName = nearestArea.path("areaName").get(0).path("value").asText();
            String country = nearestArea.path("country").get(0).path("value").asText();

            WeatherResult result = new WeatherResult();
            result.city = areaName;
            result.country = country;
            result.temperature = tempC + "°C";
            result.feelsLike = feelsLikeC + "°C";
            result.condition = translateWeather(weatherDesc);
            result.conditionEn = weatherDesc;
            result.humidity = humidity + "%";
            result.success = true;
            result.source = "wttr";

            log.info("wttr.in获取成功(降级): city={}, temp={}", areaName, tempC);
            return result;

        } catch (Exception e) {
            log.warn("wttr.in获取失败: city={}, error={}", cityName, e.getMessage());
            return null;
        }
    }

    /**
     * ip-api.com 逆地理编码（旧方案降级）
     */
    private String reverseGeocodeByIpApi(double lat, double lng) {
        try {
            String url = "http://ip-api.com/json/" + lat + "," + lng + "?lang=zh-CN&fields=city";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            String city = root.path("city").asText();
            if (!city.isEmpty()) {
                log.info("ip-api逆地理编码成功(降级): ({}, {}) → {}", lat, lng, city);
                return city;
            }
        } catch (Exception e) {
            log.warn("ip-api逆地理编码失败(降级): lat={}, lng={}", lat, lng, e.getMessage());
        }
        return "";
    }

    /**
     * ip-api.com IP定位（旧方案降级）
     */
    private String locateByIpApi(String ip) {
        try {
            String url = "http://ip-api.com/json/" + ip + "?lang=zh-CN&fields=city";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            String city = root.path("city").asText();
            if (!city.isEmpty()) {
                log.info("ip-api IP定位成功(降级): {} → {}", ip, city);
                return city;
            }
        } catch (Exception e) {
            log.warn("ip-api IP定位失败(降级): {}", e.getMessage());
        }
        return "";
    }

    // ==================== 兜底 ====================

    private WeatherResult buildFallbackResult(String city) {
        WeatherResult result = new WeatherResult();
        result.city = city;
        result.country = "中国";
        result.temperature = "15°C ~ 25°C";
        result.feelsLike = "18°C";
        result.condition = "多云";
        result.conditionEn = "Cloudy";
        result.humidity = "60%";
        result.success = true;
        result.source = "fallback";
        log.warn("所有天气服务不可用，使用兜底数据: city={}", city);
        return result;
    }

    // ==================== 工具方法 ====================

    /**
     * 将英文天气描述翻译为中文
     */
    private String translateWeather(String en) {
        if (en == null) return "多云";
        String lower = en.toLowerCase();
        if (lower.contains("sunny") || lower.contains("clear")) return "晴天";
        if (lower.contains("partly cloudy")) return "多云";
        if (lower.contains("cloudy") || lower.contains("overcast")) return "阴天";
        if (lower.contains("rain") || lower.contains("drizzle") || lower.contains("shower")) return "雨天";
        if (lower.contains("snow") || lower.contains("ice") || lower.contains("sleet")) return "寒冷";
        if (lower.contains("fog") || lower.contains("mist") || lower.contains("haze")) return "阴天";
        if (lower.contains("hot") || lower.contains("heat")) return "晴热";
        if (lower.contains("dry") || lower.contains("dust")) return "干燥";
        return "多云";
    }

    private boolean isNotEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * 去掉城市名的行政后缀（市/区/县/自治州/地区等）
     * <p>
     * 和风天气和 wttr.in 用简短城市名查询更准确：
     * "郑州市" → "郑州"，"北京市" → "北京"，"朝阳区" → "朝阳"
     * </p>
     */
    private String trimCitySuffix(String city) {
        if (city == null || city.isEmpty()) {
            return city;
        }
        // 按后缀长度从长到短匹配，避免"自治州"被"州"提前截断
        String[] suffixes = {
                "蒙古自治州", "回族自治州", "哈萨克自治州", "土家族苗族自治州",
                "苗族自治州", "彝族自治州", "藏族自治州", "傣族自治州",
                "自治州", "自治县",
                "地区", "市辖区", "新区", "高新区", "开发区",
                "市", "区", "县", "旗", "州", "盟"
        };
        for (String suffix : suffixes) {
            if (city.endsWith(suffix) && city.length() > suffix.length()) {
                return city.substring(0, city.length() - suffix.length());
            }
        }
        return city;
    }
}