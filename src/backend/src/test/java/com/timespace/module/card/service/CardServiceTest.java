package com.timespace.module.card.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.timespace.module.card.entity.UserKeywordCard;
import com.timespace.module.card.mapper.EventCardMapper;
import com.timespace.module.card.mapper.KeywordCardMapper;
import com.timespace.module.card.mapper.UserEventCardMapper;
import com.timespace.module.card.mapper.UserKeywordCardMapper;
import com.timespace.module.ai.client.AIClient;
import com.timespace.module.user.service.UserService;
import com.timespace.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.api.AtomicLongAsync;
import org.redisson.api.BatchOptions;
import org.redisson.api.Batch;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CardService 单元测试
 *
 * 测试覆盖：
 * - C-11 墨香渐淡（时间衰减）定时任务
 * - C-12 陈卡回炉逻辑
 * - 每日免费次数返还
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CardServiceTest {

    @Mock
    private KeywordCardMapper keywordCardMapper;

    @Mock
    private EventCardMapper eventCardMapper;

    @Mock
    private UserKeywordCardMapper userKeywordCardMapper;

    @Mock
    private UserEventCardMapper userEventCardMapper;

    @Mock
    private UserService userService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private DrawAlgorithm drawAlgorithm;

    @Mock
    private AIClient aiClient;

    @Mock
    private org.springframework.core.io.ResourceLoader resourceLoader;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Mock
    private RBucket<String> mockBucket;

    @Mock
    private RAtomicLong mockAtomicLong;

    private CardService cardService;

    @BeforeEach
    void setUp() {
        // 注意：CardService 继承 ServiceImpl<KeywordCardMapper, KeywordCard>
        // 构造时传入的 baseMapper 会被用作父类 baseMapper
        // 但我们主要测试的是有 @Mock 的方法
        cardService = new CardService(
                keywordCardMapper,
                eventCardMapper,
                userKeywordCardMapper,
                userEventCardMapper,
                userService,
                redissonClient,
                drawAlgorithm,
                aiClient,
                resourceLoader,
                objectMapper
        );
    }

    // ========== C-11 墨香渐淡测试 ==========

    @Test
    @DisplayName("C-11: 墨香衰减定时任务 — 验证调用 userKeywordCardMapper 执行衰减更新")
    void decayInkFragranceDaily_callsUserKeywordCardMapper() {
        // GIVEN: userKeywordCardMapper.update() 预期被调用一次，返回影响行数 5
        when(userKeywordCardMapper.update(
                isNull(),
                any(LambdaUpdateWrapper.class)
        )).thenReturn(5);

        // WHEN: 执行每日衰减定时任务
        cardService.decayInkFragranceDaily();

        // THEN: userKeywordCardMapper.update() 被调用一次（而非 baseMapper）
        verify(userKeywordCardMapper, times(1)).update(
                isNull(),
                any(LambdaUpdateWrapper.class)
        );
        // baseMapper（KeywordCardMapper）不应被调用
        verify(keywordCardMapper, never()).update(
                isNull(),
                any(LambdaUpdateWrapper.class)
        );
    }

    @Test
    @DisplayName("C-11: 墨香衰减 — 验证衰减 SQL 使用 GREATEST(0, ink_fragrance - 1)")
    @SuppressWarnings("unchecked")
    void decayInkFragranceDaily_usesCorrectSql() {
        // GIVEN
        when(userKeywordCardMapper.update(
                isNull(),
                any(LambdaUpdateWrapper.class)
        )).thenReturn(0);

        // WHEN
        cardService.decayInkFragranceDaily();

        // THEN: 捕获传入的 update wrapper，验证 setSql 包含正确的衰减表达式
        ArgumentCaptor<LambdaUpdateWrapper<UserKeywordCard>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userKeywordCardMapper).update(isNull(), captor.capture());

        String sql = captor.getValue().getSqlSegment();
        assertTrue(
                sql.contains("GREATEST") && sql.contains("ink_fragrance"),
                "衰减 SQL 应包含 GREATEST(0, ink_fragrance - 1)，实际: " + sql
        );
    }

    @Test
    @DisplayName("C-11: 墨香衰减 — 验证只更新 ink_fragrance > 0 的记录")
    @SuppressWarnings("unchecked")
    void decayInkFragranceDaily_onlyUpdatesPositiveInk() {
        // GIVEN
        when(userKeywordCardMapper.update(
                isNull(),
                any(LambdaUpdateWrapper.class)
        )).thenReturn(3);

        // WHEN
        cardService.decayInkFragranceDaily();

        // THEN: 捕获 update wrapper，验证 gt(ink_fragrance, 0) 条件存在
        ArgumentCaptor<LambdaUpdateWrapper<UserKeywordCard>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userKeywordCardMapper).update(isNull(), captor.capture());

        String sql = captor.getValue().getSqlSegment();
        assertTrue(
                sql.toLowerCase().contains("ink_fragrance"),
                "SQL 应包含 ink_fragrance 字段过滤条件"
        );
    }

    // ========== C-12 陈卡回炉测试 ==========

    @Test
    @DisplayName("C-12: 回炉成功 — 返还免费次数并返回剩余次数")
    void recycleCard_success_returnsRemainingDraws() {
        // GIVEN: 用户ID=1，卡牌存在，Redis 中今日未回炉
        Long userId = 1L;
        Long cardId = 100L;

        UserKeywordCard userCard = new UserKeywordCard();
        userCard.setId(cardId);
        userCard.setUserId(userId);
        userCard.setInkFragrance(3);

        when(userKeywordCardMapper.selectOne(any())).thenReturn(userCard);
        when(redissonClient.getBucket(anyString())).thenReturn(mockBucket);
        when(mockBucket.get()).thenReturn(null); // 今日未回炉
        when(redissonClient.getAtomicLong(anyString())).thenReturn(mockAtomicLong);
        when(mockAtomicLong.incrementAndGet()).thenReturn(1L);
        when(userService.getDailyFreeDraws(userId)).thenReturn(1); // DB中还有1次

        // WHEN
        CardService.RecycleResult result = cardService.recycleCard(userId, cardId);

        // THEN
        assertTrue(result.success());
        // 总剩余次数 = DB的1次 + Redis返还的1次 = 2次
        assertEquals(2, result.freeDrawsRemaining());
        // 验证卡牌记录被删除
        verify(userKeywordCardMapper).deleteById(cardId);
        // 验证 Redis key 被设置
        verify(mockBucket, atLeast(1)).set(anyString(), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("C-12: 回炉失败 — 卡牌不存在或不属于该用户")
    void recycleCard_cardNotFound_throws() {
        // GIVEN: 用户ID=1，但找不到属于该用户的卡牌
        Long userId = 1L;
        Long cardId = 999L;

        when(userKeywordCardMapper.selectOne(any())).thenReturn(null);

        // WHEN & THEN
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> cardService.recycleCard(userId, cardId)
        );
        assertEquals("卡牌不存在", ex.getMessage());
    }

    @Test
    @DisplayName("C-12: 回炉失败 — 今日已回炉过一张卡")
    void recycleCard_alreadyRecycledToday_throws() {
        // GIVEN: 用户ID=1，卡牌存在，但今日已回炉
        Long userId = 1L;
        Long cardId = 100L;

        UserKeywordCard userCard = new UserKeywordCard();
        userCard.setId(cardId);
        userCard.setUserId(userId);

        when(userKeywordCardMapper.selectOne(any())).thenReturn(userCard);
        when(redissonClient.getBucket(anyString())).thenReturn(mockBucket);
        when(mockBucket.get()).thenReturn("1"); // 今日已回炉

        // WHEN & THEN
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> cardService.recycleCard(userId, cardId)
        );
        assertTrue(ex.getMessage().contains("今日已回炉过"));
        // 验证 deleteById 未被调用
        verify(userKeywordCardMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("C-12: 回炉 — Redis key 格式为 recycle:user:{userId}:{date}")
    void recycleCard_usesCorrectRedisKeyFormat() {
        // GIVEN
        Long userId = 42L;
        Long cardId = 100L;
        String today = LocalDate.now().toString(); // e.g. "2026-04-11"

        UserKeywordCard userCard = new UserKeywordCard();
        userCard.setId(cardId);
        userCard.setUserId(userId);

        when(userKeywordCardMapper.selectOne(any())).thenReturn(userCard);
        when(redissonClient.getBucket(anyString())).thenReturn(mockBucket);
        when(mockBucket.get()).thenReturn(null);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(mockAtomicLong);
        when(mockAtomicLong.incrementAndGet()).thenReturn(1L);
        when(userService.getDailyFreeDraws(userId)).thenReturn(0);

        // WHEN
        cardService.recycleCard(userId, cardId);

        // THEN: 验证 Redis key 包含正确格式
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient, atLeast(1)).getBucket(keyCaptor.capture());

        String usedKey = keyCaptor.getValue();
        assertTrue(
                usedKey.matches("recycle:user:42:\\d{4}-\\d{2}-\\d{2}"),
                "Redis key 应为格式 recycle:user:{userId}:{date}，实际: " + usedKey
        );
    }

    @Test
    @DisplayName("C-12: 回炉 — TTL 设置为 86400 秒（24小时）")
    void recycleCard_setsCorrectTTL() {
        // GIVEN
        Long userId = 1L;
        Long cardId = 100L;

        UserKeywordCard userCard = new UserKeywordCard();
        userCard.setId(cardId);
        userCard.setUserId(userId);

        when(userKeywordCardMapper.selectOne(any())).thenReturn(userCard);
        when(redissonClient.getBucket(anyString())).thenReturn(mockBucket);
        when(mockBucket.get()).thenReturn(null);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(mockAtomicLong);
        when(mockAtomicLong.incrementAndGet()).thenReturn(1L);
        when(userService.getDailyFreeDraws(userId)).thenReturn(0);

        // WHEN
        cardService.recycleCard(userId, cardId);

        // THEN: 验证 expire 调用使用 86400 秒
        verify(mockAtomicLong).expire(java.time.Duration.ofSeconds(86400));
    }

    // ========== 墨香衰减边界测试 ==========

    @Test
    @DisplayName("C-11: 墨香衰减 — 当所有卡牌墨香值均为0时，update GT 条件确保不更新任何记录")
    void decayInkFragranceDaily_zeroInk_noUpdate() {
        // GIVEN: update 返回 0 行受影响
        when(userKeywordCardMapper.update(
                isNull(),
                any(LambdaUpdateWrapper.class)
        )).thenReturn(0);

        // WHEN: 执行衰减（不应抛出异常）
        assertDoesNotThrow(() -> cardService.decayInkFragranceDaily());

        // THEN: update 仍被调用
        verify(userKeywordCardMapper, times(1)).update(
                isNull(),
                any(LambdaUpdateWrapper.class)
        );
    }
}
