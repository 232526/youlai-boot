package com.youlai.boot.share.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分享类型实体
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@TableName("share_type")
@Data
public class ShareType implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 国家名称
     */
    private String name;

    /**
     * 可否使用：1=可用，0=不可用
     */
    private Integer isEnabled;

}
