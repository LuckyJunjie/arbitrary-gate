package com.timespace.module.card.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.timespace.module.card.entity.UserKeywordCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserKeywordCardMapper extends BaseMapper<UserKeywordCard> {

    /**
     * 统计用户持有的关键词卡数量
     */
    @Select("SELECT COUNT(*) FROM user_keyword_card WHERE user_id = #{userId}")
    int countByUserId(Long userId);
}
