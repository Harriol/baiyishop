package com.harriol.baiyishop.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.product.dto.CategoryRequest;
import com.harriol.baiyishop.product.dto.CategoryResponse;
import com.harriol.baiyishop.product.entity.Category;
import com.harriol.baiyishop.product.mapper.CategoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类管理（REQ-201、REQ-205）。
 * <p>规则：
 * <ul>
 *   <li>最多 3 级，超过抛 30004</li>
 *   <li>删除时若存在子分类（30002）或已被商品引用（30003）则拒绝</li>
 *   <li>path 是物化路径（/1/12/135/），改父级时自身与全部后代的 path、level 一并重算，
 *       保证「按分类含子分类」查询始终成立</li>
 * </ul>
 */
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    /** 需求规定分类最多 3 级（REQ-201） */
    private static final int MAX_LEVEL = 3;

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /** 分类树。前台只要可见的，后台要全量 */
    public List<CategoryResponse> tree(boolean includeHidden) {
        var query = Wrappers.<Category>lambdaQuery().orderByAsc(Category::getSort).orderByAsc(Category::getId);
        if (!includeHidden) {
            query.eq(Category::getVisible, 1);
        }
        List<Category> all = categoryMapper.selectList(query);
        return buildTree(all);
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category parent = resolveParent(request.parentId());
        int level = parent == null ? 1 : parent.getLevel() + 1;
        if (level > MAX_LEVEL) {
            throw new BizException(ErrorCode.CATEGORY_LEVEL_EXCEEDED);
        }

        Category category = new Category();
        category.setParentId(parent == null ? 0L : parent.getId());
        category.setName(request.name().trim());
        category.setIcon(request.icon());
        category.setSort(request.sort() == null ? 0 : request.sort());
        category.setVisible(request.visible() == null || request.visible() ? 1 : 0);
        category.setLevel(level);
        category.setPath("");
        categoryMapper.insert(category);

        // 自增 ID 生成后才能算出物化路径
        category.setPath(pathOf(parent, category.getId()));
        categoryMapper.updateById(category);
        log.info("新增分类 id={} name={} level={}", category.getId(), category.getName(), level);
        return CategoryResponse.of(category);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = require(id);
        Category parent = resolveParent(request.parentId());

        if (parent != null && parent.getId().equals(category.getId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能把分类挂到自己下面");
        }
        int newLevel = parent == null ? 1 : parent.getLevel() + 1;
        if (parent != null && isDescendant(category.getPath(), parent.getPath())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能把分类挂到自己的子分类下");
        }
        int depthOfSubtree = subtreeDepth(category);
        if (newLevel + depthOfSubtree - 1 > MAX_LEVEL) {
            throw new BizException(ErrorCode.CATEGORY_LEVEL_EXCEEDED);
        }

        category.setParentId(parent == null ? 0L : parent.getId());
        category.setName(request.name().trim());
        category.setIcon(request.icon());
        category.setSort(request.sort() == null ? 0 : request.sort());
        if (request.visible() != null) {
            category.setVisible(request.visible() ? 1 : 0);
        }

        String oldPath = category.getPath();
        String newPath = pathOf(parent, category.getId());
        category.setLevel(newLevel);
        category.setPath(newPath);
        categoryMapper.updateById(category);

        if (!newPath.equals(oldPath)) {
            rewriteDescendantPaths(category.getId(), oldPath, newPath, newLevel);
        }
        return CategoryResponse.of(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = require(id);
        if (categoryMapper.countDescendants(category.getPath(), category.getId()) > 0) {
            throw new BizException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        if (categoryMapper.countProducts(category.getId()) > 0) {
            throw new BizException(ErrorCode.CATEGORY_IN_USE);
        }
        categoryMapper.deleteById(category.getId());
        log.info("删除分类 id={}", id);
    }

    @Transactional
    public CategoryResponse setVisible(Long id, boolean visible) {
        Category category = require(id);
        category.setVisible(visible ? 1 : 0);
        categoryMapper.updateById(category);
        return CategoryResponse.of(category);
    }

    /**
     * 返回「自身 + 全部后代」的分类 ID。
     * <p>前台按一级分类浏览时要把三级分类下的商品都带出来（REQ-205），
     * 靠物化路径前缀匹配实现，避免递归查库。
     */
    public List<Long> selfAndDescendantIds(Long categoryId) {
        Category category = require(categoryId);
        List<Long> ids = new ArrayList<>();
        ids.add(category.getId());
        categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                        .likeRight(Category::getPath, category.getPath())
                        .ne(Category::getId, category.getId()))
                .forEach(child -> ids.add(child.getId()));
        return ids;
    }

    private Category require(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    private Category resolveParent(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return null;
        }
        Category parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return parent;
    }

    private String pathOf(Category parent, Long id) {
        return (parent == null ? "/" : parent.getPath()) + id + "/";
    }

    /** 判断 candidatePath 是否位于 ancestorPath 之下 */
    private boolean isDescendant(String ancestorPath, String candidatePath) {
        return candidatePath != null && candidatePath.startsWith(ancestorPath);
    }

    /** 自身以下还有几层（不含自身） */
    private int subtreeDepth(Category category) {
        List<Category> descendants = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .likeRight(Category::getPath, category.getPath())
                .ne(Category::getId, category.getId()));
        return descendants.stream()
                .mapToInt(d -> d.getLevel() - category.getLevel())
                .max().orElse(0);
    }

    /** 把后代分类的 path 与 level 按新前缀整体平移 */
    private void rewriteDescendantPaths(Long selfId, String oldPath, String newPath, int newSelfLevel) {
        List<Category> descendants = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .likeRight(Category::getPath, oldPath)
                .ne(Category::getId, selfId));
        for (Category descendant : descendants) {
            String suffix = descendant.getPath().substring(oldPath.length());
            descendant.setPath(newPath + suffix);
            descendant.setLevel(newSelfLevel + countSegments(suffix));
            categoryMapper.updateById(descendant);
        }
    }


    private int countSegments(String suffix) {
        int count = 0;
        for (String segment : suffix.split("/")) {
            if (!segment.isBlank()) {
                count++;
            }
        }
        return count;
    }

    /** 由扁平列表拼出三级树 */
    private List<CategoryResponse> buildTree(List<Category> all) {
        Map<Long, CategoryResponse> nodes = new LinkedHashMap<>();
        for (Category category : all) {
            nodes.put(category.getId(), CategoryResponse.of(category));
        }
        List<CategoryResponse> roots = new ArrayList<>();
        for (CategoryResponse node : nodes.values()) {
            if (node.parentId() == null || node.parentId() == 0L) {
                roots.add(node);
                continue;
            }
            CategoryResponse parent = nodes.get(node.parentId());
            if (parent == null) {
                // 父级被隐藏时，子级在前台树里没有挂载点，直接从可见树中略过
                continue;
            }
            parent.children().add(node);
        }
        roots.sort(Comparator.comparing(CategoryResponse::sort, Comparator.nullsLast(Integer::compareTo)));
        return roots;
    }
}