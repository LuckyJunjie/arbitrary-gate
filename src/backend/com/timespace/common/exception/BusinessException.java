package com.timespace.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessException extends RuntimeException {

    private final int code;
    private final String message;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(String message) {
        this(400, message);
    }

    // 常用业务异常
    public static BusinessException CARD_NOT_FOUND = new BusinessException(404, "卡牌不存在");
    public static BusinessException STORY_NOT_FOUND = new BusinessException(404, "故事不存在");
    public static BusinessException INK_STONE_NOT_ENOUGH = new BusinessException(400, "墨晶不足");
    public static BusinessException DAILY_FREE_EXHAUSTED = new BusinessException(400, "今日免费次数已用完");
    public static BusinessException SHARE_CODE_INVALID = new BusinessException(400, "分享码无效");
    public static BusinessException SHARE_CODE_EXPIRED = new BusinessException(400, "分享码已过期");
    public static BusinessException KEYWORD_CARD_MISMATCH = new BusinessException(400, "关键词卡与缺角不匹配");
    public static BusinessException STORY_NOT_FINISHED = new BusinessException(400, "故事尚未完成");
    public static BusinessException CHAPTER_NOT_FOUND = new BusinessException(404, "章节不存在");
    public static BusinessException CARD_ALREADY_OWNED = new BusinessException(400, "该卡牌已拥有");
    public static BusinessException CARD_LIMIT_REACHED = new BusinessException(400, "卡匣已满，请先使用或回炉卡牌");
    public static BusinessException AI_SERVICE_ERROR = new BusinessException(500, "AI服务异常");
}
