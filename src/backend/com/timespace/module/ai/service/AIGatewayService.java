package com.timespace.module.ai.service;

import com.timespace.module.ai.agent.BaiguanAgent;
import com.timespace.module.ai.agent.JudgeAgent;
import com.timespace.module.ai.agent.StorytellerAgent;
import com.timespace.module.ai.client.AIClient;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.module.card.service.CardService;
import com.timespace.module.story.entity.Story;
import com.timespace.module.story.entity.StoryChapter;
import com.timespace.module.story.entity.StoryCharacter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI 网关服务
 * 统一管理所有 AI Agent 的调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIGatewayService {

    private final AIClient aiClient;
    private final StorytellerAgent storytellerAgent;
    private final JudgeAgent judgeAgent;
    private final BaiguanAgent baiguanAgent;
    private final CardService cardService;

    /**
     * 流式生成章节（WebSocket 推送）
     *
     * AI Agent 协作流程：
     *
     *  1. [判官] 评估用户选择
     *     - 分析选择的后果
     *     - 计算历史偏离度变化
     *     - 更新配角命运值
     *     - 计算关键词共鸣
     *     - 生成涟漪效果
     *
     *  2. [说书人] 生成下一章节
     *     - 场景描写（200字）
     *     - 融入关键词
     *     - 自然过渡
     *     - 流式推送前端
     *
     *  3. [判官] 生成下一章选项
     *     - 生成3个两难选项
     *     - 每个选项附带隐藏暗示
     *     - 选项之间价值取向不同
     *
     *  4. [掌眼] 质量检查（内置）
     *     - 过滤AI腔词汇
     *     - 检查关键词融入率
     *
     *  5. [卡牌服务] 更新共鸣
     *     - 增加相关关键词卡的共鸣次数
     *     - 增加相关关键词卡的墨香值
     *
     * @param story 故事元信息
     * @param currentChapter 当前章节
     * @param selectedOption 用户选择的选项
     * @param keywords 关键词卡列表
     * @param characters 配角列表
     * @param onChunk 流式回调（WebSocket推送）
     * @return 下一章节
     */
    public StoryChapter generateNextChapter(Story story,
                                            StoryChapter currentChapter,
                                            StoryChapter.Option selectedOption,
                                            List<KeywordCard> keywords,
                                            List<StoryCharacter> characters,
                                            Consumer<String> onChunk) {
        log.info("AI协作生成下一章: storyId={}, chapterNo={}",
                story.getId(), currentChapter.getChapterNo() + 1);

        // ====== 1. 判官评估选择 ======
        JudgeAgent.EvaluationResult evaluation = judgeAgent.evaluate(
                story, currentChapter, selectedOption, keywords, characters);

        log.info("判官评估完成: deviationChange={}, newDeviation={}, judgment={}",
                evaluation.getDeviationChange(), evaluation.getNewDeviation(),
                evaluation.getJudgment());

        // ====== 2. 更新故事上下文（配角命运、偏离度、关键词共鸣）======
        updateStoryContext(story, evaluation, characters);

        // ====== 3. 说书人生成下一章（流式）======
        StoryChapter nextChapter = storytellerAgent.generateChapter(
                story,
                currentChapter.getChapterNo() + 1,
                selectedOption,
                keywords,
                characters,
                onChunk  // 流式推送
        );

        // 将判官判词存入章节（供前端展示）
        nextChapter.setChapterComment(evaluation.getJudgment());

        // ====== 4. 判官生成选项（已在生成章节时一并生成）======
        // 选项已包含在 nextChapter.options 中

        // ====== 5. 更新关键词卡共鸣 ======
        updateKeywordResonance(story, keywords, evaluation);

        return nextChapter;
    }

    /**
     * 生成第一章节（无前置选择）
     *
     * @param story 故事元信息
     * @param keywords 关键词卡列表
     * @param characters 配角列表
     * @param onChunk 流式回调
     */
    public StoryChapter generateFirstChapter(Story story,
                                              List<KeywordCard> keywords,
                                              List<StoryCharacter> characters,
                                              Consumer<String> onChunk) {
        log.info("AI生成第一章节: storyId={}", story.getId());

        // 说书人生成第一章节
        return storytellerAgent.generateChapter(
                story,
                1,
                null,
                keywords,
                characters,
                onChunk
        );
    }

    /**
     * 判官评估选择（仅评估，不生成章节）
     * 用于 submitChoiceAndStream 流程中，先推送判官结果
     */
    public JudgeAgent.EvaluationResult evaluateChoice(Story story,
                                                       StoryChapter currentChapter,
                                                       StoryChapter.Option selectedOption,
                                                       List<KeywordCard> keywords,
                                                       List<StoryCharacter> characters) {
        return judgeAgent.evaluate(story, currentChapter, selectedOption, keywords, characters);
    }

    /**
     * 生成手稿
     */
    public String generateManuscript(Story story,
                                      List<StoryChapter> chapters,
                                      List<KeywordCard> keywords,
                                      List<StoryCharacter> characters) {
        log.info("AI生成手稿: storyId={}, chapters={}", story.getId(), chapters.size());
        return storytellerAgent.generateManuscript(story, chapters, keywords, characters);
    }

    private void updateStoryContext(Story story,
                                     JudgeAgent.EvaluationResult evaluation,
                                     List<StoryCharacter> characters) {
        // 更新偏离度
        story.setHistoryDeviation(evaluation.getNewDeviation());

        // 更新配角命运
        for (Map.Entry<String, JudgeAgent.CharacterFateChange> entry :
                evaluation.getCharacterFateChanges().entrySet()) {
            String charName = entry.getKey();
            JudgeAgent.CharacterFateChange change = entry.getValue();
            // 找出对应配角并更新命运值
            characters.stream()
                    .filter(c -> c.getName().equals(charName))
                    .findFirst()
                    .ifPresent(c -> c.setFateValue(change.getNewValue()));
        }

        log.info("故事上下文更新: newDeviation={}, characterChanges={}",
                evaluation.getNewDeviation(),
                evaluation.getCharacterFateChanges().size());
    }

    private void updateKeywordResonance(Story story,
                                        List<KeywordCard> keywords,
                                        JudgeAgent.EvaluationResult evaluation) {
        for (Map.Entry<Long, Integer> entry : evaluation.getKeywordResonance().entrySet()) {
            Long cardId = entry.getKey();
            Integer resonance = entry.getValue();
            if (resonance > 0) {
                // 增加共鸣次数
                try {
                    cardService.increaseResonance(story.getUserId(), cardId);
                } catch (Exception e) {
                    log.warn("关键词卡共鸣更新失败: cardId={}, error={}", cardId, e.getMessage());
                }
                log.info("关键词卡共鸣更新: cardId={}, resonance={}", cardId, resonance);
            }
        }
    }

    private List<KeywordCard> getKeywordCards(Story story) {
        // 实际项目中根据 story.keywordCardIds 查询数据库
        // 这里返回空列表作为 fallback
        if (story.getKeywordCardIds() == null || story.getKeywordCardIds().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            // TODO: 通过 CardService 查询
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("获取关键词卡失败: storyId={}, error={}", story.getId(), e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<StoryCharacter> initializeCharacters(Story story) {
        // 实际项目中根据事件卡初始化配角
        return new ArrayList<>();
    }
}
