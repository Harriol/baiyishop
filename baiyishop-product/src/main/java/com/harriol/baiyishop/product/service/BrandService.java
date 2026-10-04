package com.harriol.baiyishop.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.product.dto.BrandRequest;
import com.harriol.baiyishop.product.dto.BrandResponse;
import com.harriol.baiyishop.product.entity.Brand;
import com.harriol.baiyishop.product.mapper.BrandMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 品牌管理（REQ-202）。
 * <p>规则：名称唯一；被商品引用的品牌不可删除（30006）；停用后前台不可见。
 */
@Service
public class BrandService {

    private static final Logger log = LoggerFactory.getLogger(BrandService.class);

    private final BrandMapper brandMapper;

    public BrandService(BrandMapper brandMapper) {
        this.brandMapper = brandMapper;
    }

    /** 前台品牌列表：只返回启用中的 */
    public List<BrandResponse> enabledBrands() {
        List<Brand> brands = brandMapper.selectList(Wrappers.<Brand>lambdaQuery()
                .eq(Brand::getEnabled, 1)
                .orderByAsc(Brand::getSort)
                .orderByAsc(Brand::getId));
        return brands.stream().map(BrandResponse::from).toList();
    }

    /** 后台品牌分页列表，可按名称模糊与启用状态过滤 */
    public PageResult<BrandResponse> page(long page, long size, String keyword, Boolean enabled) {
        Page<Brand> pager = new Page<>(page, size);
        Page<Brand> result = brandMapper.selectPage(pager, Wrappers.<Brand>lambdaQuery()
                .like(StringUtils.hasText(keyword), Brand::getName, keyword)
                .eq(enabled != null, Brand::getEnabled, Boolean.TRUE.equals(enabled) ? 1 : 0)
                .orderByAsc(Brand::getSort)
                .orderByAsc(Brand::getId));
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(BrandResponse::from).toList());
    }

    @Transactional
    public BrandResponse create(BrandRequest request) {
        String name = request.name().trim();
        requireNameAvailable(name, null);

        Brand brand = new Brand();
        apply(brand, request);
        brandMapper.insert(brand);
        log.info("新增品牌 id={} name={}", brand.getId(), name);
        return BrandResponse.from(brand);
    }

    @Transactional
    public BrandResponse update(Long id, BrandRequest request) {
        Brand brand = require(id);
        String name = request.name().trim();
        requireNameAvailable(name, id);

        apply(brand, request);
        brandMapper.updateById(brand);
        return BrandResponse.from(brand);
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = require(id);
        long referenced = brandMapper.countProducts(id);
        if (referenced > 0) {
            throw new BizException(ErrorCode.BRAND_IN_USE);
        }
        brandMapper.deleteById(brand.getId());
        log.info("删除品牌 id={} name={}", id, brand.getName());
    }

    @Transactional
    public BrandResponse setEnabled(Long id, boolean enabled) {
        Brand brand = require(id);
        brand.setEnabled(enabled ? 1 : 0);
        brandMapper.updateById(brand);
        return BrandResponse.from(brand);
    }

    private Brand require(Long id) {
        Brand brand = brandMapper.selectById(id);
        if (brand == null) {
            throw new BizException(ErrorCode.BRAND_NOT_FOUND);
        }
        return brand;
    }

    /** 品牌名唯一（库中 uk_name 兜底，这里提前给出可读提示） */
    private void requireNameAvailable(String name, Long excludeId) {
        Brand existing = brandMapper.selectOne(Wrappers.<Brand>lambdaQuery().eq(Brand::getName, name));
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "品牌名称已存在：" + name);
        }
    }

    private void apply(Brand brand, BrandRequest request) {
        brand.setName(request.name().trim());
        brand.setLogo(request.logo());
        brand.setDescription(request.description());
        brand.setSort(request.sort() == null ? 0 : request.sort());
        if (request.enabled() != null) {
            brand.setEnabled(request.enabled() ? 1 : 0);
        } else if (brand.getId() == null) {
            brand.setEnabled(1);
        }
    }
}