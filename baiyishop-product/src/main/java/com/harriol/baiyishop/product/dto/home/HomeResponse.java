package com.harriol.baiyishop.product.dto.home;

import java.util.List;

/**
 * 前台首页聚合（docs/api.md 4.3、REQ-403）。
 * <p>任一模块为空时返回空数组而不是 404，双端做空态兜底。
 */
public record HomeResponse(List<BannerItem> banners, List<NoticeItem> notices,
                           List<NavItem> navs, List<HomeFloorView> floors) {
}