package com.timespace.module.share.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.timespace.common.exception.BusinessException;
import com.timespace.common.exception.GlobalExceptionHandler.Result;
import com.timespace.module.share.service.ShareService;
import com.timespace.module.share.service.ShareService.CreateShareRequest;
import com.timespace.module.share.service.ShareService.CreateShareVO;
import com.timespace.module.share.service.ShareService.JointResultVO;
import com.timespace.module.share.service.ShareService.JointShareRequest;
import com.timespace.module.share.service.ShareService.ShareInfoVO;
import com.timespace.module.share.service.ShareService.SpecialCardVO;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    /**
     * POST /api/share/create
     * 生成分享码
     *
     * 请求：
     * {
     *   "storyId": 1,
     *   "cardId": 101
     * }
     *
     * 响应：
     * {
     *   "code": 200,
     *   "data": {
     *     "shareCode": "ABC12345",
     *     "cardName": "旧船票",
     *     "cardCategory": 1,
     *     "expiresAt": "2024-04-17T00:00:00",
     *     "storyTitle": "长安十二时辰"
     *   }
     * }
     */
    @PostMapping("/create")
    public Result<CreateShareVO> createShare(@Valid @RequestBody CreateShareRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("生成分享码请求: userId={}, storyId={}, cardId={}",
                userId, request.getStoryId(), request.getCardId());
        CreateShareVO vo = shareService.createShare(userId, request);
        return Result.ok(vo);
    }

    /**
     * GET /api/share/{code}
     * 根据分享码获取分享信息（查看缺角卡）
     *
     * 响应：
     * {
     *   "code": 200,
     *   "data": {
     *     "shareCode": "ABC12345",
     *     "cardName": "旧船票",
     *     "cardCategory": 1,
     *     "storyTitle": "长安十二时辰",
     *     "storyId": 1,
     *     "status": "pending",
     *     "expiresAt": "2024-04-17T00:00:00"
     *   }
     * }
     */
    @GetMapping("/{code}")
    public Result<ShareInfoVO> getShareInfo(@PathVariable("code") String code) {
        log.info("获取分享信息: code={}", code);
        ShareInfoVO vo = shareService.getShareInfo(code);
        return Result.ok(vo);
    }

    /**
     * POST /api/share/{code}/joint
     * 合券接口
     *
     * 请求：
     * {
     *   "cardId": 102
     * }
     *
     * 响应：
     * {
     *   "code": 200,
     *   "data": {
     *     "success": true,
     *     "message": "合券成功！",
     *     "storyTitle": "长安十二时辰",
     *     "storyId": 1,
     *     "specialCardId": 1,
     *     "specialCardName": "合璧笺",
     *     "grantedReadPermission": true
     *   }
     * }
     */
    @PostMapping("/{code}/joint")
    public Result<JointResultVO> jointShare(
            @PathVariable("code") String code,
            @Valid @RequestBody JointShareRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("合券请求: userId={}, code={}, cardId={}", userId, code, request.getCardId());
        JointResultVO vo = shareService.jointShare(userId, code, request);
        return Result.ok(vo);
    }

    /**
     * GET /api/share/special-cards
     * 获取用户拥有的合券纪念卡列表
     */
    @GetMapping("/special-cards")
    public Result<List<SpecialCardVO>> getSpecialCards() {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("获取合券纪念卡列表: userId={}", userId);
        List<SpecialCardVO> cards = shareService.getUserSpecialCards(userId);
        return Result.ok(cards);
    }
}
