package com.harriol.baiyishop.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.user.dto.AddressRequest;
import com.harriol.baiyishop.user.dto.AddressResponse;
import com.harriol.baiyishop.user.entity.UserAddress;
import com.harriol.baiyishop.user.mapper.UserAddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址管理（REQ-103）。
 * <p>两条硬约束：
 * <ul>
 *   <li>只能操作自己的地址 —— 所有查询与写入都按当前登录用户过滤，不接受前端传 userId（NFR-03）</li>
 *   <li>同一用户最多一条默认地址 —— 设默认时先把其余置 0，同一事务内完成</li>
 * </ul>
 */
@Service
public class AddressService {

    private final UserAddressMapper addressMapper;

    public AddressService(UserAddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    /** 地址列表：默认地址排最前，其余按创建时间倒序 */
    public List<AddressResponse> list(long userId) {
        List<UserAddress> addresses = addressMapper.selectList(
                Wrappers.<UserAddress>lambdaQuery()
                        .eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getIsDefault)
                        .orderByDesc(UserAddress::getId));
        return addresses.stream().map(AddressResponse::from).toList();
    }

    @Transactional
    public AddressResponse create(long userId, AddressRequest request) {
        boolean firstAddress = addressMapper.selectCount(
                Wrappers.<UserAddress>lambdaQuery().eq(UserAddress::getUserId, userId)) == 0;
        // 第一条地址自动设为默认，避免下单页没有可选项
        boolean asDefault = Boolean.TRUE.equals(request.isDefault()) || firstAddress;

        if (asDefault) {
            clearDefault(userId);
        }
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        apply(address, request);
        address.setIsDefault(asDefault ? 1 : 0);
        addressMapper.insert(address);
        return AddressResponse.from(address);
    }

    @Transactional
    public AddressResponse update(long userId, long addressId, AddressRequest request) {
        UserAddress address = requireOwned(userId, addressId);
        apply(address, request);
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefault(userId);
            address.setIsDefault(1);
        }
        addressMapper.updateById(address);
        return AddressResponse.from(address);
    }

    @Transactional
    public void delete(long userId, long addressId) {
        UserAddress address = requireOwned(userId, addressId);
        addressMapper.deleteById(address.getId());
        // 删掉的是默认地址时，把剩下最新的一条顶上，保证列表里仍有默认地址
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            List<UserAddress> remaining = addressMapper.selectList(
                    Wrappers.<UserAddress>lambdaQuery()
                            .eq(UserAddress::getUserId, userId)
                            .orderByDesc(UserAddress::getId));
            if (!remaining.isEmpty()) {
                UserAddress promoted = remaining.get(0);
                promoted.setIsDefault(1);
                addressMapper.updateById(promoted);
            }
        }
    }

    @Transactional
    public AddressResponse setDefault(long userId, long addressId) {
        UserAddress address = requireOwned(userId, addressId);
        clearDefault(userId);
        address.setIsDefault(1);
        addressMapper.updateById(address);
        return AddressResponse.from(address);
    }

    /** 取当前用户的地址，不存在或不属于该用户都按同一错误处理，不泄露资源是否存在 */
    private UserAddress requireOwned(long userId, long addressId) {
        UserAddress address = addressMapper.selectOne(
                Wrappers.<UserAddress>lambdaQuery()
                        .eq(UserAddress::getId, addressId)
                        .eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new BizException(ErrorCode.USER_ADDRESS_FORBIDDEN);
        }
        return address;
    }

    private void clearDefault(long userId) {
        addressMapper.update(null, Wrappers.<UserAddress>lambdaUpdate()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1)
                .set(UserAddress::getIsDefault, 0));
    }

    private void apply(UserAddress address, AddressRequest request) {
        address.setReceiverName(request.receiverName().trim());
        address.setReceiverPhone(request.receiverPhone().trim());
        address.setProvince(request.province().trim());
        address.setCity(request.city().trim());
        address.setDistrict(request.district().trim());
        address.setDetail(request.detail().trim());
    }
}