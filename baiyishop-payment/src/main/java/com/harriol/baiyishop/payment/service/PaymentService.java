package com.harriol.baiyishop.payment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.payment.channel.AbstractMockPaymentChannel;
import com.harriol.baiyishop.payment.channel.ChannelCallback;
import com.harriol.baiyishop.payment.channel.PaymentChannel;
import com.harriol.baiyishop.payment.channel.PaymentChannelRegistry;
import com.harriol.baiyishop.payment.client.OrderClient;
import com.harriol.baiyishop.payment.config.PaymentProperties;
import com.harriol.baiyishop.payment.dto.CreatePaymentRequest;
import com.harriol.baiyishop.payment.dto.PayableOrderView;
import com.harriol.baiyishop.payment.dto.PaymentCreateView;
import com.harriol.baiyishop.payment.dto.PaymentView;
import com.harriol.baiyishop.payment.entity.Payment;
import com.harriol.baiyishop.payment.mapper.PaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 发起支付与查询（REQ-801、REQ-803）。
 * <p>三条规则：
 * <ol>
 *   <li>**只信订单**：状态、金额、归属都回查订单服务；金额不等直接拒（60003）</li>
 *   <li>**同订单只保留一张待支付单**：重复发起（前端重试、用户连点）返回同一支付单与同一套支付参数，
 *       不产生多张支付单，也就不会出现「一张订单被支付两次」</li>
 *   <li>**支付单过期即不可支付**：过期时间与订单的 15 分钟超时对齐，过期后让用户重新下单</li>
 * </ol>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String ORDER_STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final DateTimeFormatter PAYMENT_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentMapper paymentMapper;
    private final OrderClient orderClient;
    private final PaymentChannelRegistry channelRegistry;
    private final PaymentCallbackHandler callbackHandler;
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentMapper paymentMapper,
                          OrderClient orderClient,
                          PaymentChannelRegistry channelRegistry,
                          PaymentCallbackHandler callbackHandler,
                          PaymentProperties properties,
                          ObjectMapper objectMapper) {
        this.paymentMapper = paymentMapper;
        this.orderClient = orderClient;
        this.channelRegistry = channelRegistry;
        this.callbackHandler = callbackHandler;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 发起支付（REQ-801）：必须登录，且订单必须是自己的、待付款、金额一致 */
    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateView create(long userId, String requestId, CreatePaymentRequest request) {
        if (!StringUtils.hasText(requestId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少请求标识 X-Request-Id");
        }
        if (request == null || !StringUtils.hasText(request.orderNo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少订单号");
        }
        PaymentChannel channel = channelRegistry.of(request.channel());

        PayableOrderView order = orderClient.payable(request.orderNo());
        if (!order.userId().equals(userId)) {
            // 不暴露「订单存在但不属于你」，按订单不存在处理
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!ORDER_STATUS_PENDING_PAYMENT.equals(order.status())) {
            throw new BizException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }

        Payment pending = pendingPayment(order.orderNo(), userId);
        if (pending != null) {
            if (pending.getExpireAt() != null && pending.getExpireAt().isBefore(LocalDateTime.now())) {
                throw new BizException(ErrorCode.PAYMENT_NOT_ALLOWED, "支付单已过期，请重新下单");
            }
            log.info("复用待支付单 paymentNo={} orderNo={}", pending.getPaymentNo(), order.orderNo());
            return view(pending, channel);
        }

        Payment payment = new Payment();
        payment.setPaymentNo(nextPaymentNo());
        payment.setOrderNo(order.orderNo());
        payment.setUserId(userId);
        payment.setChannel(channel.code());
        payment.setAmount(order.payAmount());
        payment.setStatus(Payment.STATUS_PENDING);
        payment.setExpireAt(order.timeoutAt());
        paymentMapper.insert(payment);
        log.info("创建支付单 paymentNo={} orderNo={} channel={} amount={}", payment.getPaymentNo(),
                payment.getOrderNo(), payment.getChannel(), payment.getAmount());
        return view(payment, channel);
    }

    /** 查询支付结果（REQ-803）：回调丢失时前端可主动查询 */
    public PaymentView query(long userId, String paymentNo) {
        return PaymentView.from(requireOwned(userId, paymentNo));
    }

    /** 按订单查支付单（REQ-803）：取最近一张 */
    public PaymentView queryByOrder(long userId, String orderNo) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderNo, orderNo)
                .eq(Payment::getUserId, userId)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return PaymentView.from(payment);
    }

    /**
     * 模拟渠道的「一键支付」（仅本地联调，`baiyishop.payment.mock-pay-enabled` 控制）。
     * <p>它不是绕过回调的捷径：内部按渠道规则**签名后调用同一套回调处理**，
     * 验签、幂等、金额校验、事件投递全部照走，所以联调验证的就是真实链路。
     */
    public PaymentView mockPay(long userId, String paymentNo) {
        if (!properties.mockPayEnabled()) {
            throw new BizException(ErrorCode.PAYMENT_NOT_ALLOWED, "模拟支付未开启");
        }
        Payment payment = requireOwned(userId, paymentNo);
        if (Payment.STATUS_SUCCESS.equals(payment.getStatus())) {
            return PaymentView.from(payment);
        }
        AbstractMockPaymentChannel channel = channelRegistry.mockOf(payment.getChannel());
        ChannelCallback callback = new ChannelCallback(payment.getPaymentNo(),
                "MOCK" + payment.getPaymentNo(), payment.getAmount(), "SUCCESS");
        String rawBody = objectMapper.writeValueAsString(callback);
        callbackHandler.handle(payment.getChannel(), rawBody, channel.signForMock(rawBody));
        return PaymentView.from(requireOwned(userId, paymentNo));
    }

    private PaymentCreateView view(Payment payment, PaymentChannel channel) {
        return new PaymentCreateView(payment.getPaymentNo(), payment.getChannel(), payment.getAmount(),
                channel.payParams(payment), payment.getExpireAt());
    }

    private Payment pendingPayment(String orderNo, long userId) {
        return paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderNo, orderNo)
                .eq(Payment::getUserId, userId)
                .eq(Payment::getStatus, Payment.STATUS_PENDING)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
    }

    private Payment requireOwned(long userId, String paymentNo) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getPaymentNo, paymentNo)
                .eq(Payment::getUserId, userId));
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return payment;
    }

    /** 支付单号：PAY + yyyyMMddHHmmss + 6 位随机，22 位以内（数据库列宽 32） */
    private String nextPaymentNo() {
        return "PAY" + LocalDateTime.now().format(PAYMENT_NO_TIME)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
