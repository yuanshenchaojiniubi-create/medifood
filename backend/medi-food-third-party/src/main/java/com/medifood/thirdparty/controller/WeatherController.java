package com.medifood.thirdparty.controller;

import com.medifood.thirdparty.service.WeatherApiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 天气查询控制器
 * <p>
 * 提供实时天气查询，支持以下定位方式（优先级从高到低）：
 * <ol>
 *   <li>用户浏览器提供的经纬度（lat/lng参数）→ 逆地理编码获取城市名</li>
 *   <li>通过请求IP定位城市</li>
 *   <li>默认城市 北京</li>
 * </ol>
 * 天气数据多级降级策略：
 * <ol>
 *   <li>和风天气 (QWeather) — 国产，稳定，需配置 API Key</li>
 *   <li>wttr.in — 国外免费天气服务，无需 Key</li>
 *   <li>兜底数据 — 所有服务不可用时的最终保障</li>
 * </ol>
 * </p>
 *
 * <h3>API Key 配置（在 application.properties 中）</h3>
 * <pre>
 * weather.qweather.key=你的和风天气key    # 免费申请：https://dev.qweather.com/
 * weather.amap.key=你的高德地图key        # 免费申请：https://lbs.amap.com/
 * </pre>
 *
 * @author MediFood Team
 */
@Slf4j
@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherApiService weatherApiService;

    public WeatherController(WeatherApiService weatherApiService) {
        this.weatherApiService = weatherApiService;
    }

    /**
     * 获取当前天气
     *
     * @param city    城市名称（可选）
     * @param lat     纬度（可选，来自前端 Geolocation API）
     * @param lng     经度（可选）
     * @param request HTTP 请求对象（用于获取客户端IP）
     * @return 天气信息，包含 city、temperature、condition、humidity、source 等
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentWeather(
            @RequestParam(value = "city", defaultValue = "") String city,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            HttpServletRequest request) {

        String clientIp = getClientIP(request);
        log.info("🌍 [ThirdParty] 收到天气请求: city={}, lat={}, lng={}, clientIp={}", city, lat, lng, clientIp);

        WeatherApiService.WeatherResult weather = weatherApiService.getWeather(city, lat, lng, clientIp);
        log.info("🌍 [ThirdParty] 天气查询结果: city={}, temp={}, condition={}, success={}, source={}",
                weather.city, weather.temperature, weather.condition, weather.success, weather.source);

        Map<String, Object> result = new HashMap<>();
        result.put("city", weather.city);
        result.put("country", weather.country);
        result.put("temperature", weather.temperature);
        result.put("feelsLike", weather.feelsLike);
        result.put("condition", weather.condition);
        result.put("conditionEn", weather.conditionEn);
        result.put("humidity", weather.humidity);
        result.put("success", weather.success);
        result.put("source", weather.source);

        return ResponseEntity.ok(result);
    }

    /**
     * 获取客户端真实IP（适配多层代理）
     */
    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}