/* 百益商城 · 用户端原型 通用渲染 */

/* ---------- 顶栏 ---------- */
window.renderHeader = function (active) {
  var cartCount = window.DB.cart.reduce(function (sum, c) { return sum + (c.invalid ? 0 : c.quantity); }, 0);
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
    + '<i data-icon="user" data-size="18"></i><span>' + window.DB.user.nickname + '</span>'
    + '<i data-icon="chevron-down" data-size="14"></i></button>'
    + '<div class="user-drop" role="menu">'
    + '<a role="menuitem" href="profile.html"><i data-icon="user" data-size="16"></i>个人中心</a>'
    + '<a role="menuitem" href="login.html"><i data-icon="log-out" data-size="16"></i>退出登录</a>'
    + '</div></div>'
    + '</div></div>';
  var bar = document.createElement("header");
  bar.className = "topbar";
  bar.innerHTML = html;
  document.body.insertBefore(bar, document.body.firstChild);

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
  return window.DB.categories.filter(function (c) { return c.id === Number(id); })[0] || null;
};
/* 一级分类 → 其下二级（含三级）构建成树，供首页左侧导航使用 */
window.catTree = function () {
  var all = window.DB.categories;
  return all.filter(function (c) { return c.level === 1; }).map(function (l1) {
    var level2 = all.filter(function (c) { return c.parentId === l1.id; }).map(function (l2) {
      return {
        id: l2.id, name: l2.name,
        children: all.filter(function (c) { return c.parentId === l2.id; })
      };
    });
    return { id: l1.id, name: l1.name, children: level2 };
  });
};
/* 自身 + 全部后代的分类 id，用于「含子分类」查询 */
window.catIds = function (id) {
  var cat = window.catById(id);
  if (!cat) return [Number(id)];
  return window.DB.categories
    .filter(function (c) { return c.path.indexOf(cat.path) === 0; })
    .map(function (c) { return c.id; });
};
/* 从子分类回溯到一级分类的路径 */
window.catPath = function (id) {
  var chain = [], cur = window.catById(id), guard = 0;
  while (cur && guard++ < 5) {
    chain.unshift(cur);
    cur = cur.parentId ? window.catById(cur.parentId) : null;
  }
  return chain;
};

/* ---------- 页脚 ---------- */
window.renderFooter = function () {
  var f = document.createElement("footer");
  f.className = "footer";
  f.innerHTML = '<div class="container"><span>百益商城 · 单商家自营 B2C 商城（学习 / 作品集项目）</span>'
    + '<span>全场包邮 · 模拟支付（微信 / 支付宝）· 发货后 7 天自动确认收货</span></div>';
  document.body.appendChild(f);
};

/* ---------- 分类聚合：含子分类的商品 ---------- */
window.categoryProducts = function (categoryId, sortField) {
  var cat = window.C(categoryId);
  var ids = [Number(categoryId)];
  if (cat) {
    window.DB.categories.forEach(function (c) {
      if (c.path.indexOf(cat.path) === 0 && c.id !== cat.id) ids.push(c.id);
    });
  }
  var list = window.DB.products.filter(function (p) {
    return p.status === "ON_SALE" && ids.indexOf(p.categoryId) >= 0;
  });
  var sorters = {
    sales: function (a, b) { return b.sales - a.sales; },
    "new": function (a, b) { return a.onSaleDays - b.onSaleDays; },
    price_asc: function (a, b) { return Number(a.price) - Number(b.price); },
    price_desc: function (a, b) { return Number(b.price) - Number(a.price); }
  };
  return list.sort(sorters[sortField] || sorters.sales);
};

window.sortLabel = function (f) {
  return { sales: "销量", "new": "上新", price_asc: "价格升", price_desc: "价格降" }[f] || "销量";
};

/* ---------- 商品卡 ---------- */
window.productCard = function (p) {
  var soldOut = p.stock <= 0;
  var tags = (p.tags || []).slice(0, 1).map(function (t) {
    return '<span class="tag ' + (t === "售罄" || t === "库存紧张" ? "tag-warn" : (t === "爆款" || t === "热销" ? "tag-price" : "tag-brand")) + '">' + t + '</span>';
  }).join("");
  return '<a class="product-card" href="product.html?id=' + p.id + '">'
    + '<div class="product-thumb"><img src="' + window.img(p.image) + '" alt="' + p.name + '" loading="lazy">'
    + '<span class="corner">' + (soldOut ? '<span class="tag tag-out">已售罄</span>' : tags) + '</span></div>'
    + '<div class="product-info">'
    + '<h4 class="product-name clamp2">' + p.name + '</h4>'
    + '<div class="product-meta"><span class="price">' + window.yuan(p.price) + '</span>'
    + '<span class="small faint">已售 ' + p.sales + '</span></div>'
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
  return '<span class="pill ' + window.DB.statusPill[status] + '">' + window.DB.statusText[status] + '</span>';
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

/* ---------- 单号生成（样式对齐 docs/api.md 6.2：时间戳 + 随机位） ---------- */
window.genOrderNo = function () {
  function p(n) { return (n < 10 ? "0" : "") + n; }
  var d = new Date();
  var stamp = "" + d.getFullYear() + p(d.getMonth() + 1) + p(d.getDate()) + p(d.getHours()) + p(d.getMinutes()) + p(d.getSeconds());
  var rnd = String(Math.floor(Math.random() * 100000000));
  while (rnd.length < 8) rnd = "0" + rnd;
  return stamp + rnd;
};
window.genTicketId = function () {
  var rnd = String(Math.floor(Math.random() * 10000));
  while (rnd.length < 4) rnd = "0" + rnd;
  return "TK" + window.genOrderNo().slice(0, 14) + rnd;
};

/* ---------- 通用初始化 ---------- */
window.pageInit = function (active) {
  window.renderHeader(active);
  window.renderFooter();
  window.mountIcons(document);
};
