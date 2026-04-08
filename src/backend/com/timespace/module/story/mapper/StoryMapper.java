package com.timespace.module.story.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.timespace.module.story.entity.Story;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StoryMapper extends BaseMapper<Story> {
}
