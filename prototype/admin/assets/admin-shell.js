/* 百益商城 · 运营后台：登录态、真实权限模型与页面外壳
   权限码与后端 R__seed_role_and_permission.sql 一致（admin:read / product:write / order:ship …） */

window.ADMIN_ROLES = {
  SUPER_ADMIN: { name: "超级管理员", perms: ["*"] },
  OPERATOR: { name: "运营", perms: null },   // null = 用后端返回的 permissions 判断
  SERVICE: { name: "客服", perms: ["order:read", "order:note"] }
};

/** 后端菜单：perm 用后端权限码，页面据此显示锁定态 */
window.MENUS = [
  { key: "dashboard", text: "工作台", icon: "chart-column", href: "index.html", perm: null, group: "概览" },
  { key: "orders", text: "订单管理", icon: "package", href: "orders.html", perm: "order:read", group: "交易", badgeKey: "pendingShip" },
  { key: "products", text: "商品管理", icon: "layout-grid", href: "products.html", perm: "product:read", group: "商品" },
  { key: "categories", text: "分类与品牌", icon: "list-filter", href: "categories.html", perm: "category:read" },
  { key: "inventory", text: "库存管理", icon: "boxes", href: "inventory.html", perm: "inventory:read", badgeKey: "alert" },
  { key: "home", text: "首页配置", icon: "house", href: "home.html", perm: "home:read", group: "运营" },
  { key: "seckill", text: "秒杀活动", icon: "ticket-percent", href: "seckill.html", perm: "seckill:read" },
  { key: "admins", text: "管理员与角色", icon: "shield-check", href: "admins.html", perm: "admin:read", group: "系统" }
];

/** 角色权限矩阵：后端没有暴露 role→permission 查询接口，这里按 R__seed 的定义展示 */
window.PERM_MATRIX = [
  { name: "分类与品牌", perms: { SUPER_ADMIN: true, OPERATOR: true, SERVICE: false } },
  { name: "商品与参数", perms: { SUPER_ADMIN: true, OPERATOR: true, SERVICE: false } },
  { name: "首页配置", perms: { SUPER_ADMIN: true, OPERATOR: true, SERVICE: false } },
  { name: "库存管理", perms: { SUPER_ADMIN: true, OPERATOR: true, SERVICE: false } },
  { name: "秒杀活动", perms: { SUPER_ADMIN: true, OPERATOR: true, SERVICE: false } },
  { name: "订单查询", perms: { SUPER_ADMIN: true, OPERATOR: true, SERVICE: true } },
  { name: "订单备注", perms: { SUPER_ADMIN: true, OPERATOR: true, SERVICE: true } },
  { name: "订单发货", perms: { SUPER_ADMIN: true, OPERATOR: true, SERVICE: false } },
  { name: "管理员与角色", perms: { SUPER_ADMIN: true, OPERATOR: false, SERVICE: false } }
];

var profile = window.Api.user();

window.adminProfile = function () { return profile; };

window.getRole = function () {
  var roles = (profile && profile.roles) || [];
  if (roles.indexOf("SUPER_ADMIN") >= 0) return "SUPER_ADMIN";
  if (roles.indexOf("OPERATOR") >= 0) return "OPERATOR";
  if (roles.indexOf("SERVICE") >= 0) return "SERVICE";
  return "OPERATOR";
};

window.roleName = function () {
  return (window.ADMIN_ROLES[window.getRole()] || {}).name || getRole();
};

/** 权限判断：超管全通；其余按后端返回的 permissions 列表 */
window.hasPerm = function (perm) {
  if (!perm) return true;
  var roles = (profile && profile.roles) || [];
  if (roles.indexOf("SUPER_ADMIN") >= 0) return true;
  var precomputed = (window.ADMIN_ROLES[window.getRole()] || {}).perms;
  if (precomputed && precomputed.indexOf("*") >= 0) return true;
  var list = (profile && profile.permissions) || [];
  return list.indexOf(perm) >= 0;
};

window.flowTypeText = function (type) {
  return { LOCK: "锁定", DEDUCT: "扣减", UNLOCK: "释放", ADJUST: "调整",
    ALLOCATE: "划拨秒杀", RETURN: "秒杀回补", ROLLBACK: "秒杀回滚" }[type] || type;
};

/** 后台登录页地址（当前页作为 next 回跳） */
function adminLoginUrl() {
  var page = location.pathname.split("/").pop() || "index.html";
  return "login.html?next=" + encodeURIComponent(page + location.search);
}

window.adminShell = function (activeKey, opts) {
  opts = opts || {};
  if (!window.Api.isLoggedIn() || !profile) {
    location.href = adminLoginUrl();
    return false;
  }

  var nav = "", lastGroup = null;
  window.MENUS.forEach(function (menu) {
    if (menu.group && menu.group !== lastGroup) { nav += '<div class="grp">' + menu.group + '</div>'; lastGroup = menu.group; }
    var allowed = window.hasPerm(menu.perm);
    var badge = allowed && menu.badgeKey ? '<span class="badge" data-badge="' + menu.badgeKey + '"></span>' : "";
    nav += '<a href="' + (allowed ? menu.href : "javascript:void(0)") + '" data-key="' + menu.key + '" data-allowed="' + allowed + '"'
      + (menu.key === activeKey ? ' class="on' + (allowed ? "" : " locked") + '"' : (allowed ? "" : ' class="locked"'))
      + '><i data-icon="' + (allowed ? menu.icon : "shield-check") + '" data-size="17"></i><span class="t">' + menu.text + '</span>' + badge + '</a>';
  });

  var shell = document.createElement("div");
  shell.className = "admin";
  shell.innerHTML =
    '<aside class="sidebar">'
    + '<div class="side-logo"><span class="logo-mark">益</span><span class="t">百益后台</span></div>'
    + '<nav class="side-nav">' + nav + '</nav>'
    + '<div class="side-foot">当前角色：' + window.roleName() + '</div>'
    + '</aside>'
    + '<div class="main">'
    + '<header class="adminbar">'
    + '<div class="crumbbar"><b>' + (opts.title || "") + '</b>'
    + (opts.crumb ? '<span class="faint">·</span><span>' + opts.crumb + '</span>' : '') + '</div>'
    + '<div class="spacer" style="flex:1"></div>'
    + '<div class="user-menu" id="adminMenu">'
    + '<button type="button" class="user-trigger" aria-haspopup="true" aria-expanded="false">'
    + '<i data-icon="user" data-size="17"></i><span>' + (profile.realName || profile.username || "管理员") + '</span>'
    + '<i data-icon="chevron-down" data-size="14"></i></button>'
    + '<div class="user-drop" role="menu">'
    + '<a role="menuitem" href="../index.html"><i data-icon="store" data-size="16"></i>前台商城</a>'
    + '<a role="menuitem" href="#" id="adminLogout"><i data-icon="log-out" data-size="16"></i>退出登录</a>'
    + '</div></div>'
    + '</header>'
    + '<div class="content" id="content"></div>'
    + '</div>';
  document.body.insertBefore(shell, document.body.firstChild);

  var menu = shell.querySelector("#adminMenu");
  var trigger = menu.querySelector(".user-trigger");
  trigger.addEventListener("click", function (ev) {
    ev.stopPropagation();
    var open = menu.classList.toggle("open");
    trigger.setAttribute("aria-expanded", open ? "true" : "false");
  });
  document.addEventListener("click", function () {
    menu.classList.remove("open");
    trigger.setAttribute("aria-expanded", "false");
  });
  shell.querySelector("#adminLogout").addEventListener("click", function (ev) {
    ev.preventDefault();
    window.Api.adminLogout();
    location.href = "login.html";
  });

  /* 无权限菜单点击提示 */
  shell.querySelectorAll('.side-nav a[data-allowed="false"]').forEach(function (a) {
    a.addEventListener("click", function () { window.toast("当前角色无该模块权限（对应接口返回 403）", "err"); });
  });

  window.mountIcons(shell);
  loadBadges(shell);
  refreshProfile();

  /* 权限门禁 */
  if (!window.hasPerm(opts.perm)) {
    document.getElementById("content").innerHTML =
      '<div class="forbidden"><i data-icon="shield-check" data-size="46"></i>'
      + '<h2 class="mt12">403 无权限</h2>'
      + '<p class="muted small mt8">当前角色「' + window.roleName() + '」没有访问该模块的权限（权限码 ' + (opts.perm || "-") + '）。</p>'
      + '<p class="small faint mt8">对应后端行为：网关与服务端二次鉴权返回 403。</p></div>';
    window.mountIcons(document.getElementById("content"));
    return false;
  }
  return true;
};

/** 侧边栏待办角标：待发货订单数、待处理库存预警数 */
function loadBadges(shell) {
  function paint(key, value) {
    var el = shell.querySelector('[data-badge="' + key + '"]');
    if (el && value > 0) el.textContent = value;
  }
  window.Api.get("/v1/admin/orders?status=PENDING_SHIPMENT&page=1&size=1", { silent: true })
    .then(function (page) { paint("pendingShip", page.total || 0); }, function () { });
  window.Api.get("/v1/admin/inventory/alerts?status=OPEN", { silent: true })
    .then(function (list) { paint("alert", (list || []).length); }, function () { });
}

/** 用最新资料刷新缓存（角色调整后下次进页面即生效） */
function refreshProfile() {
  window.Api.adminProfile().then(function (fresh) {
    profile = fresh;
  }, function () { /* 令牌失效时 Api 层已处理跳登录 */ });
}
