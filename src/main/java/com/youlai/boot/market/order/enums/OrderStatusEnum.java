package com.youlai.boot.market.order.enums;

import com.youlai.boot.common.base.IBaseEnum;
import lombok.Getter;

/**
 * 订单状态枚举
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Getter
public enum OrderStatusEnum implements IBaseEnum<Integer> {

    PENDING(0, "待发送"),
    SENDING(1, "发送中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "发送失败"),
    CANCELLED(4, "已取消");

    private final Integer value;
    private final String label;

    OrderStatusEnum(Integer value, String label) {
        this.value = value;
        this.label = label;
    }
}
