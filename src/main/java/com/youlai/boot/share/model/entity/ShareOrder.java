package com.youlai.boot.share.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.boot.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 社群订单实体
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@EqualsAndHashCode(callSuper = false)
@TableName("share_order")
@Data
public class ShareOrder extends BaseEntity {

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 国家ID
     */
    private Integer countryId;

    /**
     * 国家名称
     */
    private String countryName;

    /**
     * 是否携带区号：0-否，1-是
     */
    private Integer hasAreaCode;

    /**
     * 预约启动时间（北京时间）
     */
    private LocalDateTime scheduledTime;

    /**
     * 订单状态：0-待发送，1-发送中，2-已完成，3-发送失败，4-已取消
     */
    private Integer status;

    /**
     * 发送成功数量
     */
    private Integer successCount;

    /**
     * 发送失败数量
     */
    private Integer failCount;

    /**
     * 总数
     */
    private Integer totalCount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 上报短信数量
     */
    private Integer reportCount;

    /**
     * 付费短信数量
     */
    private Integer paidCount;

    /**
     * 取消短信数量
     */
    private Integer cancelCount;

}
