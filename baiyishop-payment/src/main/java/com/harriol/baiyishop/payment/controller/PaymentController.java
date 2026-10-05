package com.harriol.baiyishop.payment.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.context.UserContext;
import com.harriol.baiyishop.payment.channel.ChannelAck;
import com.harriol.baiyishop.payment.dto.CreatePaymentRequest;
import com.harriol.baiyishop.payment.dto.PaymentCreateView;
import com.harriol.baiyishop.payment.dto.PaymentView;
import com.harriol.baiyishop.payment.service.PaymentCallbackHandler;
import com.harriol.baiyishop.payment.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口（docs/api.md 4.8、REQ-801 ~ REQ-803）。
 * <p>回调接口是**公开**的（渠道服务器调用，靠验签而非令牌保护），
 * 其余接口都需要登录，且只能操作自己的支付单。
 * <p>回调的应答用渠道自己的报文形状（{@link ChannelAck}）而不是统一信封 ——
 * 渠道不认我们的信封，这里按真实网关的对接方式返回。
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final String SIGNATURE_HEADER = "X-Pay-Signature";

    private final PaymentService paymentService;
    private final PaymentCallbackHandler callbackHandler;

    public PaymentController(PaymentService paymentService, PaymentCallbackHandler callbackHandler) {
        this.paymentService = paymentService;
        this.callbackHandler = callbackHandler;
    }

    /** 发起支付：返回渠道支付参数，前端据此唤起支付（REQ-801） */
    @PostMapping
    public Result<PaymentCreateView> create(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody CreatePaymentRequest request) {
        return Result.ok(paymentService.create(UserContext.requireUserId(), requestId, request));
    }

    /** 渠道支付结果回调：先验签，再幂等，最后才动业务（REQ-802） */
    @PostMapping("/callback/{channel}")
    public ChannelAck callback(@PathVariable String channel,
                               @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
                               @RequestBody(required = false) String rawBody) {
        return callbackHandler.handle(channel, rawBody, signature);
    }

    /** 查询支付结果（REQ-803） */
    @GetMapping("/{paymentNo}")
    public Result<PaymentView> query(@PathVariable String paymentNo) {
        return Result.ok(paymentService.query(UserContext.requireUserId(), paymentNo));
    }

    /** 按订单查支付单（REQ-803） */
    @GetMapping("/by-order/{orderNo}")
    public Result<PaymentView> queryByOrder(@PathVariable String orderNo) {
        return Result.ok(paymentService.queryByOrder(UserContext.requireUserId(), orderNo));
    }

    /**
     * 模拟渠道的一键支付（仅本地联调）。
     * <p>内部签名后调用真实的回调处理，验签与幂等照走，便于在没有真实渠道时打通全链路。
     */
    @PostMapping("/{paymentNo}/mock-pay")
    public Result<PaymentView> mockPay(@PathVariable String paymentNo) {
        return Result.ok(paymentService.mockPay(UserContext.requireUserId(), paymentNo));
    }
}
