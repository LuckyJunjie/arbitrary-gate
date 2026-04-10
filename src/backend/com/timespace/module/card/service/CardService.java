package com.timespace.module.card.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.timespace.common.exception.BusinessException;
import com.timespace.common.utils.IdGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timespace.module.card.entity.EventCard;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.module.card.entity.UserEventCard;
import com.timespace.module.card.entity.UserKeywordCard;
import com.timespace.module.card.mapper.EventCardMapper;
import com.timespace.module.card.mapper.KeywordCardMapper;
import com.timespace.module.card.mapper.UserEventCardMapper;
import com.timespace.module.card.mapper.UserKeywordCardMapper;
import com.timespace.module.ai.client.AIClient;
import com.timespace.module.user.service.UserService;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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
    private final EventCardMapper eventCardMapper;
    private final UserKeywordCardMapper userKeywordCardMapper;
    private final UserEventCardMapper userEventCardMapper;
    private final UserService userService;
    private final RedissonClient redissonClient;
    private final DrawAlgorithm drawAlgorithm;
    private final AIClient aiClient;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    @Value("${timespace.card.ink-stone-cost:10}")
    private int inkStoneCost;

    @Value("${timespace.card.daily-free-count:1}")
    private int dailyFreeCount;

    private static final String DRAW_LOCK_PREFIX = "draw:lock:user:";
    private static final int KEYWORD_CARD_LIMIT = 9;

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

            // 手牌上限检查：关键词卡最多9张
            int keywordCardCount = userKeywordCardMapper.countByUserId(userId);
            if (keywordCardCount >= KEYWORD_CARD_LIMIT) {
                throw BusinessException.KEYWORD_CARD_LIMIT_REACHED;
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

    // ========== 事件卡抽取 ========== //

    /**
     * 抽事件卡
     * 复用 DrawAlgorithm 权重桶算法，事件卡独立保底计数器（key 加 event: 前缀）
     *
     * 事件卡保底规则（简化版）：
     * - 连续9次未出珍品（rarity=2），第10抽必出珍品
     * - 珍品保底优先级高于普通概率
     *
     * @param userId 用户ID
     * @return 抽中的事件卡信息
     */
    @Transactional
    public DrawEventResult drawEventCard(Long userId) {
        String lockKey = "event:draw:lock:user:" + userId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(400, "抽卡过于频繁，请稍后");
            }

            // 手牌上限检查：事件卡最多3张
            int eventCardCount = userEventCardMapper.selectCount(
                    new LambdaQueryWrapper<UserEventCard>()
                            .eq(UserEventCard::getUserId, userId)
                            .eq(UserEventCard::getCardType, "event")
            );
            if (eventCardCount >= EVENT_CARD_LIMIT) {
                throw BusinessException.EVENT_CARD_LIMIT_REACHED;
            }

            // 获取事件卡保底状态（独立 key）
            EventGuaranteeState state = getEventGuaranteeState(userId);

            // 执行加权随机抽卡
            EventCard card = executeEventDraw(state);

            // 更新保底状态
            updateEventGuaranteeState(userId, state, card.getWeight());

            // 记录抽卡
            log.info("抽事件卡: userId={}, cardId={}, title={}", userId, card.getId(), card.getTitle());

            return DrawEventResult.builder()
                    .cardId(card.getId())
                    .cardNo(card.getCardNo())
                    .title(card.getTitle())
                    .dynasty(card.getDynasty())
                    .location(card.getLocation())
                    .description(card.getDescription())
                    .era(card.getEra())
                    .isGuaranteedRare(card.getWeight() >= 100)
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

    private static final String EVENT_GUARANTEE_KEY = "event:card:guarantee:user:%d";
    private static final long EVENT_GUARANTEE_TTL = 7 * 24 * 3600L;
    private static final int EVENT_CARD_LIMIT = 3;

    /**
     * 事件卡保底状态
     */
    private static class EventGuaranteeState {
        int consecutiveNonRare; // 连续未出珍品（weight>=100）次数
    }

    /**
     * 获取事件卡保底状态
     */
    private EventGuaranteeState getEventGuaranteeState(Long userId) {
        String key = String.format(EVENT_GUARANTEE_KEY, userId);
        String json = redissonClient.getBucket(key).get();
        if (json == null) {
            return new EventGuaranteeState();
        }
        try {
            EventGuaranteeState state = new EventGuaranteeState();
            state.consecutiveNonRare = Integer.parseInt(json.trim());
            return state;
        } catch (Exception e) {
            log.error("解析事件卡保底状态失败: userId={}", userId, e);
            return new EventGuaranteeState();
        }
    }

    /**
     * 更新事件卡保底状态
     * weight >= 100 视为珍品（核心事件）
     */
    private void updateEventGuaranteeState(Long userId, EventGuaranteeState state, int weight) {
        if (weight < 100) {
            state.consecutiveNonRare++;
        } else {
            state.consecutiveNonRare = 0;
        }
        String key = String.format(EVENT_GUARANTEE_KEY, userId);
        redissonClient.getBucket(key).set(String.valueOf(state.consecutiveNonRare),
                java.util.concurrent.TimeUnit.SECONDS, EVENT_GUARANTEE_TTL);
        log.info("事件卡保底状态更新: userId={}, consecutiveNonRare={}, weight={}",
                userId, state.consecutiveNonRare, weight);
    }

    /**
     * 执行事件卡加权随机抽卡
     * 保底：连续9次未出珍品，第10抽必出珍品（weight >= 100）
     */
    private EventCard executeEventDraw(EventGuaranteeState state) {
        // 检查保底
        if (state.consecutiveNonRare >= 9) {
            log.info("触发事件卡珍品保底: consecutiveNonRare={}", state.consecutiveNonRare);
            return selectEventCardByWeight(true);
        }
        // 加权随机
        List<EventCard> allCards = eventCardMapper.selectList(null);
        if (allCards == null || allCards.isEmpty()) {
            log.warn("事件卡表为空，使用 Mock 数据");
            return buildMockEventCard();
        }
        // 权重桶算法
        int totalWeight = allCards.stream().mapToInt(EventCard::getWeight).sum();
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (EventCard card : allCards) {
            cumulative += card.getWeight();
            if (roll < cumulative) {
                return card;
            }
        }
        return allCards.get(allCards.size() - 1);
    }

    private EventCard selectEventCardByWeight(boolean guaranteedRare) {
        List<EventCard> allCards = eventCardMapper.selectList(null);
        if (allCards == null || allCards.isEmpty()) {
            return buildMockEventCard();
        }
        if (!guaranteedRare) {
            int totalWeight = allCards.stream().mapToInt(EventCard::getWeight).sum();
            int roll = ThreadLocalRandom.current().nextInt(totalWeight);
            int cumulative = 0;
            for (EventCard card : allCards) {
                cumulative += card.getWeight();
                if (roll < cumulative) {
                    return card;
                }
            }
        }
        // 保底：只从珍品（weight>=100）中选
        List<EventCard> rareCards = allCards.stream()
                .filter(c -> c.getWeight() >= 100)
                .collect(Collectors.toList());
        if (rareCards.isEmpty()) {
            return allCards.get(ThreadLocalRandom.current().nextInt(allCards.size()));
        }
        int totalWeight = rareCards.stream().mapToInt(EventCard::getWeight).sum();
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (EventCard card : rareCards) {
            cumulative += card.getWeight();
            if (roll < cumulative) {
                return card;
            }
        }
        return rareCards.get(rareCards.size() - 1);
    }

    /**
     * Mock 事件卡（当数据库为空时使用）
     */
    private EventCard buildMockEventCard() {
        EventCard mock = new EventCard();
        mock.setId(1);
        mock.setCardNo("EV001");
        mock.setTitle("巨鹿·破釜沉舟");
        mock.setDynasty("秦");
        mock.setLocation("巨鹿");
        mock.setDescription("项羽率楚军渡河，凿沉船只，粉碎秦军主力");
        mock.setWeight(100);
        mock.setEra("秦末");
        return mock;
    }

    /**
     * 获取用户拥有的关键词卡列表
     */
    public List<UserCardVO> getUserCards(Long userId, Integer category, Integer rarity, int page, int size) {
        LambdaQueryWrapper<UserKeywordCard> wrapper = new LambdaQueryWrapper<UserKeywordCard>()
                .eq(UserKeywordCard::getUserId, userId)
                .orderByDesc(UserKeywordCard::getAcquiredAt);

        List<UserKeywordCard> userCards = userKeywordCardMapper.selectList(
                new LambdaQueryWrapper<UserKeywordCard>()
                        .eq(UserKeywordCard::getUserId, userId)
                        .orderByDesc(UserKeywordCard::getAcquiredAt));

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

    /**
     * S-13 获取关键词卡的当前共鸣值
     *
     * @param userId 用户ID
     * @param cardId 关键词卡ID（KeywordCard.id）
     * @return 当前共鸣次数，未找到返回0
     */
    public int getResonanceCount(Long userId, Long cardId) {
        UserKeywordCard userCard = userKeywordCardMapper.selectOne(
                new LambdaQueryWrapper<UserKeywordCard>()
                        .eq(UserKeywordCard::getUserId, userId)
                        .eq(UserKeywordCard::getCardId, cardId)
        );
        return userCard != null ? userCard.getResonanceCount() : 0;
    }

    // ========== C-12 陈卡回炉 ========== //

    private static final String RECYCLE_DAILY_KEY = "recycle:user:%d:%s";  // recycle:{userId}:{date}
    private static final String FREE_DRAW_REDIS_KEY = "free_draw:user:%d:%s"; // free_draw:{userId}:{date}

    /**
     * C-12 陈卡回炉
     * 将关键词卡投入墨池，返还1次免费抽卡机会，每日限1次
     *
     * @param userId 用户ID
     * @param cardId 关键词卡ID（user_keyword_card.id）
     * @return 回收结果，包含剩余免费抽卡次数
     */
    @Transactional
    public RecycleResult recycleCard(Long userId, Long cardId) {
        // 1. 校验卡牌归属：必须属于当前用户
        UserKeywordCard userCard = userKeywordCardMapper.selectOne(
                new LambdaQueryWrapper<UserKeywordCard>()
                        .eq(UserKeywordCard::getUserId, userId)
                        .eq(UserKeywordCard::getId, cardId)
        );
        if (userCard == null) {
            throw BusinessException.CARD_NOT_FOUND;
        }

        // 2. 每日限制1次：Redis key = recycle:{userId}:{date}
        String today = java.time.LocalDate.now().toString();
        String recycleKey = String.format(RECYCLE_DAILY_KEY, userId, today);
        Boolean alreadyRecycled = redissonClient.getBucket(recycleKey).get() != null;
        if (alreadyRecycled) {
            throw new BusinessException(400, "今日已回炉过一张卡，明日再来吧");
        }

        // 3. 删除用户卡牌记录（软删除）
        userKeywordCardMapper.deleteById(userCard.getId());
        log.info("[C-12] 用户回炉卡牌: userId={}, cardId={}", userId, cardId);

        // 4. 设置今日回炉标记（TTL 86400秒 = 24小时）
        redissonClient.getBucket(recycleKey).set("1",
                java.time.Duration.ofSeconds(86400));

        // 5. Redis incr free_draw:{userId}:{date}（返还1次免费抽卡机会）
        String freeDrawKey = String.format(FREE_DRAW_REDIS_KEY, userId, today);
        RAtomicLong freeDrawAtomic = redissonClient.getAtomicLong(freeDrawKey);
        long newFreeDraws = freeDrawAtomic.incrementAndGet();
        // 设置 TTL（次日自动失效）
        freeDrawAtomic.expire(java.time.Duration.ofSeconds(86400));

        // 6. 计算总可用免费次数 = DB剩余 + Redis额外
        int dbFreeDraws = userService.getDailyFreeDraws(userId);
        int totalFreeDraws = dbFreeDraws + (int) newFreeDraws;

        log.info("[C-12] 回炉成功: userId={}, cardId={}, newFreeDraws={}, total={}",
                userId, cardId, newFreeDraws, totalFreeDraws);

        return new RecycleResult(true, totalFreeDraws);
    }

    /**
     * 获取用户今日额外免费抽卡次数（来自陈卡回炉）
     */
    public int getExtraFreeDrawsFromRedis(Long userId) {
        String today = java.time.LocalDate.now().toString();
        String freeDrawKey = String.format(FREE_DRAW_REDIS_KEY, userId, today);
        Long val = redissonClient.getAtomicLong(freeDrawKey).get();
        return val != null ? val.intValue() : 0;
    }

    /**
     * C-12 回收结果
     */
    public record RecycleResult(boolean success, int freeDrawsRemaining) {}

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

    // ========== 墨迹占卜（今日运势）========== //

    /**
     * 运势文案库（从 classpath:fortune.json 加载，失败时 fallback 到内存）
     */
    private List<FortuneEntry> fortuneLibrary;

    /**
     * C-08: 从 fortune.json 加载运势文案，失败时使用硬编码兜底
     */
    @PostConstruct
    public void loadFortuneLibrary() {
        try {
            Resource resource = resourceLoader.getResource("classpath:fortune.json");
            fortuneLibrary = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<List<FortuneEntry>>() {}
            );
            log.info("[C-08] 运势文案加载成功，共 {} 条", fortuneLibrary.size());
        } catch (IOException e) {
            log.warn("[C-08] 运势文案加载失败，使用硬编码兜底: {}", e.getMessage());
            fortuneLibrary = getDefaultFortuneLibrary();
        }
    }

    /**
     * 硬编码兜底运势文案（与 fortune.json 保持同步，共34条）
     */
    private List<FortuneEntry> getDefaultFortuneLibrary() {
        return List.of(
                new FortuneEntry("今日墨色偏青，似有旧物来寻。", "器物"),
                new FortuneEntry("池中落了一片桂花，或有故人将至。", "职人"),
                new FortuneEntry("墨浓欲凝，宜静待时机。", "称谓"),
                new FortuneEntry("池水微漾，珍奇隐于其下。", "器物"),
                new FortuneEntry("砚中墨浅，然大机缘将至。", "风物"),
                new FortuneEntry("窗外有风过堂，今日宜得一奇字。", "称谓"),
                new FortuneEntry("墨迹未干，池底似有微光透出。", "器物"),
                new FortuneEntry("月色入水，宜候一纸良缘。", "情绪"),
                new FortuneEntry("落笔之处，香气隐隐似曾相识。", "风物"),
                new FortuneEntry("墨香四溢，今夜当有巧遇。", "职人"),
                new FortuneEntry("砚边微尘聚散，缘分暗中牵引。", "情绪"),
                new FortuneEntry("池面初平，有舟自远处来。", "风物"),
                new FortuneEntry("墨色渐淡，有人在记忆深处等你。", "称谓"),
                new FortuneEntry("笔尖微颤，今日宜书写新章。", "器物"),
                new FortuneEntry("水墨交融时，最宜邂逅故人。", "职人"),
                new FortuneEntry("砚池无波，心事却起涟漪。", "情绪"),
                new FortuneEntry("一缕墨烟飘散，暗示远行人将归。", "称谓"),
                new FortuneEntry("笔搁砚台，今日宜静不宜动。", "风物"),
                new FortuneEntry("墨汁浓稠如心事，待人解其中味。", "器物"),
                new FortuneEntry("池畔柳絮飞，今日宜别离亦宜重逢。", "情绪"),
                new FortuneEntry("砚中墨色温润如玉，宜缓缓书之。", "职人"),
                new FortuneEntry("落墨无声，却惊动池底游鱼。", "风物"),
                new FortuneEntry("墨香与茶香相融，今日宜会友。", "称谓"),
                new FortuneEntry("笔锋一转，暗藏一段未了情。", "器物"),
                new FortuneEntry("池水映月光，有人倚栏独望。", "情绪"),
                new FortuneEntry("墨点如星，今日宜思念旧友。", "职人"),
                new FortuneEntry("砚台微温，暗示前方有暖意。", "风物"),
                new FortuneEntry("笔走龙蛇处，今日当有奇遇。", "器物"),
                new FortuneEntry("墨干复又润，缘分几番轮回。", "称谓"),
                new FortuneEntry("池边独坐，风送故人消息。", "情绪"),
                new FortuneEntry("墨色如旧年，情绪却已不同。", "风物"),
                new FortuneEntry("一纸空白，候谁来落笔。", "器物"),
                new FortuneEntry("砚边寒露起，今日宜收敛心神。", "职人"),
                new FortuneEntry("墨迹斑驳，似一段被遗忘的往事。", "称谓")
        );
    }

    /**
     * 根据当天日期 + 用户ID 生成确定性运势
     * 使用日期作为seed，同一用户同一天看到的运势相同
     */
    public FortuneResult getFortune(Long userId) {
        int today = java.time.LocalDate.now().getYear() * 10000
                + java.time.LocalDate.now().getMonthValue() * 100
                + java.time.LocalDate.now().getDayOfMonth();

        // 用日期 seed + 用户 ID 混合哈希，确保不同用户、不同日期结果不同
        int hash = Integer.hashCode(today) ^ Long.hashCode(userId);
        int index = Math.abs(hash) % fortuneLibrary.size();

        FortuneEntry entry = fortuneLibrary.get(index);
        return new FortuneResult(entry.fortune, entry.hint);
    }

    public record FortuneEntry(String fortune, String hint) {}

    public record FortuneResult(String fortune, String hint) {}

    // ========== 事件卡抽卡结果 ========== //

    @Data
    @lombok.Builder
    public static class DrawEventResult {
        private Integer cardId;
        private String cardNo;
        private String title;
        private String dynasty;
        private String location;
        private String description;
        private String era;
        private boolean isGuaranteedRare;
    }

    // ========== P-01 组合判词生成 ========== //

    /**
     * P-01 组合判词生成
     * 根据选中的3张关键词卡+1事件卡，调用 AI 生成一句古文判词（20字以内）
     */
    public PreviewResult generatePreviewJudgment(List<Long> keywordIds, Integer eventId) {
        // 查询关键词卡详情
        List<String> keywordNames = new ArrayList<>();
        if (keywordIds != null && !keywordIds.isEmpty()) {
            List<KeywordCard> cards = keywordCardMapper.selectBatchIds(keywordIds);
            keywordNames = cards.stream()
                    .sorted(Comparator.comparingInt(id -> keywordIds.indexOf(id.getId())))
                    .map(KeywordCard::getName)
                    .collect(Collectors.toList());
        }

        // 查询事件卡详情
        String eventTitle = "";
        if (eventId != null) {
            EventCard eventCard = eventCardMapper.selectById(eventId);
            if (eventCard != null) {
                eventTitle = eventCard.getTitle();
            }
        }

        // 构造 AI prompt（说书人 Agent 轻量 prompt）
        String keywordDesc = keywordNames.isEmpty() ? "无" : String.join("、", keywordNames);
        String prompt = String.format("""
                三张关键词：%s，历史事件：%s。
                请用一句判词（20字以内）暗示这组卡可能产生的故事，用古文口吻。
                只返回一句古文判词，不要任何解释，不要加引号，不要用括号。
                """,
                keywordDesc,
                eventTitle.isEmpty() ? "无" : eventTitle);

        try {
            String judgment = aiClient.callSync(
                    "你是一位古代判官，擅长用古文写判词。", prompt);
            // 清洗返回内容：去掉可能的引号、换行、前后空白
            judgment = judgment.trim()
                    .replaceAll("^[\"'「『\\[]+", "")
                    .replaceAll("[\"'」』\\]]+$", "");
            // 限制字数（要求20字以内）
            if (judgment.length() > 20) {
                judgment = judgment.substring(0, 20);
            }
            // 空结果兜底
            if (judgment.isBlank()) {
                judgment = "墨中藏命，缘起无形。";
            }
            log.info("组合判词生成成功: keywordIds={}, eventId={}, judgment={}",
                    keywordIds, eventId, judgment);
            return new PreviewResult(judgment);
        } catch (Exception e) {
            log.warn("组合判词生成失败，降级为兜底文案: keywordIds={}, eventId={}, error={}",
                    keywordIds, eventId, e.getMessage());
            return new PreviewResult("墨中藏命，缘起无形。");
        }
    }

    /**
     * 组合判词预览请求
     */
    public record PreviewRequest(List<Long> keywordIds, Integer eventId) {}

    /**
     * 组合判词预览结果
     */
    public record PreviewResult(String judgment) {}

    // ========== C-11 墨香渐淡（时间衰减）每日零点执行 ==========

    /**
     * C-11 墨香渐淡（时间衰减）
     * 每日零点（0:00）执行，扫描所有用户关键词卡的墨香值，
     * 将 ink_fragrance = MAX(0, ink_fragrance - 1)
     * 模拟"墨香随时间流逝而渐淡"的游戏体验。
     *
     * 注意：墨香值存储在 user_keyword_card 表，使用 userKeywordCardMapper。
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void decayInkFragranceDaily() {
        log.info("[C-11] 墨香渐淡定时任务开始执行");

        // 墨香值最小为0，衰减后不低于0；只更新 ink_fragrance > 0 的记录
        int updated = userKeywordCardMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserKeywordCard>()
                        .setSql("ink_fragrance = GREATEST(0, ink_fragrance - 1)")
                        .gt(UserKeywordCard::getInkFragrance, 0)
        );
        log.info("[C-11] 墨香渐淡定时任务完成，共衰减 {} 张卡牌的墨香值", updated);
    }
}
