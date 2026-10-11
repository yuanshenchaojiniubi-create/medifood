package com.medifood.community.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationAggregateScheduler {

    private final NotificationService notificationService;

    /**
     * 1~3秒微批窗口：这里使用 1 秒扫描 + 2 秒窗口聚合触发。
     */
    @Scheduled(fixedDelay = 1000)
    public void dispatchDueAggregates() {
        try {
            notificationService.dispatchDueAggregates();
        } catch (Exception e) {
            log.error("微批聚合 SSE 推送失败", e);
        }
    }

    /**
     * 每 30 秒将 Redis 聚合增量批量持久化到 MySQL。
     */
    @Scheduled(fixedDelay = 30000)
    public void flushAggregates() {
        try {
            notificationService.flushAggregatesToDb();
        } catch (Exception e) {
            log.error("聚合通知批量落库失败", e);
        }
    }
}
