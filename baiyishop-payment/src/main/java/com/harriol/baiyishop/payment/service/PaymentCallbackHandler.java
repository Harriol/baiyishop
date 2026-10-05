package com.harriol.baiyishop.payment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.payment.channel.ChannelAck;
import com.harriol.baiyishop.payment.channel.ChannelCallback;
import com.harriol.baiyishop.payment.channel.PaymentChannel;
import com.harriol.baiyishop.payment.channel.PaymentChannelRegistry;
import com.harriol.baiyishop.payment.config.PaymentProperties;
import com.harriol.baiyishop.payment.dto.PaymentSuccessEvent;
import com.harriol.baiyishop.payment.entity.Payment;
import com.harriol.baiyishop.payment.entity.PaymentCallbackLog;
import com.harriol.baiyishop.payment.mapper.PaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 支付回调处理（REQ-802）。
 * <p>顺序刻意如此：**先验签，再幂等，最后才动业务**。理由：
 * <ol>
 *   <li>验签是唯一能挡住伪造回调的关口，必须排在最前（REQ-802-1）</li>
 *   <li>幂等落点是 payment_callback_log 的 uk_channel_trade 唯一索引：重复回调在这里被挡下，
 *       业务代码不需要再判断「是不是处理过了」（REQ-802-2）</li>
 *   <li>金额必须与支付单一致，防止渠道回调被伪造成小额支付（REQ-801 的 60003 保护）</li>
 * </ol>
 * <p>验签失败的报文同样落库（sign_verified = 0），便于排查伪造回调；但拿不到可信流水号，
 * 用报文哈希代替，保证同一份伪造报文只留一条记录、不刷屏。
 * <p>日志与报文都**不含密钥**（REQ-802-5）。
 */
@Service
public class PaymentCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackHandler.class);

    private static final String TAG_PAYMENT_SUCCESS = "PAYMENT_SUCCESS";
    private final PaymentMapper paymentMapper;
    private final PaymentCallbackLogService callbackLogService;
    private final PaymentOutboxService outboxService;
    private final PaymentChannelRegistry channelRegistry;
    private final PaymentProperties properties;

    public PaymentCallbackHandler(PaymentMapper paymentMapper,
                                  PaymentCallbackLogService callbackLogService,
                                  PaymentOutboxService outboxService,
                                  PaymentChannelRegistry channelRegistry,
                                  PaymentProperties properties) {
        this.paymentMapper = paymentMapper;
        this.callbackLogService = callbackLogService;
        this.outboxService = outboxService;
        this.channelRegistry = channelRegistry;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelAck handle(String channelCode, String rawBody, String signature) {
        PaymentChannel channel = channelRegistry.of(channelCode);

        ChannelCallback payload;
        try {
            payload = channel.verify(rawBody, signature);
        } catch (BizException ex) {
            callbackLogService.record(channelCode, unsignedTradeNo(rawBody), null, rawBody, false,
                    "验签失败(" + ex.getErrorCode().getCode() + ")");
            throw ex;
        }

        Payment payment = paymentMapper.selectOne(
                Wrappers.<Payment>lambdaQuery().eq(Payment::getPaymentNo, payload.paymentNo()));
        if (payment == null) {
            callbackLogService.record(channelCode, payload.channelTradeNo(), payload.paymentNo(), rawBody, true, "支付单不存在");
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (payload.amount() == null || !payment.getAmount().equals(payload.amount())) {
            callbackLogService.record(channelCode, payload.channelTradeNo(), payment.getPaymentNo(), rawBody, true,
                    "金额与支付单不一致");
            throw new BizException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        PaymentCallbackLog callbackLog;
        try {
            callbackLog = callbackLogService.record(channelCode, payload.channelTradeNo(),
                    payment.getPaymentNo(), rawBody, true, "已受理");
        } catch (DataIntegrityViolationException ex) {
            // uk_channel_trade 命中：同一渠道同一流水号重复回调，直接确认，不重复处理（REQ-802-2）
            log.info("重复回调已忽略 channel={} tradeNo={}", channelCode, payload.channelTradeNo());
            return ChannelAck.duplicate();
        }

        if (!payload.success()) {
            paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                    .eq(Payment::getPaymentNo, payment.getPaymentNo())
                    .eq(Payment::getStatus, Payment.STATUS_PENDING)
                    .set(Payment::getStatus, Payment.STATUS_FAILED)
                    .set(Payment::getChannelTradeNo, payload.channelTradeNo()));
            callbackLogService.finish(callbackLog.getId(), "渠道返回支付失败");
            return ChannelAck.ok();
        }

        LocalDateTime payTime = LocalDateTime.now();
        int rows = paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                .eq(Payment::getPaymentNo, payment.getPaymentNo())
                .eq(Payment::getStatus, Payment.STATUS_PENDING)
                .set(Payment::getStatus, Payment.STATUS_SUCCESS)
                .set(Payment::getChannelTradeNo, payload.channelTradeNo())
                .set(Payment::getPayTime, payTime));
        if (rows == 0) {
            // 支付单已是终态（并发回调或已处理过），按重复处理
            callbackLogService.finish(callbackLog.getId(), "支付单已终态，按重复回调处理");
            return ChannelAck.duplicate();
        }

        // 与「支付单置成功」同一本地事务：事务提交后由投递任务发给 order-service（REQ-802-3）
        outboxService.append(properties.paymentSuccessTopic(), TAG_PAYMENT_SUCCESS, payment.getOrderNo(),
                new PaymentSuccessEvent(payment.getPaymentNo(), payment.getOrderNo(), payment.getChannel(),
                        payload.channelTradeNo(), payment.getAmount(), payTime));
        callbackLogService.finish(callbackLog.getId(), "支付成功，已通知订单");
        log.info("支付成功 paymentNo={} orderNo={} channel={} tradeNo={}", payment.getPaymentNo(),
                payment.getOrderNo(), payment.getChannel(), payload.channelTradeNo());
        return ChannelAck.ok();
    }

    /** 验签失败时没有可信流水号：用报文哈希占位，保证重复投递的伪造报文只留一条记录 */
    private String unsignedTradeNo(String rawBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(digest.digest(
                    (rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8)));
            return "UNVERIFIED-" + hash.substring(0, 32);
        } catch (Exception ex) {
            return "UNVERIFIED-UNKNOWN";
        }
    }
}
