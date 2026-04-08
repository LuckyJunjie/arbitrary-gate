package com.timespace.module.card.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.timespace.common.exception.BusinessException;
import com.timespace.common.utils.IdGenerator;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.module.card.entity.UserKeywordCard;
import com.timespace.module.card.mapper.KeywordCardMapper;
import com.timespace.module.card.mapper.UserKeywordCardMapper;
import com.timespace.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 抽卡服务
 * 抽卡算法核心逻辑：
 * - 每抽消耗墨晶或使用每日免费次数
 * - 保底机制：
 *   - 连续9次未出奇品，第10抽必出奇品（保底奇品）
 *   - 连续29次未出绝品，第30抽必出绝品（保底绝品）
 * - 保底优先于普通概率，即先检查保底再进行随机
 * - 抽中奇品时重置奇品保底计数，抽中绝品时重置绝品保底计数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardService extends ServiceImpl<KeywordCardMapper, KeywordCard> {

    private final KeywordCardMapper keywordCardMapper;
    private final UserKeywordCardMapper userKeywordCardMapper;
    private final UserService userService;
    private final RedissonClient redissonClient;
    private final DrawAlgorithm drawAlgorithm;

    @Value("${timespace.card.ink-stone-cost:10}")
    private int inkStoneCost;

    @Value("${timespace.card.daily-free-count:1}")
    private int dailyFreeCount;

    private static final String DRAW_LOCK_PREFIX = "draw:lock:user:";

    /**
     * 抽关键词卡
     *
     * @param userId 用户ID
     * @param useFreeDraw 是否使用免费次数（false则消耗墨晶）
     * @return 抽中的卡牌信息
     */
    @Transactional
    public DrawResult drawKeywordCard(Long userId, boolean useFreeDraw) {
        String lockKey = DRAW_LOCK_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            // 获取分布式锁，防止并发抽卡
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(400, "抽卡过于频繁，请稍后");
            }

            // 检查免费次数或墨晶
            if (useFreeDraw) {
                if (!userService.hasDailyFreeDraw(userId)) {
                    throw BusinessException.DAILY_FREE_EXHAUSTED;
                }
                userService.useDailyFreeDraw(userId);
            } else {
                userService.consumeInkStone(userId, inkStoneCost);
            }

            // 获取用户当前保底状态
            DrawAlgorithm.GuaranteeState state = drawAlgorithm.getGuaranteeState(userId);

            // 执行抽卡算法
            KeywordCard card = drawAlgorithm.executeDraw(state);

            // 记录保底状态
            drawAlgorithm.updateGuaranteeState(userId, state, card.getRarity());

            // 发放卡牌给用户
            UserKeywordCard userCard = grantCardToUser(userId, card);

            // 构建返回
            return DrawResult.builder()
                    .cardId(card.getId())
                    .cardNo(card.getCardNo())
                    .name(card.getName())
                    .category(card.getCategory())
                    .rarity(card.getRarity())
                    .description(card.getDescription())
                    .imageUrl(card.getImageUrl())
                    .inkFragrance(userCard.getInkFragrance())
                    .isGuaranteedRare(card.getRarity() >= 3) // 奇品或绝品
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "抽卡系统繁忙");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 获取用户拥有的关键词卡列表
     */
    public List<UserCardVO> getUserCards(Long userId, Integer category, Integer rarity, int page, int size) {
        LambdaQueryWrapper<UserKeywordCard> wrapper = new LambdaQueryWrapper<UserKeywordCard>()
                .eq(UserKeywordCard::getUserId, userId)
                .orderByDesc(UserKeywordCard::getAcquiredAt);

        List<UserKeywordCard> userCards = userKeywordCardMapper.selectPage(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserKeywordCard>()
                        .eq(UserKeywordCard::getUserId, userId), null).getRecords();

        if (userCards == null || userCards.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集卡牌ID
        List<Long> cardIds = userCards.stream()
                .map(UserKeywordCard::getCardId)
                .collect(Collectors.toList());

        // 查询卡牌详情
        LambdaQueryWrapper<KeywordCard> cardWrapper = new LambdaQueryWrapper<KeywordCard>()
                .in(KeywordCard::getId, cardIds);
        if (category != null) cardWrapper.eq(KeywordCard::getCategory, category);
        if (rarity != null) cardWrapper.eq(KeywordCard::getRarity, rarity);

        List<KeywordCard> cards = keywordCardMapper.selectList(cardWrapper);
        Map<Long, KeywordCard> cardMap = cards.stream()
                .collect(Collectors.toMap(KeywordCard::getId, c -> c));

        // 合并用户卡和卡牌信息
        return userCards.stream()
                .filter(uc -> cardMap.containsKey(uc.getCardId()))
                .map(uc -> {
                    KeywordCard card = cardMap.get(uc.getCardId());
                    return UserCardVO.builder()
                            .userCardId(uc.getId())
                            .cardId(card.getId())
                            .cardNo(card.getCardNo())
                            .name(card.getName())
                            .category(card.getCategory())
                            .rarity(card.getRarity())
                            .description(card.getDescription())
                            .imageUrl(card.getImageUrl())
                            .inkFragrance(uc.getInkFragrance())
                            .resonanceCount(uc.getResonanceCount())
                            .acquiredAt(uc.getAcquiredAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 检查用户是否拥有某张关键词卡
     */
    public boolean hasCard(Long userId, Long cardId) {
        return userKeywordCardMapper.selectCount(
                new LambdaQueryWrapper<UserKeywordCard>()
                        .eq(UserKeywordCard::getUserId, userId)
                        .eq(UserKeywordCard::getCardId, cardId)
        ) > 0;
    }

    /**
     * 增加关键词卡的墨香值
     */
    @Transactional
    public void increaseInkFragrance(Long userId, Long cardId, int delta) {
        UserKeywordCard userCard = userKeywordCardMapper.selectOne(
                new LambdaQueryWrapper<UserKeywordCard>()
                        .eq(UserKeywordCard::getUserId, userId)
                        .eq(UserKeywordCard::getCardId, cardId)
        );
        if (userCard == null) throw BusinessException.CARD_NOT_FOUND;
        userCard.setInkFragrance(Math.min(7, userCard.getInkFragrance() + delta));
        userCard.setResonanceCount(userCard.getResonanceCount() + 1);
        userKeywordCardMapper.updateById(userCard);
    }

    /**
     * 增加关键词卡的共鸣次数
     */
    @Transactional
    public void increaseResonance(Long userId, Long cardId) {
        increaseInkFragrance(userId, cardId, 1);
    }

    private UserKeywordCard grantCardToUser(Long userId, KeywordCard card) {
        // 检查是否已拥有
        UserKeywordCard existing = userKeywordCardMapper.selectOne(
                new LambdaQueryWrapper<UserKeywordCard>()
                        .eq(UserKeywordCard::getUserId, userId)
                        .eq(UserKeywordCard::getCardId, card.getId())
        );
        if (existing != null) {
            // 已拥有则增加墨香值（不超过7）
            existing.setInkFragrance(Math.min(7, existing.getInkFragrance() + 1));
            userKeywordCardMapper.updateById(existing);
            log.info("用户已拥有该卡，增加墨香值: userId={}, cardId={}", userId, card.getId());
            return existing;
        }

        // 新增记录
        UserKeywordCard userCard = new UserKeywordCard();
        userCard.setUserId(userId);
        userCard.setCardId(card.getId());
        userCard.setInkFragrance(7); // 新卡默认满墨香
        userCard.setResonanceCount(0);
        userCard.setAcquiredAt(LocalDateTime.now());
        userKeywordCardMapper.insert(userCard);
        return userCard;
    }

    @Data
    @lombok.Builder
    public static class DrawResult {
        private Long cardId;
        private String cardNo;
        private String name;
        private Integer category;
        private Integer rarity;
        private String description;
        private String imageUrl;
        private Integer inkFragrance;
        private boolean isGuaranteedRare; // 是否保底出
    }

    @Data
    @lombok.Builder
    public static class UserCardVO {
        private Long userCardId;
        private Long cardId;
        private String cardNo;
        private String name;
        private Integer category;
        private Integer rarity;
        private String description;
        private String imageUrl;
        private Integer inkFragrance;
        private Integer resonanceCount;
        private LocalDateTime acquiredAt;
    }
}
