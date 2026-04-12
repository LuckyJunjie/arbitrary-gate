package com.timespace.module.share.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.timespace.common.exception.BusinessException;
import com.timespace.common.utils.IdGenerator;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.module.card.entity.UserKeywordCard;
import com.timespace.module.card.mapper.KeywordCardMapper;
import com.timespace.module.card.mapper.UserKeywordCardMapper;
import com.timespace.module.share.entity.StoryReader;
import com.timespace.module.share.entity.StoryShare;
import com.timespace.module.share.entity.SpecialCard;
import com.timespace.module.share.entity.UserSpecialCard;
import com.timespace.module.share.mapper.StoryReaderMapper;
import com.timespace.module.share.mapper.StoryShareMapper;
import com.timespace.module.share.mapper.SpecialCardMapper;
import com.timespace.module.share.mapper.UserSpecialCardMapper;
import com.timespace.module.share.service.CommemorativeCardService.CommemorativeCardVO;
import com.timespace.module.story.entity.Story;
import com.timespace.module.story.entity.StoryManuscript;
import com.timespace.module.story.mapper.StoryMapper;
import com.timespace.module.story.mapper.StoryManuscriptMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService extends ServiceImpl<StoryShareMapper, StoryShare> {

    private final StoryShareMapper storyShareMapper;
    private final SpecialCardMapper specialCardMapper;
    private final StoryReaderMapper storyReaderMapper;
    private final UserSpecialCardMapper userSpecialCardMapper;
    private final UserKeywordCardMapper userKeywordCardMapper;
    private final KeywordCardMapper keywordCardMapper;
    private final StoryMapper storyMapper;
    private final StoryManuscriptMapper storyManuscriptMapper;
    private final RedissonClient redissonClient;
    private final CommemorativeCardService commemorativeCardService;

    @Value("${timespace.share.code-length:8}")
    private int shareCodeLength;

    private static final long SHARE_EXPIRE_DAYS = 7L;
    private static final String JOINT_LOCK_PREFIX = "share:joint:lock:";

    /**
     * POST /api/share/create
     * 生成分享码
     *
     * 请求：
     * {
     *   "storyId": 1,
     *   "cardId": 101   // 用户选择作为缺角的关键词卡ID
     * }
     *
     * 响应：
     * {
     *   "shareCode": "ABC12345",
     *   "cardName": "旧船票",
     *   "cardCategory": 1,
     *   "expiresAt": "2024-04-17T00:00:00",
     *   "storyTitle": "长安十二时辰"
     * }
     */
    @Transactional
    public CreateShareVO createShare(Long userId, CreateShareRequest request) {
        String lockKey = "share:create:" + userId + ":" + request.getStoryId();
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(400, "操作过于频繁，请稍后");
            }

            // 1. 校验故事存在且属于当前用户
            Story story = storyMapper.selectById(request.getStoryId());
            if (story == null) {
                throw BusinessException.STORY_NOT_FOUND;
            }
            if (!story.getUserId().equals(userId)) {
                throw new BusinessException(403, "无权分享此故事");
            }
            if (story.getStatus() != 2) {
                throw new BusinessException(400, "故事未完成，无法分享");
            }

            // 2. 校验用户拥有这张关键词卡
            UserKeywordCard userCard = userKeywordCardMapper.selectOne(
                    new LambdaQueryWrapper<UserKeywordCard>()
                            .eq(UserKeywordCard::getUserId, userId)
                            .eq(UserKeywordCard::getCardId, request.getCardId())
                            .last("limit 1")
            );
            if (userCard == null) {
                throw BusinessException.CARD_NOT_FOUND;
            }

            // 3. 获取卡牌信息
            KeywordCard keywordCard = keywordCardMapper.selectById(request.getCardId());
            if (keywordCard == null) {
                throw BusinessException.CARD_NOT_FOUND;
            }

            // 4. 生成分享码
            String shareCode = IdGenerator.shareCode();

            // 5. 创建分享记录
            StoryShare share = new StoryShare()
                    .setShareCode(shareCode)
                    .setStoryId(request.getStoryId())
                    .setCreatorUserId(userId)
                    .setMissingCornerCardId(request.getCardId())
                    .setCardName(keywordCard.getName())
                    .setCardCategory(keywordCard.getCategory())
                    .setStatus(1) // 待合券
                    .setExpiresAt(LocalDateTime.now().plusDays(SHARE_EXPIRE_DAYS))
                    .setCreatedAt(LocalDateTime.now());

            storyShareMapper.insert(share);

            log.info("生成分享码: userId={}, storyId={}, cardId={}, shareCode={}",
                    userId, request.getStoryId(), request.getCardId(), shareCode);

            return new CreateShareVO()
                    .setShareCode(shareCode)
                    .setCardName(keywordCard.getName())
                    .setCardCategory(keywordCard.getCategory())
                    .setExpiresAt(share.getExpiresAt())
                    .setStoryTitle(story.getTitle());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "系统繁忙，请稍后重试");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * GET /api/share/{code}
     * 根据分享码获取分享信息（查看缺角卡）
     *
     * 响应：
     * {
     *   "shareCode": "ABC12345",
     *   "cardName": "旧船票",
     *   "cardCategory": 1,
     *   "storyTitle": "长安十二时辰",
     *   "storyId": 1,
     *   "status": "pending" | "jointed",
     *   "creatorNickname": "时光旅人",
     *   "expiresAt": "2024-04-17T00:00:00"
     * }
     */
    public ShareInfoVO getShareInfo(String code) {
        StoryShare share = storyShareMapper.selectOne(
                new LambdaQueryWrapper<StoryShare>()
                        .eq(StoryShare::getShareCode, code)
                        .eq(StoryShare::getDeleted, 0)
                        .last("limit 1")
        );

        if (share == null) {
            // I-07: Timing Attack 防护 — 不存在的分享码也延迟响应，统一响应时间
            try {
                Thread.sleep(50 + (long) (Math.random() * 150));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            throw BusinessException.SHARE_CODE_INVALID;
        }

        // 检查是否过期
        if (share.getExpiresAt().isBefore(LocalDateTime.now())) {
            // 更新状态为已过期
            share.setStatus(3);
            storyShareMapper.updateById(share);
            throw BusinessException.SHARE_CODE_EXPIRED;
        }

        Story story = storyMapper.selectById(share.getStoryId());

        return new ShareInfoVO()
                .setShareCode(share.getShareCode())
                .setCardName(share.getCardName())
                .setCardCategory(share.getCardCategory())
                .setStoryTitle(story != null ? story.getTitle() : "未知故事")
                .setStoryId(share.getStoryId())
                .setStatus(share.getStatus() == 2 ? "jointed" : "pending")
                .setExpiresAt(share.getExpiresAt());
    }

    /**
     * POST /api/share/{code}/joint
     * 合券接口
     *
     * 请求：
     * {
     *   "cardId": 102   // 用户持有的、能与缺角匹配的关键词卡ID
     * }
     *
     * 响应：
     * {
     *   "success": true,
     *   "message": "合券成功！",
     *   "storyTitle": "长安十二时辰",
     *   "storyId": 1,
     *   "specialCardId": 1,
     *   "specialCardName": "合璧笺",
     *   "grantedReadPermission": true
     * }
     */
    @Transactional
    public JointResultVO jointShare(Long userId, String code, JointShareRequest request) {
        String lockKey = JOINT_LOCK_PREFIX + code;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(400, "合券操作过于频繁，请稍后");
            }

            // 1. 查找分享记录
            StoryShare share = storyShareMapper.selectOne(
                    new LambdaQueryWrapper<StoryShare>()
                            .eq(StoryShare::getShareCode, code)
                            .eq(StoryShare::getDeleted, 0)
                            .last("limit 1")
            );

            if (share == null) {
                throw BusinessException.SHARE_CODE_INVALID;
            }

            if (share.getExpiresAt().isBefore(LocalDateTime.now())) {
                share.setStatus(3);
                storyShareMapper.updateById(share);
                throw BusinessException.SHARE_CODE_EXPIRED;
            }

            if (share.getStatus() == 2) {
                throw new BusinessException(400, "该分享码已被使用");
            }

            // 2. 不能分享给自己的故事
            if (share.getCreatorUserId().equals(userId)) {
                throw new BusinessException(400, "不能与自己分享的故事合券");
            }

            // 3. 校验用户持有这张卡
            UserKeywordCard userCard = userKeywordCardMapper.selectOne(
                    new LambdaQueryWrapper<UserKeywordCard>()
                            .eq(UserKeywordCard::getUserId, userId)
                            .eq(UserKeywordCard::getCardId, request.getCardId())
                            .last("limit 1")
            );
            if (userCard == null) {
                throw BusinessException.CARD_NOT_FOUND;
            }

            // 4. 校验用户持有的卡与缺角卡同类（category 相同才可合券）
            KeywordCard userKeywordCard = keywordCardMapper.selectById(request.getCardId());
            if (userKeywordCard == null) {
                throw BusinessException.CARD_NOT_FOUND;
            }

            // 简单匹配规则：同类卡可合，或者同为奇品/绝品可以跨类合
            // 设计：category 相同即可合券，体现"物以类聚"
            boolean sameCategory = userKeywordCard.getCategory().equals(share.getCardCategory());
            boolean isHighRarity = userKeywordCard.getRarity() >= 3 || (share.getCardCategory() != null && share.getCardCategory() >= 3);

            if (!sameCategory && !isHighRarity) {
                throw BusinessException.KEYWORD_CARD_MISMATCH;
            }

            // 5. 匹配成功：更新分享状态
            share.setStatus(2);
            share.setJointUserId(userId);
            share.setJointedAt(LocalDateTime.now());
            storyShareMapper.updateById(share);

            // 6. 授予合券者对故事的阅读权限
            StoryReader reader = new StoryReader()
                    .setStoryId(share.getStoryId())
                    .setUserId(userId)
                    .setSourceShareCode(code)
                    .setReadAt(LocalDateTime.now());
            // 防止重复插入
            StoryReader existing = storyReaderMapper.selectOne(
                    new LambdaQueryWrapper<StoryReader>()
                            .eq(StoryReader::getStoryId, share.getStoryId())
                            .eq(StoryReader::getUserId, userId)
                            .last("limit 1")
            );
            if (existing == null) {
                storyReaderMapper.insert(reader);
            }

            // 7. 双方各获得一张合券纪念卡（special_card）
            // 以模板卡 SC-COUPON-001 为基础，创建专属纪念卡
            Long specialCardId = grantSpecialCard(share.getCreatorUserId(), share, "creator");
            Long specialCardIdJoint = grantSpecialCard(userId, share, "joint");

            // 8. 生成合券纪念卡（永久纪念）
            CommemorativeCardVO commCard = commemorativeCardService.generateCommemorativeCard(
                    share.getStoryId(), share.getCreatorUserId(), userId);

            Story story = storyMapper.selectById(share.getStoryId());

            log.info("合券成功: shareCode={}, creatorUserId={}, jointUserId={}, storyId={}",
                    code, share.getCreatorUserId(), userId, share.getStoryId());

            return new JointResultVO()
                    .setSuccess(true)
                    .setMessage("合券成功！")
                    .setStoryTitle(story != null ? story.getTitle() : "未知故事")
                    .setStoryId(share.getStoryId())
                    .setSpecialCardId(specialCardIdJoint)
                    .setSpecialCardName("合璧笺")
                    .setGrantedReadPermission(true)
                    .setCommemorativeCardId(commCard != null ? commCard.getId() : null)
                    .setCommemorativeCardNo(commCard != null ? commCard.getCardNo() : null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "系统繁忙，请稍后重试");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 向用户授予合券纪念卡
     */
    private Long grantSpecialCard(Long userId, StoryShare share, String role) {
        // 查找模板纪念卡
        SpecialCard template = specialCardMapper.selectOne(
                new LambdaQueryWrapper<SpecialCard>()
                        .eq(SpecialCard::getCardNo, "SC-COUPON-001")
                        .last("limit 1")
        );

        if (template == null) {
            log.warn("未找到合券纪念卡模板");
            return null;
        }

        // 创建专属纪念卡（克隆模板）
        String newCardNo = "SC-" + share.getShareCode() + "-" + role.charAt(0);
        SpecialCard specialCard = new SpecialCard()
                .setCardNo(newCardNo)
                .setName("合璧笺·" + share.getCardName())
                .setDescription("与" + (role.equals("creator") ? "合券者" : "分享者") + "的限定纪念卡。记录一段跨越时空的相遇。")
                .setSourceStoryId(share.getStoryId())
                .setSourceShareCode(share.getShareCode())
                .setRarity(3) // 奇品
                .setCreatedAt(LocalDateTime.now());

        specialCardMapper.insert(specialCard);

        // 授予用户这张卡
        UserSpecialCard userSpecial = new UserSpecialCard()
                .setUserId(userId)
                .setSpecialCardId(specialCard.getId())
                .setSourceShareCode(share.getShareCode())
                .setAcquiredAt(LocalDateTime.now());

        // 防止重复授予
        UserSpecialCard existing = userSpecialCardMapper.selectOne(
                new LambdaQueryWrapper<UserSpecialCard>()
                        .eq(UserSpecialCard::getUserId, userId)
                        .eq(UserSpecialCard::getSpecialCardId, specialCard.getId())
                        .last("limit 1")
        );
        if (existing == null) {
            userSpecialCardMapper.insert(userSpecial);
        }

        return specialCard.getId();
    }

    /**
     * 获取用户获得的合券纪念卡列表
     */
    public List<SpecialCardVO> getUserSpecialCards(Long userId) {
        List<UserSpecialCard> userCards = userSpecialCardMapper.selectList(
                new LambdaQueryWrapper<UserSpecialCard>()
                        .eq(UserSpecialCard::getUserId, userId)
                        .orderByDesc(UserSpecialCard::getAcquiredAt)
        );

        return userCards.stream().map(uc -> {
            SpecialCard card = specialCardMapper.selectById(uc.getSpecialCardId());
            if (card == null) return null;
            return new SpecialCardVO()
                    .setId(card.getId())
                    .setCardNo(card.getCardNo())
                    .setName(card.getName())
                    .setDescription(card.getDescription())
                    .setImageUrl(card.getImageUrl())
                    .setRarity(card.getRarity())
                    .setSourceStoryId(card.getSourceStoryId())
                    .setSourceShareCode(card.getSourceShareCode())
                    .setAcquiredAt(uc.getAcquiredAt());
        }).filter(c -> c != null).toList();
    }

    // ========== DTO ==========

    @Data
    public static class CreateShareRequest {
        private Long storyId;
        private Long cardId;
    }

    @Data
    @lombok.Builder
    public static class CreateShareVO {
        private String shareCode;
        private String cardName;
        private Integer cardCategory;
        private LocalDateTime expiresAt;
        private String storyTitle;
    }

    @Data
    @lombok.Builder
    public static class ShareInfoVO {
        private String shareCode;
        private String cardName;
        private Integer cardCategory;
        private String storyTitle;
        private Long storyId;
        private String status; // "pending" | "jointed"
        private LocalDateTime expiresAt;
    }

    @Data
    public static class JointShareRequest {
        private Long cardId;
    }

    @Data
    @lombok.Builder
    public static class JointResultVO {
        private Boolean success;
        private String message;
        private String storyTitle;
        private Long storyId;
        private Long specialCardId;
        private String specialCardName;
        private Boolean grantedReadPermission;
        /** 合券纪念卡ID（SH-04） */
        private Long commemorativeCardId;
        /** 合券纪念卡编号 */
        private String commemorativeCardNo;
    }

    @Data
    @lombok.Builder
    public static class SpecialCardVO {
        private Long id;
        private String cardNo;
        private String name;
        private String description;
        private String imageUrl;
        private Integer rarity;
        private Long sourceStoryId;
        private String sourceShareCode;
        private LocalDateTime acquiredAt;
    }
}
