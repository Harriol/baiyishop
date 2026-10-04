package com.harriol.baiyishop.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.product.dto.ParamItemRequest;
import com.harriol.baiyishop.product.dto.ParamItemResponse;
import com.harriol.baiyishop.product.dto.ParamTemplateRequest;
import com.harriol.baiyishop.product.dto.ParamTemplateResponse;
import com.harriol.baiyishop.product.entity.ParamItem;
import com.harriol.baiyishop.product.entity.ParamTemplate;
import com.harriol.baiyishop.product.mapper.ParamItemMapper;
import com.harriol.baiyishop.product.mapper.ParamTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 参数模板与参数项维护（REQ-204）。
 * <p>参数由后台统一维护，商品只做「选项 + 填值」，避免每个商品各写一套参数名。
 */
@Service
public class ParamService {

    private static final Logger log = LoggerFactory.getLogger(ParamService.class);

    private final ParamTemplateMapper templateMapper;
    private final ParamItemMapper itemMapper;

    public ParamService(ParamTemplateMapper templateMapper, ParamItemMapper itemMapper) {
        this.templateMapper = templateMapper;
        this.itemMapper = itemMapper;
    }

    public List<ParamTemplateResponse> listTemplates() {
        List<ParamTemplate> templates = templateMapper.selectList(Wrappers.<ParamTemplate>lambdaQuery()
                .orderByAsc(ParamTemplate::getSort).orderByAsc(ParamTemplate::getId));
        return templates.stream()
                .map(t -> new ParamTemplateResponse(t.getId(), t.getName(), t.getSort(),
                        t.getEnabled() != null && t.getEnabled() == 1, listItems(t.getId())))
                .toList();
    }

    public List<ParamItemResponse> listItems(Long templateId) {
        requireTemplate(templateId);
        return itemMapper.selectList(Wrappers.<ParamItem>lambdaQuery()
                        .eq(ParamItem::getTemplateId, templateId)
                        .orderByAsc(ParamItem::getSort).orderByAsc(ParamItem::getId))
                .stream().map(ParamItemResponse::from).toList();
    }

    @Transactional
    public ParamTemplateResponse createTemplate(ParamTemplateRequest request) {
        ParamTemplate template = new ParamTemplate();
        apply(template, request);
        templateMapper.insert(template);
        return new ParamTemplateResponse(template.getId(), template.getName(), template.getSort(),
                template.getEnabled() == 1, List.of());
    }

    @Transactional
    public ParamTemplateResponse updateTemplate(Long id, ParamTemplateRequest request) {
        ParamTemplate template = requireTemplate(id);
        apply(template, request);
        templateMapper.updateById(template);
        return new ParamTemplateResponse(template.getId(), template.getName(), template.getSort(),
                template.getEnabled() == 1, listItems(id));
    }

    @Transactional
    public void deleteTemplate(Long id) {
        requireTemplate(id);
        long items = itemMapper.selectCount(Wrappers.<ParamItem>lambdaQuery().eq(ParamItem::getTemplateId, id));
        if (items > 0) {
            throw new BizException(ErrorCode.PARAM_TEMPLATE_IN_USE);
        }
        templateMapper.deleteById(id);
        log.info("删除参数模板 id={}", id);
    }

    @Transactional
    public ParamItemResponse createItem(ParamItemRequest request) {
        if (request.templateId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择所属模板");
        }
        requireTemplate(request.templateId());

        ParamItem item = new ParamItem();
        apply(item, request);
        itemMapper.insert(item);
        return ParamItemResponse.from(item);
    }

    @Transactional
    public ParamItemResponse updateItem(Long id, ParamItemRequest request) {
        ParamItem item = requireItem(id);
        if (request.templateId() != null && !request.templateId().equals(item.getTemplateId())) {
            requireTemplate(request.templateId());
            item.setTemplateId(request.templateId());
        }
        apply(item, request);
        itemMapper.updateById(item);
        return ParamItemResponse.from(item);
    }

    @Transactional
    public void deleteItem(Long id) {
        requireItem(id);
        long used = itemMapper.countProductUsage(id);
        if (used > 0) {
            throw new BizException(ErrorCode.PARAM_ITEM_IN_USE);
        }
        itemMapper.deleteById(id);
        log.info("删除参数项 id={}", id);
    }

    private ParamTemplate requireTemplate(Long id) {
        ParamTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BizException(ErrorCode.PARAM_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private ParamItem requireItem(Long id) {
        ParamItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.PARAM_ITEM_NOT_FOUND);
        }
        return item;
    }

    private void apply(ParamTemplate template, ParamTemplateRequest request) {
        template.setName(request.name().trim());
        template.setSort(request.sort() == null ? 0 : request.sort());
        if (request.enabled() != null) {
            template.setEnabled(request.enabled() ? 1 : 0);
        } else if (template.getId() == null) {
            template.setEnabled(1);
        }
    }

    private void apply(ParamItem item, ParamItemRequest request) {
        if (request.templateId() != null) {
            item.setTemplateId(request.templateId());
        }
        item.setName(request.name().trim());
        item.setUnit(request.unit());
        item.setSort(request.sort() == null ? 0 : request.sort());
    }
}