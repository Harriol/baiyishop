/* 百益商城 · 页面通用小工具（替换原型 mock 数据层的工具函数） */
var LOCAL_IMAGES = ["headphone-01", "sneaker-01", "keyboard-01", "jacket-01", "mug-01", "watch-01",
  "backpack-01", "camera-01", "chair-01", "coffee-01", "jeans-01", "lamp-01",
  "mouse-01", "notebook-01", "sunglass-01", "tee-01", "bottle-01", "heel-01"];

function imgPrefix() {
  return /\/(admin|miniapp)\//.test(window.location.pathname) ? "../assets/img/" : "assets/img/";
}

/** 把后端返回的图片地址落到本地占位图：MinIO 示例地址/空值都回退，保证联调不出现裂图 */
function localImage(seed, fallback) {
  var name = fallback || LOCAL_IMAGES[Math.abs(hashCode(String(seed || ""))) % LOCAL_IMAGES.length];
  return imgPrefix() + name + ".jpg";
}

function hashCode(text) {
  var h = 0;
  for (var i = 0; i < text.length; i++) {
    h = (h * 31 + text.charCodeAt(i)) | 0;
  }
  return h;
}

/* 图片：完整可达 URL 直接用；示例 minio 地址与裸文件名回退本地占位图 */
window.img = function (name, fallback) {
  if (!name) return localImage("", fallback);
  if (/^https?:\/\//i.test(name)) {
    // 只有明显的示例占位地址（https://minio/xxx）才回退；真实的 MinIO / CDN 地址原样使用
    if (/^https?:\/\/minio[./]/i.test(name)) {
      return localImage(name, fallback);
    }
    return name;
  }
  if (/\.(jpe?g|png|webp|gif|svg)$/i.test(name)) return imgPrefix() + name;
  return localImage(name, fallback);
};

/* 图片加载失败（对象存储没起或地址失效）时回退到本地占位图，页面不留裂图 */
document.addEventListener("error", function (event) {
  var el = event.target;
  if (!el || el.tagName !== "IMG" || el.dataset.imgFallback === "1") return;
  el.dataset.imgFallback = "1";
  el.src = localImage(el.getAttribute("src") || "", "tee-01");
}, true);

/** HTML 转义：后端数据（商品名、备注等）拼进 innerHTML 前使用 */
window.esc = function (value) {
  return String(value == null ? "" : value).replace(/[&<>"']/g, function (c) {
    return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
  });
};
window.qs = function (k, d) {
  var v = new URLSearchParams(location.search).get(k);
  return v === null ? d : v;
};
/* 金额展示：后端一律「分」，这里格式化为两位小数的「元」 */
window.yuan = function (fen) { return window.Store.moneyHtml(fen); };
window.yuanPlain = function (fen) { return window.Store.moneyPlain(fen); };
window.add = function (a, b) { return Number(a || 0) + Number(b || 0); };
window.mul = function (a, n) { return Number(a || 0) * Number(n || 0); };
window.sum = function (arr) {
  return (arr || []).reduce(function (s, v) { return s + Number(v || 0); }, 0);
};
/* 时间：后端返回 ISO-8601（无时区，语义为 Asia/Shanghai），转成本地 Date */
window.parseTime = function (value) {
  if (!value) return null;
  if (value instanceof Date) return value;
  var text = String(value).trim().replace(" ", "T");
  var date = new Date(text);
  return isNaN(date.getTime()) ? null : date;
};
window.formatTime = function (value) {
  var date = window.parseTime(value);
  if (!date) return "";
  function two(n) { return (n < 10 ? "0" : "") + n; }
  return date.getFullYear() + "-" + two(date.getMonth() + 1) + "-" + two(date.getDate()) + " "
    + two(date.getHours()) + ":" + two(date.getMinutes()) + ":" + two(date.getSeconds());
};
/** 统一的失败提示：优先用后端返回的中文 message */
window.showError = function (error) {
  window.toast((error && error.message) || "操作失败，请稍后重试", "err");
};
/** 需要登录才能继续的页面调用它（未登录直接跳登录页） */
window.requireLogin = function () {
  if (!window.Api.isLoggedIn()) {
    location.href = window.Api.loginUrl();
    return false;
  }
  return true;
};
