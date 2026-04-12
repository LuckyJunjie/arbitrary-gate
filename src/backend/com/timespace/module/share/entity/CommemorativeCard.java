package com.timespace.module.share.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("story_commemorative_card")
public class CommemorativeCard {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 纪念卡编号，格式：CM-YYYYMMDD-XXXXXX */
    private String cardNo;

    /** 关联故事ID */
    private Long storyId;

    /** 来源分享码 */
    private String shareCode;

    /** 故事标题 */
    private String storyTitle;

    /** 用户1 OpenID（分享者） */
    private String user1OpenId;

    /** 用户1昵称 */
    private String user1Nickname;

    /** 用户2 OpenID（合券者） */
    private String user2OpenId;

    /** 用户2昵称 */
    private String user2Nickname;

    /** 结局类型 */
    private String endingType;

    /** 专属印记（四字吉语） */
    private String exclusiveMark;

    /** 合券时间 */
    private LocalDateTime jointTime;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
