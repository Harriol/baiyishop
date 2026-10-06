/* 百益商城 · 前端共享状态与展示工具（分类缓存、购物车角标、金额换算、状态字典）
   金额：后端统一「分」，前端负责展示换算（docs/api.md 1.8） */
window.Store = (function () {
  var flatCategories = [];
  var categoryMap = {};
  var categoryPromise = null;
  var user = null;
  var cartCount = 0;
  var listeners = [];

  var STATUS_TEXT = {
    PENDING_PAYMENT: "待付款",
    PENDING_SHIPMENT: "待发货",
    PENDING_RECEIPT: "待收货",
    COMPLETED: "已完成",
    CANCELLED: "已取消"
  };
  var STATUS_PILL = {
    PENDING_PAYMENT: "pill-warn",
    PENDING_SHIPMENT: "pill-info",
    PENDING_RECEIPT: "pill-info",
    COMPLETED: "pill-ok",
    CANCELLED: "pill-muted"
  };

  function notify() {
    listeners.forEach(function (fn) {
      try {
        fn({ user: user, cartCount: cartCount });
      } catch (e) { /* 单个订阅出错不影响其它 */ }
    });
  }

  function subscribe(fn) {
    listeners.push(fn);
    fn({ user: user, cartCount: cartCount });
  }

  /** 分 → 元字符串（保留两位） */
  function money(fen) {
    var n = Number(fen || 0) / 100;
    return n.toFixed(2);
  }

  function moneyHtml(fen) {
    return '<span class="sym">¥</span>' + money(fen);
  }

  function moneyPlain(fen) {
    return "¥" + money(fen);
  }

  /** 分类树 → 扁平数组（原型页面的查询工具按扁平结构工作） */
  function flatten(nodes, out) {
    (nodes || []).forEach(function (node) {
      out.push(node);
      if (node.children && node.children.length) flatten(node.children, out);
    });
    return out;
  }

  function categories() {
    if (categoryPromise) return categoryPromise;
    categoryPromise = Api.get("/v1/categories/tree").then(function (tree) {
      flatCategories = flatten(tree || [], []);
      categoryMap = {};
      flatCategories.forEach(function (c) { categoryMap[c.id] = c; });
      return flatCategories;
    }, function (error) {
      categoryPromise = null;   // 失败后允许下次重试
      throw error;
    });
    return categoryPromise;
  }

  function catById(id) {
    return categoryMap[Number(id)] || null;
  }

  function catPath(id) {
    var chain = [], cur = catById(id), guard = 0;
    while (cur && guard++ < 5) {
      chain.unshift(cur);
      cur = cur.parentId ? catById(cur.parentId) : null;
    }
    return chain;
  }

  /** 自身 + 全部后代分类 id（按物化路径前缀匹配，供「含子分类」查询） */
  function catIdsOf(id) {
    var cat = catById(id);
    if (!cat) return [Number(id)];
    return flatCategories
      .filter(function (c) { return String(c.path || "").indexOf(cat.path) === 0; })
      .map(function (c) { return c.id; });
  }

  /** 一级分类 → 二级 → 三级（首页左侧导航用） */
  function catTree() {
    return flatCategories.filter(function (c) { return c.level === 1; }).map(function (l1) {
      return {
        id: l1.id, name: l1.name,
        children: flatCategories.filter(function (c) { return c.parentId === l1.id; }).map(function (l2) {
          return {
            id: l2.id, name: l2.name,
            children: flatCategories.filter(function (c) { return c.parentId === l2.id; })
          };
        })
      };
    });
  }

  function refreshUser() {
    if (!Api.isLoggedIn()) {
      user = null;
      notify();
      return Promise.resolve(null);
    }
    return Api.profile().then(function (profile) {
      user = profile;
      notify();
      return profile;
    }, function () {
      user = Api.user();
      notify();
      return null;
    });
  }

  function refreshCartCount() {
    if (!Api.isLoggedIn()) {
      cartCount = 0;
      notify();
      return Promise.resolve(0);
    }
    return Api.get("/v1/carts", { silent: true }).then(function (cart) {
      cartCount = (cart.items || []).reduce(function (sum, item) {
        return sum + (item.invalid ? 0 : item.quantity);
      }, 0);
      notify();
      return cartCount;
    }, function () {
      return cartCount;
    });
  }

  function statusText(status) {
    return STATUS_TEXT[status] || status;
  }

  function statusPill(status) {
    return '<span class="pill ' + (STATUS_PILL[status] || "pill-muted") + '">' + statusText(status) + "</span>";
  }

  /** 失败原因 → 中文提示（秒杀结果用） */
  function failMessage(reason, fallback) {
    var map = {
      SOLD_OUT: "秒杀商品已售罄",
      LIMIT_EXCEEDED: "超出限购数量",
      ACTIVITY_NOT_STARTED: "秒杀尚未开始",
      ACTIVITY_ENDED: "秒杀已结束",
      NO_ADDRESS: "请先设置收货地址",
      ORDER_CANCELLED: "订单已取消，可重新抢购",
      SYSTEM_ERROR: "系统繁忙，请稍后重试"
    };
    return map[reason] || fallback || "抢购失败，请稍后重试";
  }

  /** 下单入口文案 */
  function sourceText(source) {
    return { CART: "购物车结算", BUY_NOW: "立即购买", SECKILL: "秒杀活动" }[source] || source || "—";
  }

  /** 支付渠道文案 */
  function channelText(channel) {
    return { WECHAT: "微信支付", ALIPAY: "支付宝" }[channel] || channel || "—";
  }

  return {
    money: money,
    moneyHtml: moneyHtml,
    moneyPlain: moneyPlain,
    categories: categories,
    catById: catById,
    catIdsOf: catIdsOf,
    catPath: catPath,
    catTree: catTree,
    statusText: statusText,
    statusPill: statusPill,
    failMessage: failMessage,
    sourceText: sourceText,
    channelText: channelText,
    refreshUser: refreshUser,
    refreshCartCount: refreshCartCount,
    subscribe: subscribe,
    user: function () { return user; },
    cartCount: function () { return cartCount; }
  };
})();
