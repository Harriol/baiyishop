/* 百益商城 · 小程序原型外壳：手机框 + 状态栏 + 导航栏 + 底部标签栏
   数据已接入真实接口：购物车角标来自 Store.cartCount()（/api/v1/carts） */

window.MINI_TABS = [
  { key: "home", text: "首页", icon: "house", href: "index.html" },
  { key: "category", text: "分类", icon: "layout-grid", href: "category.html" },
  { key: "cart", text: "购物车", icon: "shopping-cart", href: "cart.html" },
  { key: "profile", text: "我的", icon: "user", href: "profile.html" }
];

/* 创建小程序页面骨架，返回可用于填充内容的 body 元素 */
window.miniShell = function (opts) {
  opts = opts || {};
  document.body.classList.add("mini-doc");

  var tabsHTML = opts.tabs === false ? "" : '<nav class="mini-tabs">'
    + window.MINI_TABS.map(function (t) {
        var dot = t.key === "cart" ? '<span class="dot" id="miniCartDot" style="display:none"></span>' : "";
        return '<a href="' + t.href + '" class="' + (t.key === opts.tab ? "on" : "") + '">'
          + '<i data-icon="' + t.icon + '" data-size="20"></i><span>' + t.text + '</span>' + dot + '</a>';
      }).join("") + '</nav>';

  var navHTML = opts.navbar === false ? "" : '<div class="mini-nav">'
    + (opts.back ? '<a class="back" href="' + opts.back + '"><i data-icon="chevron-left" data-size="20"></i></a>'
                 : '<span class="act"></span>')
    + '<span class="t">' + (opts.title || "") + '</span>'
    + '<span class="act"></span></div>';

  var wrap = document.createElement("div");
  wrap.className = "phone";
  wrap.innerHTML = '<div class="phone-screen">'
    + '<div class="mini-status"><span>9:41</span><span class="right">'
    + '<i data-icon="smartphone" data-size="12"></i><i data-icon="wallet" data-size="12"></i>'
    + '<i data-icon="shield-check" data-size="12"></i><b style="font-weight:600">100%</b></span></div>'
    + navHTML
    + '<div class="mini-body" id="miniBody"></div>'
    + (opts.bar || "")
    + tabsHTML
    + '</div>';
  document.body.appendChild(wrap);
  window.mountIcons(wrap);

  /* 购物车角标：订阅 Store，登录态或购物车变化时自动更新 */
  if (window.Store && window.Store.subscribe) {
    window.Store.subscribe(function (state) {
      var dot = document.getElementById("miniCartDot");
      if (!dot) return;
      dot.textContent = state.cartCount > 99 ? "99+" : (state.cartCount || "");
      dot.style.display = state.cartCount > 0 ? "" : "none";
    });
    window.Store.refreshCartCount();
  }
  return document.getElementById("miniBody");
};

window.miniToast = function (msg, type) { window.toast(msg, type); };

window.mountMiniIcons = function (root) { window.mountIcons(root || document); };
