package com.timespace.module.image.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.timespace.common.exception.GlobalExceptionHandler.Result;
import com.timespace.module.image.service.ImageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * AI 图片生成 Controller
 * C-14 AI画师对接
 *
 * 端点：
 * POST /api/image/generate
 *   - 调用通义万相/SD API 生成图片
 *   - 返回图片 URL（上传 OSS 或直接返回 base64）
 *
 * 请求：
 * {
 *   "prompt": "水墨淡彩风格...",
 *   "size": "512*768" | "1024*1024"  // 默认 512*768
 * }
 *
 * 响应：
 * {
 *   "code": 200,
 *   "data": {
 *     "imageUrl": "https://...",
 *     "cached": false
 *   }
 * }
 */
@Slf4j
@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    /**
     * POST /api/image/generate
     * 生成图片（AI 画师）
     *
     * C-14: 调用通义万相 API，支持 prompt builder 传入的 prompt
     * 缓存：相同 prompt hash 不重复生成（TTL 24h）
     */
    @PostMapping("/generate")
    public Result<ImageService.ImageGenerateResult> generateImage(
            @RequestBody ImageGenerateRequest request) {
        try {
            StpUtil.checkLogin();
        } catch (Exception e) {
            // 允许游客调用（降级方案）
            log.debug("[Image] 游客调用图片生成 API");
        }

        log.info("[C-14] 生成图片请求: promptLen={}, size={}",
                request.getPrompt() != null ? request.getPrompt().length() : 0,
                request.getSize());

        ImageService.ImageGenerateResult result = imageService.generateImage(
                request.getPrompt(),
                request.getSize()
        );

        return Result.success(result);
    }

    @Data
    public static class ImageGenerateRequest {
        /** 英文 prompt（由前端 aiPainter.ts 的 prompt builder 构建） */
        private String prompt;

        /** 图片尺寸，默认 512*768（竖版卡面） */
        private String size = "512*768";
    }
}
