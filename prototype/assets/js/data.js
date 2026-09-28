/* 百益商城 · 用户端原型 mock 数据
   字段命名与 docs/api.md 一致：金额为「元」字符串，状态用枚举码 */

window.DB = (function () {

  var categoryTree = [
    { id: 12, name: "数码电器", children: [
      { id: 121, name: "影音娱乐", children: [
        { id: 1211, name: "耳机音箱" }, { id: 1212, name: "相机摄影" }] },
      { id: 122, name: "电脑外设", children: [
        { id: 1221, name: "键盘鼠标" }, { id: 1222, name: "显示器" }] },
      { id: 123, name: "智能生活", children: [
        { id: 1231, name: "智能穿戴" }, { id: 1232, name: "生活电器" }] }] },
    { id: 13, name: "服饰鞋包", children: [
      { id: 131, name: "男装", children: [{ id: 1311, name: "T恤" }, { id: 1312, name: "外套" }] },
      { id: 132, name: "女装", children: [{ id: 1321, name: "牛仔裤" }, { id: 1322, name: "连衣裙" }] },
      { id: 133, name: "鞋靴", children: [{ id: 1331, name: "运动鞋" }, { id: 1332, name: "女鞋" }] },
      { id: 134, name: "箱包", children: [{ id: 1341, name: "双肩包" }, { id: 1342, name: "行李箱" }] },
      { id: 135, name: "配饰", children: [{ id: 1351, name: "眼镜" }] }] },
    { id: 14, name: "家居生活", children: [
      { id: 141, name: "家具", children: [{ id: 1411, name: "座椅" }] },
      { id: 142, name: "厨房用品", children: [{ id: 1421, name: "杯壶" }] },
      { id: 143, name: "文具", children: [{ id: 1431, name: "笔记本" }] }] },
    { id: 15, name: "食品饮料", children: [
      { id: 151, name: "咖啡冲调", children: [{ id: 1511, name: "咖啡豆" }] },
      { id: 152, name: "休闲零食", children: [{ id: 1521, name: "坚果炒货" }] }] },
    { id: 16, name: "美妆个护", children: [
      { id: 161, name: "护肤", children: [{ id: 1611, name: "面部护理" }] },
      { id: 162, name: "洗护", children: [{ id: 1621, name: "洗发护发" }] }] },
    { id: 17, name: "母婴玩具", children: [
      { id: 171, name: "婴童用品", children: [{ id: 1711, name: "喂养用品" }] },
      { id: 172, name: "玩具", children: [{ id: 1721, name: "积木拼装" }] }] },
    { id: 18, name: "运动户外", children: [
      { id: 181, name: "运动装备", children: [{ id: 1811, name: "健身器材" }] },
      { id: 182, name: "户外用品", children: [{ id: 1821, name: "户外露营" }] }] },
    { id: 19, name: "图书文娱", children: [
      { id: 191, name: "图书", children: [{ id: 1911, name: "经管励志" }] },
      { id: 192, name: "文具文创", children: [{ id: 1921, name: "手账周边" }] }] }
  ];

  /* 扁平化：level、path（物化路径，含子分类查询用）与 parentId */
  var categories = (function () {
    var flat = [];
    categoryTree.forEach(function (l1) {
      flat.push({ id: l1.id, parentId: 0, level: 1, name: l1.name, path: "/" + l1.id + "/" });
      (l1.children || []).forEach(function (l2) {
        var p2 = "/" + l1.id + "/" + l2.id + "/";
        flat.push({ id: l2.id, parentId: l1.id, level: 2, name: l2.name, path: p2 });
        (l2.children || []).forEach(function (l3) {
          flat.push({ id: l3.id, parentId: l2.id, level: 3, name: l3.name, path: p2 + l3.id + "/" });
        });
      });
    });
    return flat;
  })();

  var brands = [
    { id: 1, name: "百益严选" }, { id: 2, name: "云简" }, { id: 3, name: "木石" },
    { id: 4, name: "北辰" }, { id: 5, name: "逐风" }
  ];

  var products = [
    { id: 1001, name: "纯棉圆领短袖T恤 男女同款", categoryId: 1311, brandId: 5, price: "99.00", originPrice: "159.00", sales: 3286, stock: 120, image: "tee-01", status: "ON_SALE", tags: ["新客立减"], onSaleDays: 2,
      params: { "材质": "100% 精梳棉", "产地": "浙江", "克重": "190g", "版型": "标准版型" } },
    { id: 1002, name: "直筒水洗牛仔裤 复古蓝", categoryId: 1321, brandId: 5, price: "229.00", originPrice: "329.00", sales: 1204, stock: 64, image: "jeans-01", status: "ON_SALE", tags: [], onSaleDays: 9,
      params: { "材质": "98% 棉 2% 氨纶", "产地": "广东", "洗涤": "机洗冷水" } },
    { id: 1003, name: "轻便防风夹克 三色可选", categoryId: 1312, brandId: 4, price: "369.00", originPrice: "499.00", sales: 682, stock: 38, image: "jacket-01", status: "ON_SALE", tags: ["当季新品"], onSaleDays: 1,
      params: { "面料": "聚酯纤维", "防水等级": "日常防泼水", "产地": "江苏" } },
    { id: 1004, name: "轻量缓震运动鞋 透气网面", categoryId: 1331, brandId: 5, price: "299.00", originPrice: "429.00", sales: 2415, stock: 210, image: "sneaker-01", status: "ON_SALE", tags: ["热销"], onSaleDays: 16,
      params: { "鞋面": "飞织网面", "鞋底": "EVA 缓震", "适用": "日常通勤 / 慢跑" } },
    { id: 1005, name: "细跟通勤高跟鞋 7cm", categoryId: 1332, brandId: 2, price: "359.00", originPrice: "459.00", sales: 431, stock: 52, image: "heel-01", status: "ON_SALE", tags: [], onSaleDays: 22,
      params: { "跟高": "7cm", "鞋面": "超纤皮", "产地": "四川" } },
    { id: 1006, name: "大容量双肩背包 15.6英寸", categoryId: 1341, brandId: 3, price: "259.00", originPrice: "349.00", sales: 1533, stock: 88, image: "backpack-01", status: "ON_SALE", tags: ["通勤好物"], onSaleDays: 5,
      params: { "容量": "26L", "适配": "15.6 英寸笔记本", "面料": "防泼水尼龙" } },
    { id: 1007, name: "头戴式主动降噪耳机", categoryId: 1211, brandId: 1, price: "899.00", originPrice: "1099.00", sales: 908, stock: 45, image: "headphone-01", status: "ON_SALE", tags: ["降噪"], onSaleDays: 3,
      params: { "降噪深度": "-42dB", "续航": "38 小时", "连接": "蓝牙 5.3" } },
    { id: 1008, name: "简约石英腕表 真皮表带", categoryId: 1231, brandId: 2, price: "559.00", originPrice: "799.00", sales: 366, stock: 27, image: "watch-01", status: "ON_SALE", tags: [], onSaleDays: 27,
      params: { "机芯": "日本石英", "表带": "头层牛皮", "防水": "3ATM" } },
    { id: 1009, name: "偏光太阳镜 防紫外线", categoryId: 1351, brandId: 2, price: "189.00", originPrice: "259.00", sales: 1442, stock: 8, image: "sunglass-01", status: "ON_SALE", tags: ["库存紧张"], onSaleDays: 34,
      params: { "镜片": "TAC 偏光", "防护": "UV400", "框型": "方框" } },
    { id: 1010, name: "微单数码相机 含套机镜头", categoryId: 1212, brandId: 1, price: "5299.00", originPrice: "5999.00", sales: 76, stock: 12, image: "camera-01", status: "ON_SALE", tags: ["旗舰"], onSaleDays: 11,
      params: { "传感器": "APS-C 2420 万", "视频": "4K 30fps", "防抖": "机身五轴" } },
    { id: 1011, name: "机械键盘 87键 茶轴", categoryId: 1221, brandId: 1, price: "349.00", originPrice: "459.00", sales: 2210, stock: 96, image: "keyboard-01", status: "ON_SALE", tags: ["热销"], onSaleDays: 6,
      params: { "轴体": "茶轴", "配列": "87 键", "连接": "有线 / 蓝牙双模" } },
    { id: 1012, name: "无线静音鼠标 双模", categoryId: 1221, brandId: 1, price: "129.00", originPrice: "169.00", sales: 3187, stock: 340, image: "mouse-01", status: "ON_SALE", tags: [], onSaleDays: 19,
      params: { "连接": "2.4G / 蓝牙", "静音": "支持", "续航": "12 个月" } },
    { id: 1013, name: "国AA级护眼台灯 无极调光", categoryId: 1232, brandId: 3, price: "269.00", originPrice: "359.00", sales: 812, stock: 74, image: "lamp-01", status: "ON_SALE", tags: [], onSaleDays: 13,
      params: { "照度等级": "国AA级", "色温": "3000-5000K", "显色指数": "Ra95" } },
    { id: 1014, name: "人体工学电脑椅 网布透气", categoryId: 1411, brandId: 3, price: "1099.00", originPrice: "1499.00", sales: 233, stock: 19, image: "chair-01", status: "ON_SALE", tags: ["大件包邮"], onSaleDays: 41,
      params: { "靠背": "网布", "调节": "4D 扶手 / 腰托", "承重": "150kg" } },
    { id: 1015, name: "陶瓷马克杯 350ml 情侣款", categoryId: 1421, brandId: 3, price: "59.00", originPrice: "89.00", sales: 4520, stock: 460, image: "mug-01", status: "ON_SALE", tags: ["爆款"], onSaleDays: 8,
      params: { "容量": "350ml", "材质": "釉下彩陶瓷", "可用": "微波炉 / 洗碗机" } },
    { id: 1016, name: "316不锈钢保温水壶 600ml", categoryId: 1421, brandId: 3, price: "139.00", originPrice: "199.00", sales: 1876, stock: 152, image: "bottle-01", status: "ON_SALE", tags: [], onSaleDays: 14,
      params: { "内胆": "316 不锈钢", "保温": "12 小时", "容量": "600ml" } },
    { id: 1017, name: "硬壳线装笔记本 A5", categoryId: 1431, brandId: 4, price: "45.00", originPrice: "69.00", sales: 2988, stock: 520, image: "notebook-01", status: "ON_SALE", tags: [], onSaleDays: 25,
      params: { "规格": "A5 / 160 页", "纸张": "100g 米白", "装订": "线装可平摊" } },
    { id: 1018, name: "精品手冲咖啡豆 250g 中深烘", categoryId: 1511, brandId: 4, price: "98.00", originPrice: "128.00", sales: 1642, stock: 0, image: "coffee-01", status: "ON_SALE", tags: ["售罄"], onSaleDays: 7,
      params: { "产地": "云南保山", "烘焙度": "中深烘", "风味": "坚果 / 焦糖" } }
  ];

  var home = {
    banners: [
      { id: 1, title: "百益开学季", subtitle: "数码好物 满 300 减 50", image: "banner-01", linkType: 2, linkValue: "12" },
      { id: 2, title: "秋冬新装上新", subtitle: "服饰鞋包 全场包邮", image: "banner-02", linkType: 2, linkValue: "13" },
      { id: 3, title: "居家焕新计划", subtitle: "厨房小电 低至 5 折", image: "banner-03", linkType: 2, linkValue: "14" }
    ],
    notices: [
      { id: 1, content: "全场包邮，无门槛" },
      { id: 2, content: "秒杀专区每晚 20:00 开抢" },
      { id: 3, content: "新用户注册即享首单立减" }
    ],
    navs: [
      { id: 1, name: "数码电器", icon: "smartphone", categoryId: 12 },
      { id: 2, name: "服饰鞋包", icon: "store", categoryId: 13 },
      { id: 3, name: "家居生活", icon: "house", categoryId: 14 },
      { id: 4, name: "食品饮料", icon: "boxes", categoryId: 15 },
      { id: 5, name: "秒杀专区", icon: "ticket-percent", href: "seckill.html" },
      { id: 6, name: "全部商品", icon: "layout-grid", href: "category.html" },
      { id: 7, name: "我的订单", icon: "package", href: "orders.html" },
      { id: 8, name: "个人中心", icon: "user", href: "profile.html" }
    ],
    floors: [
      { id: 1, title: "数码好物", categoryId: 12, sortField: "new", limit: 4 },
      { id: 2, title: "服饰新品", categoryId: 13, sortField: "sales", limit: 4 },
      { id: 3, title: "居家优选", categoryId: 14, sortField: "price_asc", limit: 4 },
      { id: 4, title: "食品饮料", categoryId: 15, sortField: "new", limit: 4 }
    ]
  };

  var user = { id: 1001, username: "harriol", nickname: "Harriol", phone: "13800138888", avatarText: "H", memberLevel: "普通会员" };

  var addresses = [
    { id: 33, receiverName: "李百益", receiverPhone: "13800138888", province: "浙江省", city: "杭州市", district: "西湖区", detail: "文三路 199 号 云谷大厦 12 层", isDefault: true },
    { id: 34, receiverName: "李百益", receiverPhone: "13800138888", province: "浙江省", city: "杭州市", district: "滨江区", detail: "江南大道 3588 号 恒生大厦 A 座 9 层", isDefault: false }
  ];

  var cart = [
    { id: 11, productId: 1004, quantity: 1, checked: true, invalid: false },
    { id: 12, productId: 1011, quantity: 1, checked: true, invalid: false },
    { id: 13, productId: 1015, quantity: 2, checked: true, invalid: false },
    { id: 14, productId: 1009, quantity: 1, checked: false, invalid: true, invalidReason: "商品库存不足" }
  ];

  var orders = [
    { orderNo: "2026092815301212345678", status: "PENDING_PAYMENT", source: "CART", createdAt: "2026-09-28 15:30:12",
      timeoutAt: "2026-09-28 15:45:12", totalAmount: "936.00", payAmount: "936.00", freightAmount: "0.00",
      items: [{ productId: 1004, quantity: 1 }, { productId: 1011, quantity: 1 }, { productId: 1015, quantity: 2 }],
      receiver: "李百益 138****8888 浙江省杭州市西湖区文三路 199 号 云谷大厦 12 层" },
    { orderNo: "2026092710123344556677", status: "PENDING_SHIPMENT", source: "BUY_NOW", createdAt: "2026-09-27 10:12:33",
      payTime: "2026-09-27 10:14:02", payType: "WECHAT", totalAmount: "899.00", payAmount: "899.00", freightAmount: "0.00",
      items: [{ productId: 1007, quantity: 1 }],
      receiver: "李百益 138****8888 浙江省杭州市西湖区文三路 199 号 云谷大厦 12 层" },
    { orderNo: "2026092509081122334455", status: "PENDING_RECEIPT", source: "CART", createdAt: "2026-09-25 09:08:11",
      payTime: "2026-09-25 09:09:40", shipTime: "2026-09-25 18:20:00", trackingNo: "SF1234567890123",
      autoReceiveAt: "2026-10-02 18:20:00", totalAmount: "358.00", payAmount: "358.00", freightAmount: "0.00",
      items: [{ productId: 1006, quantity: 1 }, { productId: 1017, quantity: 2 }],
      receiver: "李百益 138****8888 浙江省杭州市滨江区江南大道 3588 号 恒生大厦 A 座 9 层" },
    { orderNo: "2026091818203344556677", status: "COMPLETED", source: "CART", createdAt: "2026-09-18 18:20:33",
      payTime: "2026-09-18 18:22:10", shipTime: "2026-09-19 09:30:00", receiveTime: "2026-09-22 11:05:00",
      totalAmount: "199.00", payAmount: "199.00", freightAmount: "0.00",
      items: [{ productId: 1012, quantity: 1 }, { productId: 1015, quantity: 1 }],
      receiver: "李百益 138****8888 浙江省杭州市西湖区文三路 199 号 云谷大厦 12 层" },
    { orderNo: "2026091507510099887766", status: "CANCELLED", source: "CART", createdAt: "2026-09-15 07:51:00",
      cancelTime: "2026-09-15 08:06:00", cancelReason: "超时未支付，系统自动取消",
      totalAmount: "559.00", payAmount: "559.00", freightAmount: "0.00",
      items: [{ productId: 1008, quantity: 1 }],
      receiver: "李百益 138****8888 浙江省杭州市西湖区文三路 199 号 云谷大厦 12 层" }
  ];

  var seckill = {
    sessions: (function () {
      function fmt(d) {
        function p(n) { return (n < 10 ? "0" : "") + n; }
        return d.getFullYear() + "-" + p(d.getMonth() + 1) + "-" + p(d.getDate()) + " "
          + p(d.getHours()) + ":" + p(d.getMinutes());
      }
      function at(min) { return fmt(new Date(Date.now() + min * 60000)); }
      return [
        { id: 9001, name: "上一场", startTime: at(-240), endTime: at(-120), status: "ENDED" },
        { id: 9002, name: "本场抢购中", startTime: at(-30), endTime: at(90), status: "RUNNING" },
        { id: 9003, name: "下一场", startTime: at(120), endTime: at(240), status: "NOT_STARTED" }
      ];
    })(),
    items: [
      { id: 5001, productId: 1007, sessionId: 9002, seckillPrice: "599.00", originPrice: "899.00", total: 100, sold: 78, limitPerUser: 1 },
      { id: 5002, productId: 1011, sessionId: 9002, seckillPrice: "199.00", originPrice: "349.00", total: 200, sold: 168, limitPerUser: 2 },
      { id: 5003, productId: 1004, sessionId: 9002, seckillPrice: "169.00", originPrice: "299.00", total: 150, sold: 150, limitPerUser: 1 },
      { id: 5004, productId: 1013, sessionId: 9003, seckillPrice: "159.00", originPrice: "269.00", total: 120, sold: 12, limitPerUser: 1 },
      { id: 5005, productId: 1015, sessionId: 9003, seckillPrice: "29.00", originPrice: "59.00", total: 300, sold: 41, limitPerUser: 3 }
    ]
  };

  var statusText = {
    PENDING_PAYMENT: "待付款", PENDING_SHIPMENT: "待发货", PENDING_RECEIPT: "待收货",
    COMPLETED: "已完成", CANCELLED: "已取消"
  };
  var statusPill = {
    PENDING_PAYMENT: "pill-pay", PENDING_SHIPMENT: "pill-ship", PENDING_RECEIPT: "pill-recv",
    COMPLETED: "pill-done", CANCELLED: "pill-cancel"
  };

  return {
    categories: categories, brands: brands, products: products, home: home, user: user,
    addresses: addresses, cart: cart, orders: orders, seckill: seckill,
    statusText: statusText, statusPill: statusPill
  };
})();

/* ---------- 通用查询与格式化 ---------- */
window.P = function (id) {
  return window.DB.products.filter(function (p) { return p.id === Number(id); })[0] || null;
};
window.C = function (id) {
  return window.DB.categories.filter(function (c) { return c.id === Number(id); })[0] || null;
};
/* 图片路径：后台页面位于 /admin/ 子目录，需回到上一级再取资源 */
window.img = function (name) {
  var prefix = /\/(admin|miniapp)\//.test(window.location.pathname) ? "../assets/img/" : "assets/img/";
  return prefix + name + ".jpg";
};
window.yuan = function (s) { return '<span class="sym">¥</span>' + s; };
window.yuanPlain = function (s) { return "¥" + s; };
window.add = function (a, b) { return (Number(a) + Number(b)).toFixed(2); };
window.mul = function (a, n) { return (Number(a) * Number(n)).toFixed(2); };
window.sum = function (arr) {
  return arr.reduce(function (s, v) { return s + Number(v); }, 0).toFixed(2);
};
window.qs = function (k, d) {
  var v = new URLSearchParams(location.search).get(k);
  return v === null ? d : v;
};
