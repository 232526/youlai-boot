package com.youlai.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 国家实体
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@EqualsAndHashCode(callSuper = false)
@TableName("countries")
@Data
public class Country implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
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
