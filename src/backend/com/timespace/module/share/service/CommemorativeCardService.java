package com.timespace.module.share.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.timespace.module.share.entity.CommemorativeCard;
import com.timespace.module.share.mapper.CommemorativeCardMapper;
import com.timespace.module.story.entity.Story;
import com.timespace.module.story.entity.StoryManuscript;
import com.timespace.module.story.mapper.StoryMapper;
import com.timespace.module.story.mapper.StoryManuscriptMapper;
import com.timespace.module.user.entity.User;
import com.timespace.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 合券纪念卡服务
 * 生成并管理每次合券后产生的限定纪念卡
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommemorativeCardService extends ServiceImpl<CommemorativeCardMapper, CommemorativeCard> {

    private final CommemorativeCardMapper commemorativeCardMapper;
    private final StoryMapper storyMapper;
    private final StoryManuscriptMapper storyManuscriptMapper;
    private final UserMapper userMapper;

    /** 结局类型 → 专属印记（四字吉语）映射表 */
    private static final Map<String, String[]> ENDING_MARKS = Map.ofEntries(
            Map.entry("功成名就", new String[]{"金玉满堂", "凤鸣朝阳", "前程似锦", "富贵荣华"}),
            Map.entry("归隐山林", new String[]{"清风明月", "林泉放鹤", "山水逍遥", "云淡风轻"}),
            Map.entry("悲剧收场", new String[]{"梦觉黄粱", "人去楼空", "曲终人散", "浮生若梦"}),
            Map.entry("爱情圆满", new String[]{"执子之手", "比翼连枝", "花开并蒂", "琴瑟和鸣"}),
            Map.entry("友情长存", new String[]{"天涯比邻", "莫逆之交", "肝胆相照", "风雨同舟"}),
            Map.entry("宿命难逃", new String[]{"因果轮回", "命中注定", "天数难违", "因果注定"}),
            Map.entry("意外转折", new String[]{"柳暗花明", "绝处逢生", "峰回路转", "否极泰来"}),
            Map.entry("平淡是真", new String[]{"岁月静好", "细水长流", "平常是真", "现世安稳"}),
            Map.entry("未知", new String[]{"缘定三生", "浮生若梦", "时光如寄", "素昧平生"})
    );

    /** 所有吉语池（当无法确定类型时使用） */
    private static final String[] UNIVERSAL_MARKS = {
            "天涯比邻", "素昧平生", "缘定三生", "时光如寄",
            "浮生若梦", "沧海桑田", "白首如新", "倾盖如故"
    };

    /**
     * 生成合券纪念卡
     *
     * @param storyId    故事ID
     * @param user1Id    用户1 ID（分享者）
     * @param user2Id    用户2 ID（合券者）
     * @return 生成的纪念卡
     */
    @Transactional
    public CommemorativeCard generateCommemorativeCard(Long storyId, Long user1Id, Long user2Id) {
        // 1. 获取故事信息
        Story story = storyMapper.selectById(storyId);
        if (story == null) {
            log.warn("[CommemorativeCard] Story not found: storyId={}", storyId);
            return null;
        }

        // 2. 获取用户信息
        User user1 = user1Id != null ? userMapper.selectById(user1Id) : null;
        User user2 = user2Id != null ? userMapper.selectById(user2Id) : null;

        // 3. 获取手稿信息，推断结局类型
        String endingType = inferEndingType(story);
        String exclusiveMark = selectExclusiveMark(endingType);

        // 4. 生成纪念卡编号
        String cardNo = generateCardNo();

        // 5. 创建纪念卡记录
        CommemorativeCard card = new CommemorativeCard()
                .setCardNo(cardNo)
                .setStoryId(storyId)
                .setShareCode("") // 由调用方补充
                .setStoryTitle(story.getTitle() != null ? story.getTitle() : "时光旧事")
                .setUser1OpenId(user1 != null ? user1.getOpenId() : "")
                .setUser1Nickname(user1 != null ? user1.getNickname() : "时光旅人")
                .setUser2OpenId(user2 != null ? user2.getOpenId() : "")
                .setUser2Nickname(user2 != null ? user2.getNickname() : "陌路相逢")
                .setEndingType(endingType)
                .setExclusiveMark(exclusiveMark)
                .setJointTime(LocalDateTime.now())
                .setCreatedAt(LocalDateTime.now());

        commemorativeCardMapper.insert(card);

        log.info("[CommemorativeCard] Generated: cardNo={}, storyId={}, endingType={}, exclusiveMark={}",
                cardNo, storyId, endingType, exclusiveMark);

        return card;
    }

    /**
     * 根据故事信息推断结局类型
     */
    private String inferEndingType(Story story) {
        // 优先使用故事自身的 endingType 字段（如有）
        if (story.getEndingType() != null && !story.getEndingType().isEmpty()) {
            return story.getEndingType();
        }

        // 通过历史偏离度推断
        Integer deviation = story.getHistoryDeviation();
        if (deviation != null) {
            if (deviation >= 80) {
                return "意外转折"; // 高度偏离，峰回路转
            } else if (deviation <= 20) {
                return "宿命难逃"; // 高度吻合历史轨迹，宿命感强
            }
        }

        // 通过状态推断
        Integer status = story.getStatus();
        if (status != null && status == 3) {
            return "平淡是真"; // 已归档，归于平淡
        }

        // 默认返回"未知"，会从通用吉语池中选择
        return "未知";
    }

    /**
     * 根据结局类型选择专属印记
     */
    private String selectExclusiveMark(String endingType) {
        String[] marks = ENDING_MARKS.getOrDefault(endingType, UNIVERSAL_MARKS);
        return marks[ThreadLocalRandom.current().nextInt(marks.length)];
    }

    /**
     * 生成纪念卡编号：CM-YYYYMMDD-XXXXXX
     */
    private String generateCardNo() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int random = ThreadLocalRandom.current().nextInt(100000, 999999);
        return String.format("CM-%s-%d", dateStr, random);
    }

    /**
     * 根据纪念卡ID获取详情
     */
    public CommemorativeCardVO getCardById(Long cardId) {
        CommemorativeCard card = commemorativeCardMapper.selectById(cardId);
        if (card == null) {
            return null;
        }
        return toVO(card);
    }

    /**
     * 根据纪念卡编号获取详情
     */
    public CommemorativeCardVO getCardByCardNo(String cardNo) {
        CommemorativeCard card = commemorativeCardMapper.selectOne(
                new LambdaQueryWrapper<CommemorativeCard>()
                        .eq(CommemorativeCard::getCardNo, cardNo)
                        .last("limit 1")
        );
        if (card == null) {
            return null;
        }
        return toVO(card);
    }

    /**
     * 获取用户的所有纪念卡（作为分享者或合券者）
     */
    public List<CommemorativeCardVO> getCardsByOpenId(String openId) {
        List<CommemorativeCard> cards = commemorativeCardMapper.selectList(
                new LambdaQueryWrapper<CommemorativeCard>()
                        .eq(CommemorativeCard::getUser1OpenId, openId)
                        .or()
                        .eq(CommemorativeCard::getUser2OpenId, openId)
                        .orderByDesc(CommemorativeCard::getJointTime)
        );
        return cards.stream().map(this::toVO).toList();
    }

    private CommemorativeCardVO toVO(CommemorativeCard card) {
        return CommemorativeCardVO.builder()
                .id(card.getId())
                .cardNo(card.getCardNo())
                .storyId(card.getStoryId())
                .shareCode(card.getShareCode())
                .storyTitle(card.getStoryTitle())
                .user1OpenId(card.getUser1OpenId())
                .user1Nickname(card.getUser1Nickname())
                .user2OpenId(card.getUser2OpenId())
                .user2Nickname(card.getUser2Nickname())
                .endingType(card.getEndingType())
                .exclusiveMark(card.getExclusiveMark())
                .jointTime(card.getJointTime())
                .createdAt(card.getCreatedAt())
                .build();
    }

    // ========== DTO ==========

    @lombok.Data
    @lombok.Builder
    public static class CommemorativeCardVO {
        private Long id;
        private String cardNo;
        private Long storyId;
        private String shareCode;
        private String storyTitle;
        private String user1OpenId;
        private String user1Nickname;
        private String user2OpenId;
        private String user2Nickname;
        private String endingType;
        private String exclusiveMark;
        private LocalDateTime jointTime;
        private LocalDateTime createdAt;
    }
}
