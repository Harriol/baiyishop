/* 百益商城 · 运营后台原型：角色权限模型 + 后台数据 + 页面外壳 */

/* ---------- 角色与权限（对应 docs/database.md 3.5~3.8、REQ-106） ---------- */
window.ADMIN_ROLES = {
  SUPER_ADMIN: { name: "超级管理员", perms: ["*"] },
  OPERATOR: { name: "运营", perms: ["category", "brand", "product", "param", "home", "inventory", "seckill", "order", "order:read", "order:note", "order:ship"] },
  SERVICE: { name: "客服", perms: ["order", "order:read", "order:note"] }
};

window.getRole = function () {
  var r = sessionStorage.getItem("baiyi_admin_role");
  return window.ADMIN_ROLES[r] ? r : "SUPER_ADMIN";
};
window.setRole = function (r) { sessionStorage.setItem("baiyi_admin_role", r); };
window.hasPerm = function (perm) {
  if (!perm) return true;
  var list = window.ADMIN_ROLES[window.getRole()].perms;
  return list.indexOf("*") >= 0 || list.indexOf(perm) >= 0;
};
window.roleName = function () { return window.ADMIN_ROLES[window.getRole()].name; };

/* ---------- 后端菜单 ---------- */
window.MENUS = [
  { key: "dashboard", text: "工作台", icon: "chart-column", href: "index.html", perm: null, group: "概览" },
  { key: "orders", text: "订单管理", icon: "package", href: "orders.html", perm: "order", group: "交易", badgeKey: "pendingShip" },
  { key: "products", text: "商品管理", icon: "layout-grid", href: "products.html", perm: "product", group: "商品" },
  { key: "categories", text: "分类与品牌", icon: "list-filter", href: "categories.html", perm: "category" },
  { key: "inventory", text: "库存管理", icon: "boxes", href: "inventory.html", perm: "inventory", badgeKey: "alert" },
  { key: "home", text: "首页配置", icon: "house", href: "home.html", perm: "home", group: "运营" },
  { key: "seckill", text: "秒杀活动", icon: "ticket-percent", href: "seckill.html", perm: "seckill" },
  { key: "admins", text: "管理员与角色", icon: "shield-check", href: "admins.html", perm: "admin", group: "系统" }
];

/* ---------- 后台数据（原型 mock） ---------- */
window.ADMIN_DB = (function () {
  var buyers = ["王小雨", "李百益", "陈子谦", "赵敏", "周乐", "吴桐"];

  /* 订单：基于前台订单补充买家信息，并补几条让列表更真实 */
  var orders = window.DB.orders.map(function (o, i) {
    return {
      orderNo: o.orderNo, status: o.status, source: o.source, createdAt: o.createdAt,
      buyer: buyers[i % buyers.length], buyerId: 1001 + i,
      payAmount: o.payAmount, payType: o.payType || "", shipTime: o.shipTime || "",
      trackingNo: o.trackingNo || "", receiver: o.receiver, items: o.items,
      notes: i === 1 ? [{ admin: "客服小林", content: "客户电话要求工作日送达", at: "2026-09-27 11:02" }] : []
    };
  });
  var extra = [
    { orderNo: "2026092811023344556601", status: "PENDING_SHIPMENT", source: "SECKILL", createdAt: "2026-09-28 11:02:33",
      buyer: "孙浩", buyerId: 1009, payAmount: "599.00", payType: "WECHAT", shipTime: "", trackingNo: "",
      receiver: "孙浩 139****2233 江苏省南京市鼓楼区中山北路 100 号", items: [{ productId: 1007, quantity: 1 }], notes: [] },
    { orderNo: "2026092809451122334402", status: "PENDING_SHIPMENT", source: "CART", createdAt: "2026-09-28 09:45:11",
      buyer: "何静", buyerId: 1010, payAmount: "577.00", payType: "ALIPAY", shipTime: "", trackingNo: "",
      receiver: "何静 137****9911 上海市浦东新区张江路 88 号 3 号楼", items: [{ productId: 1011, quantity: 1 }, { productId: 1012, quantity: 1 }], notes: [] }
  ];
  orders = extra.concat(orders);

  /* 库存：以商品为维度（原型简化，正式为 SKU 维度） */
  var inventory = window.DB.products.map(function (p, i) {
    return {
      productId: p.id, skuCode: p.id + "-01", name: p.name, image: p.image,
      available: p.stock, locked: (i % 3) * 2, warnThreshold: 10, updatedAt: "2026-09-28 09:12"
    };
  });
  var alerts = inventory.filter(function (r) { return r.available <= r.warnThreshold; })
    .map(function (r) { return { productId: r.productId, name: r.name, current: r.available, threshold: r.warnThreshold, status: "OPEN", at: "2026-09-28 08:40" }; });
  alerts.push({ productId: 1014, name: "人体工学电脑椅 网布透气", current: 6, threshold: 10, status: "OPEN", at: "2026-09-27 19:20" });

  var flows = [
    { id: 9001, skuCode: "1004-01", name: "轻量缓震运动鞋 透气网面", type: "LOCK", quantity: 1, before: 211, after: 210, reason: "用户下单锁定", operator: "系统", at: "2026-09-28 15:30" },
    { id: 9002, skuCode: "1011-01", name: "机械键盘 87键 茶轴", type: "DEDUCT", quantity: 1, before: 97, after: 96, reason: "支付成功扣减", operator: "系统", at: "2026-09-28 14:12" },
    { id: 9003, skuCode: "1017-01", name: "硬壳线装笔记本 A5", type: "ADJUST", quantity: 200, before: 320, after: 520, reason: "到货补货", operator: "运营小王", at: "2026-09-28 10:05" },
    { id: 9004, skuCode: "1007-01", name: "头戴式主动降噪耳机", type: "ALLOCATE", quantity: 100, before: 145, after: 45, reason: "划拨至秒杀池（活动 #9002）", operator: "运营小王", at: "2026-09-28 09:30" },
    { id: 9005, skuCode: "1015-01", name: "陶瓷马克杯 350ml 情侣款", type: "UNLOCK", quantity: 2, before: 462, after: 460, reason: "订单超时取消释放", operator: "系统", at: "2026-09-27 22:41" }
  ];

  var admins = [
    { id: 1, username: "harriol", realName: "Harriol", roles: ["SUPER_ADMIN"], status: 1, lastLoginAt: "2026-09-28 09:02" },
    { id: 2, username: "yunying01", realName: "王运营", roles: ["OPERATOR"], status: 1, lastLoginAt: "2026-09-28 08:51" },
    { id: 3, username: "kefu01", realName: "林客服", roles: ["SERVICE"], status: 1, lastLoginAt: "2026-09-27 18:20" },
    { id: 4, username: "kefu02", realName: "陈客服", roles: ["SERVICE"], status: 0, lastLoginAt: "2026-09-20 10:11" }
  ];

  var PERM_MATRIX = [
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

  return { orders: orders, inventory: inventory, alerts: alerts, flows: flows, admins: admins, PERM_MATRIX: PERM_MATRIX };
})();

window.flowTypeText = function (t) {
  return { LOCK: "锁定", DEDUCT: "扣减", UNLOCK: "释放", ADJUST: "调整",
    ALLOCATE: "划拨秒杀", RETURN: "秒杀回补", ROLLBACK: "秒杀回滚" }[t] || t;
};

/* ---------- 页面外壳 ---------- */
window.adminShell = function (activeKey, opts) {
  opts = opts || {};
  var role = window.getRole();

  var pendingShip = window.ADMIN_DB.orders.filter(function (o) { return o.status === "PENDING_SHIPMENT"; }).length;
  var alertCount = window.ADMIN_DB.alerts.filter(function (a) { return a.status === "OPEN"; }).length;
  var badges = { pendingShip: pendingShip, alert: alertCount };

  var nav = "", lastGroup = null;
  window.MENUS.forEach(function (m) {
    if (m.group && m.group !== lastGroup) { nav += '<div class="grp">' + m.group + '</div>'; lastGroup = m.group; }
    var allowed = window.hasPerm(m.perm);
    var badge = allowed && m.badgeKey && badges[m.badgeKey] ? '<span class="badge">' + badges[m.badgeKey] + '</span>' : "";
    nav += '<a href="' + (allowed ? m.href : "javascript:void(0)") + '" data-key="' + m.key + '" data-allowed="' + allowed + '"'
      + (m.key === activeKey ? ' class="on' + (allowed ? "" : " locked") + '"' : (allowed ? "" : ' class="locked"'))
      + '><i data-icon="' + (allowed ? m.icon : "shield-check") + '" data-size="17"></i><span class="t">' + m.text + '</span>' + badge + '</a>';
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
    + '<span class="small faint nowrap">演示角色</span>'
    + '<div class="role-switch" id="roleSwitch">'
    + ["SUPER_ADMIN", "OPERATOR", "SERVICE"].map(function (r) {
        return '<button data-role="' + r + '" class="' + (r === role ? "on" : "") + '">' + window.ADMIN_ROLES[r].name + '</button>';
      }).join("")
    + '</div>'
    + '<div class="user-menu" id="adminMenu">'
    + '<button type="button" class="user-trigger" aria-haspopup="true" aria-expanded="false">'
    + '<i data-icon="user" data-size="17"></i><span>' + window.DB.user.nickname + '</span>'
    + '<i data-icon="chevron-down" data-size="14"></i></button>'
    + '<div class="user-drop" role="menu">'
    + '<a role="menuitem" href="../index.html"><i data-icon="store" data-size="16"></i>前台商城</a>'
    + '<a role="menuitem" href="login.html"><i data-icon="log-out" data-size="16"></i>退出登录</a>'
    + '</div></div>'
    + '</header>'
    + '<div class="content" id="content"></div>'
    + '</div>';
  document.body.insertBefore(shell, document.body.firstChild);

  /* 角色切换 */
  shell.querySelectorAll("#roleSwitch button").forEach(function (b) {
    b.addEventListener("click", function () {
      window.setRole(b.dataset.role);
      location.reload();
    });
  });

  /* 管理员下拉（点击 / 悬浮均可） */
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

  /* 无权限菜单点击提示 */
  shell.querySelectorAll('.side-nav a[data-allowed="false"]').forEach(function (a) {
    a.addEventListener("click", function () { window.toast("当前角色无该模块权限（对应接口返回 403）", "err"); });
  });

  window.mountIcons(shell);

  /* 权限门禁 */
  if (!window.hasPerm(opts.perm)) {
    document.getElementById("content").innerHTML =
      '<div class="forbidden"><i data-icon="shield-check" data-size="46"></i>'
      + '<h2 class="mt12">403 无权限</h2>'
      + '<p class="muted small mt8">当前角色「' + window.roleName() + '」没有访问该模块的权限，请切换到超管或运营角色查看。</p>'
      + '<p class="small faint mt8">对应后端行为：网关与服务端二次鉴权返回 403（' + (opts.perm || "-") + '）。</p></div>';
    window.mountIcons(document.getElementById("content"));
    return false;
  }
  return true;
};
