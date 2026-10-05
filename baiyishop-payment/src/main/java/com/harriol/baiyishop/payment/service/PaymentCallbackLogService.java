package com.harriol.baiyishop.payment.service;

import com.harriol.baiyishop.payment.entity.PaymentCallbackLog;
import com.harriol.baiyishop.payment.mapper.PaymentCallbackLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 回调日志写入（docs/database.md 7.2）。
 * <p>**独立事务（REQUIRES_NEW）**是关键：
 * <ul>
 *   <li>验签失败 / 金额不一致时业务要抛异常回滚，但审计记录必须留下（REQ-802-1 要能查伪造回调）</li>
 *   <li>回调幂等靠 uk_channel_trade 唯一索引，日志立即提交，并发重复回调才能被同时挡住</li>
 * </ul>
 */
@Service
public class PaymentCallbackLogService {

    private static final int RAW_BODY_LIMIT = 1000;

    private final PaymentCallbackLogMapper callbackLogMapper;

    public PaymentCallbackLogService(PaymentCallbackLogMapper callbackLogMapper) {
        this.callbackLogMapper = callbackLogMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PaymentCallbackLog record(String channel, String tradeNo, String paymentNo,
                                     String rawBody, boolean signVerified, String result) {
        PaymentCallbackLog callbackLog = new PaymentCallbackLog();
        callbackLog.setChannel(channel.toUpperCase());
        callbackLog.setChannelTradeNo(tradeNo);
        callbackLog.setPaymentNo(paymentNo);
        callbackLog.setRawBody(mask(rawBody));
        callbackLog.setSignVerified(signVerified ? 1 : 0);
        callbackLog.setProcessResult(result);
        callbackLogMapper.insert(callbackLog);
        return callbackLog;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void finish(Long id, String result) {
        PaymentCallbackLog update = new PaymentCallbackLog();
        update.setId(id);
        update.setProcessResult(result);
        callbackLogMapper.updateById(update);
    }

    /** 报文可能很长，只留前若干字符；密钥从不入日志、不入库（REQ-802-5） */
    private String mask(String rawBody) {
        if (rawBody == null) {
            return null;
        }
        return rawBody.length() <= RAW_BODY_LIMIT ? rawBody : rawBody.substring(0, RAW_BODY_LIMIT) + "...(truncated)";
    }
}
