package com.harriol.baiyishop.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 支付回调日志（docs/database.md 7.2、REQ-802）。
 * <p>{@code uk_channel_trade} 是回调幂等的落点：同一渠道同一流水号重复回调会撞唯一索引，
 * 服务按「已处理」返回成功。验签失败的记录同样落库（sign_verified = 0），便于排查伪造回调。
 */
@Getter
@Setter
@TableName("payment_callback_log")
public class PaymentCallbackLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String channel;

    private String channelTradeNo;

    private String paymentNo;

    /** 脱敏后的报文：不含密钥（REQ-802-5） */
    private String rawBody;

    private Integer signVerified;

    private String processResult;

    private LocalDateTime createdAt;
}
