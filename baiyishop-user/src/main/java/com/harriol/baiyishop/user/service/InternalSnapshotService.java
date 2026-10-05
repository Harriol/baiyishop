package com.harriol.baiyishop.user.service;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.user.dto.AddressSnapshot;
import com.harriol.baiyishop.user.dto.AdminNameView;
import com.harriol.baiyishop.user.entity.Admin;
import com.harriol.baiyishop.user.entity.UserAddress;
import com.harriol.baiyishop.user.mapper.AdminMapper;
import com.harriol.baiyishop.user.mapper.UserAddressMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 供其他服务读取的快照（docs/api.md 第 6 章）。
 * <p>只读、不做令牌校验（网关拦截 /internal/**）：调用方是内部服务，身份由链路保证。
 * <p>这里不校验地址归属：归属由下单方（order-service）用快照里的 userId 判断 ——
 * 「地址属于谁」只有下单方知道当前用户是谁。
 */
@Service
public class InternalSnapshotService {

    private final UserAddressMapper addressMapper;
    private final AdminMapper adminMapper;

    public InternalSnapshotService(UserAddressMapper addressMapper, AdminMapper adminMapper) {
        this.addressMapper = addressMapper;
        this.adminMapper = adminMapper;
    }

    /** 地址快照；不存在（含已删除）抛 20006 */
    public AddressSnapshot address(long addressId) {
        UserAddress address = addressMapper.selectById(addressId);
        if (address == null) {
            throw new BizException(ErrorCode.USER_ADDRESS_NOT_FOUND);
        }
        return AddressSnapshot.from(address);
    }

    /**
     * 结算页默认地址：优先取设置了「默认」的那条，没有就取最新一条。
     * <p>返回 {@code null} 表示该用户还没有任何地址，前端引导去新建（不报错）。
     */
    public AddressSnapshot defaultAddress(long userId) {
        List<UserAddress> addresses = addressMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<UserAddress>lambdaQuery()
                        .eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getIsDefault)
                        .orderByDesc(UserAddress::getId)
                        .last("LIMIT 1"));
        return addresses.isEmpty() ? null : AddressSnapshot.from(addresses.get(0));
    }

    /** 管理员姓名；账号已删除时返回 unknown 而不是报错，避免影响备注这类非关键动作 */
    public AdminNameView admin(long adminId) {
        Admin admin = adminMapper.selectById(adminId);
        return new AdminNameView(adminId, nameOf(admin));
    }

    private String nameOf(Admin admin) {
        if (admin == null) {
            return "unknown";
        }
        return admin.getRealName() == null || admin.getRealName().isBlank()
                ? admin.getUsername() : admin.getRealName();
    }
}
