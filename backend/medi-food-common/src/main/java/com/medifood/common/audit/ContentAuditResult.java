package com.medifood.common.audit;

/**
 * AI 内容审核结果（三态）
 *
 * <ul>
 *   <li>{@link #VERDICT_SAFE} —— 内容安全：
 *       先发后审内容无需处理；先审后发内容自动转为"已发布"状态</li>
 *   <li>{@link #VERDICT_SUSPICIOUS} —— 疑似违规（擦边/不确定）：
 *       记入审核台账，等待管理员人工复核</li>
 *   <li>{@link #VERDICT_VIOLATION} —— 明确违规：
 *       自动删除内容并通知发布者（严重情形）</li>
 * </ul>
 */
public class ContentAuditResult {

    public static final String VERDICT_SAFE = "safe";
    public static final String VERDICT_SUSPICIOUS = "suspicious";
    public static final String VERDICT_VIOLATION = "violation";

    /** 审核结论：safe / suspicious / violation */
    private String verdict;
    /** 判定原因简述 */
    private String reason;
    /** 违规类别（色情低俗/暴力血腥/违法信息/人身攻击/广告营销/正常） */
    private String category;

    public ContentAuditResult() {}

    public ContentAuditResult(String verdict, String reason, String category) {
        this.verdict = verdict;
        this.reason = reason;
        this.category = category;
    }

    public boolean isSafe() {
        return VERDICT_SAFE.equals(verdict);
    }

    public boolean isSuspicious() {
        return VERDICT_SUSPICIOUS.equals(verdict);
    }

    public boolean isViolation() {
        return VERDICT_VIOLATION.equals(verdict);
    }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
