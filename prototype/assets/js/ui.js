/* 百益商城 · 用户端原型 通用渲染 */

/* ---------- 顶栏 ---------- */
window.renderHeader = function (active) {
  var cartCount = window.Store.cartCount();
  var profile = window.Store.user();
  var kw = window.qs("keyword", "");
  var links = [
    { key: "home", text: "首页", href: "index.html" },
    { key: "seckill", text: "秒杀专区", href: "seckill.html" },
    { key: "orders", text: "我的订单", href: "orders.html" }
  ];
  var html = '<div class="container">'
    + '<a class="logo" href="index.html"><span class="logo-mark">益</span>百益商城</a>'
    + '<nav class="navlinks">'
    + links.map(function (l) {
        return '<a href="' + l.href + '" class="' + (l.key === active ? "on" : "") + '">' + l.text + '</a>';
      }).join("")
    + '</nav>'
    + '<form class="searchbar" action="search.html" method="get" role="search">'
    + '<input type="search" name="keyword" placeholder="搜索商品，如「耳机」「牛仔裤」" value="'
    + kw.replace(/"/g, "&quot;") + '" aria-label="搜索商品">'
    + '<button type="submit"><i data-icon="search" data-size="16"></i>搜索</button>'
    + '</form>'
    + '<div class="topactions">'
    + '<a href="cart.html"><i data-icon="shopping-cart" data-size="18"></i><span>购物车</span>'
    + (cartCount > 0 ? '<b class="cart-badge">' + cartCount + '</b>' : "") + '</a>'
    + '<div class="user-menu" id="userMenu">'
    + '<button type="button" class="user-trigger" aria-haspopup="true" aria-expanded="false">'
    + '<i data-icon="user" data-size="18"></i><span>'
    + (profile ? (profile.nickname || profile.username || "我的账号") : "登录 / 注册") + '</span>'
    + '<i data-icon="chevron-down" data-size="14"></i></button>'
    + '<div class="user-drop" role="menu">'
    + '<a role="menuitem" href="profile.html"><i data-icon="user" data-size="16"></i>个人中心</a>'
    + '<a role="menuitem" href="orders.html"><i data-icon="package" data-size="16"></i>我的订单</a>'
    + (profile
        ? '<a role="menuitem" href="#" id="logoutLink"><i data-icon="log-out" data-size="16"></i>退出登录</a>'
        : '<a role="menuitem" href="login.html"><i data-icon="log-in" data-size="16"></i>去登录</a>')
    + '</div></div>'
    + '</div></div>';
  var bar = document.createElement("header");
  bar.className = "topbar";
  bar.innerHTML = html;
  document.body.insertBefore(bar, document.body.firstChild);
  window.mountIcons(bar);

  var logout = bar.querySelector("#logoutLink");
  if (logout) {
    logout.addEventListener("click", function (ev) {
      ev.preventDefault();
      window.Api.logout().then(function () {
        window.toast("已退出登录");
        setTimeout(function () { location.href = "index.html"; }, 600);
      });
    });
  }

  /* 触屏 / 键盘场景：点击也能展开下拉 */
  var menu = bar.querySelector("#userMenu");
  var trigger = menu.querySelector(".user-trigger");
  trigger.addEventListener("click", function (ev) {
    ev.stopPropagation();
    var open = menu.classList.toggle("open");
    trigger.setAttribute("aria-expanded", open ? "true" : "false");
  });
  document.addEventListener("click", function () {
    if (menu.classList.contains("open")) {
      menu.classList.remove("open");
      trigger.setAttribute("aria-expanded", "false");
    }
  });
  menu.addEventListener("focusout", function () {
    setTimeout(function () {
      if (!menu.contains(document.activeElement)) {
        menu.classList.remove("open");
        trigger.setAttribute("aria-expanded", "false");
      }
    }, 0);
  });
};

/* ---------- 分类树工具（三级） ---------- */
window.catById = function (id) {
  return window.Store.catById(id);
};
/* 一级分类 → 其下二级（含三级）构建成树，供首页左侧导航使用 */
window.catTree = function () {
  return window.Store.catTree();
};
/* 自身 + 全部后代的分类 id，用于「含子分类」查询 */
window.catIds = function (id) {
  return window.Store.catIdsOf(id);
};
/* 从子分类回溯到一级分类的路径 */
window.catPath = function (id) {
  return window.Store.catPath(id);
};

/* ---------- 页脚 ---------- */
window.renderFooter = function () {
  var f = document.createElement("footer");
  f.className = "footer";
  f.innerHTML = '<div class="container"><span>百益商城 · 单商家自营 B2C 商城（学习 / 作品集项目）</span>'
    + '<span>全场包邮 · 模拟支付（微信 / 支付宝）· 发货后 7 天自动确认收货</span></div>';
  document.body.appendChild(f);
};

/* ---------- 分类聚合：含子分类的商品（走后端接口，返回 Promise） ---------- */
window.categoryProducts = function (categoryId, sortField, page, size) {
  return window.Api.get("/v1/categories/" + categoryId + "/products?sort=" + (sortField || "sales")
    + "&page=" + (page || 1) + "&size=" + (size || 20));
};

window.sortLabel = function (f) {
  return { sales: "销量", "new": "上新", price_asc: "价格升", price_desc: "价格降" }[f] || "销量";
};

/* ---------- 商品卡 ---------- */
window.productCard = function (p) {
  var image = window.img(p.mainImage || p.image || "", "headphone-01");
  var tag = p.brandName ? '<span class="tag tag-brand">' + window.esc(p.brandName) + '</span>' : "";
  return '<a class="product-card" href="product.html?id=' + p.id + '">'
    + '<div class="product-thumb"><img src="' + image + '" alt="' + window.esc(p.name) + '" loading="lazy">'
    + '<span class="corner">' + tag + '</span></div>'
    + '<div class="product-info">'
    + '<h4 class="product-name clamp2">' + window.esc(p.name) + '</h4>'
    + '<div class="product-meta"><span class="price">' + window.Store.moneyHtml(p.price) + '</span>'
    + '<span class="small faint">已售 ' + (p.sales || 0) + '</span></div>'
    + '</div></a>';
};

window.mountGrid = function (sel, list) {
  var el = document.querySelector(sel);
  if (!el) return;
  if (!list.length) {
    el.innerHTML = window.emptyHTML("没有找到相关商品", "换个关键词或筛选条件试试");
    return;
  }
  el.innerHTML = list.map(window.productCard).join("");
  window.mountIcons(el);
};

window.emptyHTML = function (title, desc) {
  return '<div class="empty" style="grid-column:1/-1"><i data-icon="package-x" data-size="40"></i>'
    + '<h4>' + title + '</h4><p class="small">' + desc + '</p></div>';
};

/* ---------- 状态 ---------- */
window.statusPill = function (status) {
  return window.Store.statusPill(status);
};

/* ---------- 轻提示 ---------- */
window.toast = function (msg, type) {
  var wrap = document.querySelector(".toast-wrap");
  if (!wrap) {
    wrap = document.createElement("div");
    wrap.className = "toast-wrap";
    document.body.appendChild(wrap);
  }
  var t = document.createElement("div");
  t.className = "toast " + (type === "err" ? "err" : type === "ok" ? "ok" : "");
  t.innerHTML = '<i data-icon="' + (type === "err" ? "circle-alert" : "circle-check") + '" data-size="16"></i>' + msg;
  wrap.appendChild(t);
  window.mountIcons(t);
  setTimeout(function () { t.remove(); }, 2400);
};

/* ---------- 弹窗 ---------- */
window.openModal = function (innerHTML, opts) {
  opts = opts || {};
  var mask = document.createElement("div");
  mask.className = "mask";
  mask.innerHTML = '<div class="modal" role="dialog" aria-modal="true">'
    + '<div class="modal-head"><h3>' + (opts.title || "") + '</h3>'
    + '<button class="btn btn-ghost btn-sm" data-close aria-label="关闭"><i data-icon="x" data-size="18"></i></button></div>'
    + '<div class="modal-body">' + innerHTML + '</div>'
    + (opts.footer ? '<div class="modal-foot">' + opts.footer + '</div>' : "")
    + '</div>';
  mask.addEventListener("click", function (e) {
    if (e.target === mask || e.target.closest("[data-close]")) closeModal();
  });
  document.body.appendChild(mask);
  window.mountIcons(mask);
  return mask;
};
window.closeModal = function () {
  document.querySelectorAll(".mask").forEach(function (m) { m.remove(); });
};

/* ---------- 标签页 / 分段控件 ---------- */
window.bindTabs = function (sel, onChange) {
  document.querySelectorAll(sel).forEach(function (group) {
    group.addEventListener("click", function (e) {
      var btn = e.target.closest("button");
      if (!btn || !group.contains(btn)) return;
      group.querySelectorAll("button").forEach(function (b) { b.classList.remove("on"); });
      btn.classList.add("on");
      if (onChange) onChange(btn.dataset.value || btn.textContent.trim(), btn);
    });
  });
};

/* ---------- 倒计时 ---------- */
window.countdown = function (el, endTime, onEnd) {
  var target = (endTime instanceof Date) ? endTime.getTime() : (function () {
    var s = String(endTime), t = Date.parse(s);
    return isNaN(t) ? Date.parse(s.replace(/-/g, "/")) : t;
  })();
  var interval = null, fired = false;
  function two(n) { return (n < 10 ? "0" : "") + n; }
  function tick() {
    var diff = target - Date.now();
    if (diff <= 0) {
      el.innerHTML = "<b>00</b>:<b>00</b>:<b>00</b>";
      if (interval) { clearInterval(interval); interval = null; }
      if (onEnd && !fired) { fired = true; setTimeout(onEnd, 0); }
      return;
    }
    var h = Math.floor(diff / 3600000), m = Math.floor(diff % 3600000 / 60000), s = Math.floor(diff % 60000 / 1000);
    el.innerHTML = "<b>" + two(h) + "</b>:<b>" + two(m) + "</b>:<b>" + two(s) + "</b>";
  }
  tick();
  if (!fired) interval = setInterval(tick, 1000);
  return interval;
};

/* ---------- 下单步骤条 ---------- */
window.stepbar = function (step) {
  var labels = ["我的购物车", "确认订单", "付款", "完成"];
  return '<div class="steps">' + labels.map(function (t, i) {
    var n = i + 1;
    return (i ? '<span class="sep"><i data-icon="chevron-right" data-size="14"></i></span>' : "")
      + '<div class="' + (n <= step ? "on" : "") + '"><i>' + n + '</i>' + t + '</div>';
  }).join("") + '</div>';
};

/* ---------- 通用初始化 ---------- */
function paintHeader(active) {
  var old = document.querySelector("header.topbar");
  if (old) old.remove();
  window.renderHeader(active);
  window.mountIcons(document);
}

/** 供页面在登录态 / 购物车数量变化后重画顶栏 */
window.repaintHeader = paintHeader;

window.pageInit = function (active) {
  paintHeader(active);
  window.renderFooter();
  window.mountIcons(document);
  // 会话与购物车角标是异步的：拿到后让顶栏按最新状态重画一次
  Promise.all([window.Store.refreshUser(), window.Store.refreshCartCount()]).then(function () {
    paintHeader(active);
  });
};
