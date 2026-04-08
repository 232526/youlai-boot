package com.youlai.boot.share.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 社群订单状态枚举
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Getter
@AllArgsConstructor
public enum ShareOrderStatusEnum {

    /**
     * 待发送
     */
    PENDING(0, "待发送"),

    /**
     * 发送中
     */
    SENDING(1, "发送中"),

    /**
     * 已完成
     */
    COMPLETED(2, "已完成"),

    /**
     * 发送失败
     */
    FAILED(3, "发送失败"),

    /**
     * 已取消
     */
    CANCELLED(4, "已取消");

    private final Integer value;
    private final String desc;

    /**
     * 根据值获取枚举
     */
    public static ShareOrderStatusEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (ShareOrderStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }

}
