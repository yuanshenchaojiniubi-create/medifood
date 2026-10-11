package org.example.medifoodgateway.config;

/**
 * 网关层：允许匿名访问的 API（与前端「访客仅首页+登录注册」策略一致，首页需拉取热门榜）
 */
public final class GatewayPublicPaths {

    private GatewayPublicPaths() {
    }

    /**
     * 无需 JWT 即可访问（当前仅首页热门接口）。
     * 同时兼容网关路径重写前后的形态：knowledge 路由会把 /api/knowledge/ 重写为 /knowledge/
     * （重写过滤器先于匿名检查执行，所以两个前缀都要匹配）。
     */
    public static boolean allowsAnonymous(String path, String httpMethod) {
        if (!"GET".equalsIgnoreCase(httpMethod)) {
            return false;
        }
        return "/api/knowledge/hot".equals(path) || "/knowledge/hot".equals(path)
                || "/api/recipe/hot".equals(path) || "/recipe/hot".equals(path);
    }
}
