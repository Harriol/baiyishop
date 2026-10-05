package com.harriol.baiyishop.order.client;

import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.web.client.InternalApiClient;
import com.harriol.baiyishop.common.web.client.InternalApiClients;
import com.harriol.baiyishop.order.dto.AddressSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 用户域内部调用（docs/api.md 第 6 章）。
 * <p>地址与管理员姓名都是**快照读取**：读到之后复制进订单 / 备注，
 * 之后用户改地址、管理员改名都不影响历史订单。
 */
@Component
public class UserClient {

    private static final String SERVICE = "http://baiyishop-user";

    private final InternalApiClient client;

    public UserClient(InternalApiClients clients) {
        this.client = clients.client(SERVICE);
    }

    /** 地址快照；不存在（含已删除）返回 empty，由调用方翻译成 20006 */
    public Optional<AddressSnapshot> address(long addressId) {
        return client.getOptional("/internal/users/addresses/" + addressId,
                AddressSnapshot.class, ErrorCode.USER_ADDRESS_NOT_FOUND.getCode());
    }

    /** 管理员姓名（后台备注用）；查不到返回 unknown 而不是报错 */
    public String adminName(long adminId) {
        AdminName admin = client.get("/internal/users/admins/" + adminId, AdminName.class);
        return admin == null || admin.name() == null ? "unknown" : admin.name();
    }

    /** 结算页默认地址；用户还没有地址时返回 empty */
    public Optional<AddressSnapshot> defaultAddress(long userId) {
        return client.getOptional("/internal/users/addresses/default/" + userId, AddressSnapshot.class);
    }

    private record AdminName(Long id, String name) {
    }
}
