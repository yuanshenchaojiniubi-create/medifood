package com.medifood.knowledge.scheduler;

import com.medifood.knowledge.service.KnowledgeService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热门知识定时缓存刷新器
 *
 * <p>工作流程：
 * <ol>
 *   <li>服务启动后立即执行一次（@PostConstruct），完成缓存预热，避免冷启动压力</li>
 *   <li>此后每隔 20 分钟重新计算并刷新 Redis 中的热门知识列表</li>
 * </ol>
 *
 * <p>热度算法：热度分 = 点赞数×3 + 浏览数×1 + 评论数×2
 *
 * @author MediFood Team
 */
@Slf4j
@Component
public class HotKnowledgeScheduler {

    @Autowired
    private KnowledgeService knowledgeService;

    /**
     * 服务启动后立即执行一次，完成缓存预热
     * 使用独立线程，避免阻塞应用启动流程
     */
    @PostConstruct
    public void warmUpCache() {
        new Thread(() -> {
            try {
                log.info("[热门知识] 服务启动，开始缓存预热...");
                knowledgeService.refreshHotKnowledgeCache();
                log.info("[热门知识] 缓存预热完成");
            } catch (Exception e) {
                log.error("[热门知识] 缓存预热失败（不影响服务启动）: {}", e.getMessage(), e);
            }
        }, "hot-knowledge-warmup").start();
    }

    /**
     * 每 20 分钟定时刷新热门知识缓存
     * fixedRate = 20 * 60 * 1000 ms
     */
    @Scheduled(fixedRate = 20 * 60 * 1000L)
    public void refreshCache() {
        log.info("[热门知识] 定时刷新缓存开始...");
        knowledgeService.refreshHotKnowledgeCache();
        log.info("[热门知识] 定时刷新缓存结束");
    }
}
