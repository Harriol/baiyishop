/* 百益商城 · 前端 API 层（对接真实后端）
   约定：
   - 默认请求前缀 /api，由 Vite 开发代理转发到网关（http://localhost:8080），因此本地联调无跨域问题；
     部署到同域时把 window.BAIYI_API_BASE 设为网关地址即可。
   - 后端统一响应体 { code, message, data, success }：code=0 认为成功并**直接返回 data**；
     其余按业务失败抛出 Error（带 code/message，message 就是后端返回的中文提示）。
   - 令牌存 localStorage；收到 10002（登录失效）时清会话并跳登录页（可用 opts.silent 关闭）。 */
window.Api = (function () {
  var BASE = window.BAIYI_API_BASE || "/api";
  /* 前台与后台分开存会话：同一个浏览器同时登录前台用户与后台管理员时互不覆盖 */
  var NS = /\/admin\//.test(window.location.pathname) ? "baiyi.admin" : "baiyi";
  var ADMIN_PAGE = NS === "baiyi.admin";
  var TOKEN_KEY = NS + ".token";
  var REFRESH_KEY = NS + ".refresh";
  var USER_KEY = NS + ".user";

  function token() {
    return localStorage.getItem(TOKEN_KEY) || "";
  }

  function user() {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) || "null");
    } catch (e) {
      return null;
    }
  }

  function isLoggedIn() {
    return !!token();
  }

  /** 登录 / 刷新后保存会话 */
  function saveSession(data) {
    if (!data) return;
    if (data.accessToken) localStorage.setItem(TOKEN_KEY, data.accessToken);
    if (data.refreshToken) localStorage.setItem(REFRESH_KEY, data.refreshToken);
    if (data.user) localStorage.setItem(USER_KEY, JSON.stringify(data.user));
    if (data.admin) localStorage.setItem(USER_KEY, JSON.stringify(data.admin));
  }

  function saveUser(profile) {
    if (profile) localStorage.setItem(USER_KEY, JSON.stringify(profile));
  }

  function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
  }

  /** 请求级幂等键（下单 / 支付 / 抢购都要求 X-Request-Id） */
  function uuid() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID().replace(/-/g, "");
    return "req" + Date.now() + Math.random().toString(16).slice(2, 10);
  }

  function loginUrl() {
    var path = location.pathname;
    var page = path.split("/").pop() || "index.html";
    if (/\/miniapp\//.test(path)) {
      return "../login.html?next=" + encodeURIComponent("../miniapp/" + page + location.search);
    }
    // 后台页面同目录下的 login.html；前台页面根目录的 login.html
    return "login.html?next=" + encodeURIComponent(page + location.search);
  }

  function request(method, path, body, opts) {
    opts = opts || {};
    var headers = {};
    if (body !== undefined && body !== null) headers["Content-Type"] = "application/json";
    // 后台令牌只发给 /v1/admin/**：公开接口若收到后台令牌会被服务端判为 10002（受众不匹配），
    // 既拿不到数据，还会被误判成「登录已失效」而清掉会话
    if (token() && !(ADMIN_PAGE && !/^\/v1\/admin\//.test(path))) {
      headers.Authorization = "Bearer " + token();
    }
    if (opts.requestId) headers["X-Request-Id"] = opts.requestId;

    return fetch(BASE + path, {
      method: method,
      headers: headers,
      body: body === undefined || body === null ? undefined : JSON.stringify(body)
    }).then(function (response) {
      return response.text().then(function (text) {
        var payload = null;
        try {
          payload = text ? JSON.parse(text) : null;
        } catch (e) {
          payload = null;
        }
        if (!payload || typeof payload.code === "undefined") {
          var transportError = new Error("服务暂时不可用（HTTP " + response.status + "），请稍后重试");
          transportError.code = -1;
          throw transportError;
        }
        if (payload.code === 0) {
          return payload.data;
        }
        var error = new Error(payload.message || "操作失败，请稍后重试");
        error.code = payload.code;
        if (payload.code === 10002) {
          // 令牌失效必须清会话；silent 只用来抑制跳转（例如首页静默刷新登录态）
          clearSession();
          if (!opts.silent && !/login\.html$/.test(location.pathname)) {
            location.href = loginUrl();
          }
        }
        throw error;
      });
    });
  }

  function get(path, opts) {
    return request("GET", path, null, opts);
  }

  function post(path, body, opts) {
    return request("POST", path, body === undefined ? {} : body, opts);
  }

  function put(path, body, opts) {
    return request("PUT", path, body === undefined ? {} : body, opts);
  }

  function del(path, opts) {
    return request("DELETE", path, null, opts);
  }

  return {
    BASE: BASE,
    token: token,
    user: user,
    isLoggedIn: isLoggedIn,
    saveSession: saveSession,
    saveUser: saveUser,
    clearSession: clearSession,
    uuid: uuid,
    loginUrl: loginUrl,
    get: get,
    post: post,
    put: put,
    del: del,

    // ---------------- 领域方法 ----------------
    register: function (username, password, nickname) {
      return post("/v1/auth/register", { username: username, password: password, nickname: nickname });
    },
    login: function (username, password) {
      return post("/v1/auth/login", { username: username, password: password }).then(function (data) {
        saveSession(data);
        return data;
      });
    },
    wechatLogin: function (code, nickname) {
      return post("/v1/auth/wechat-login", { code: code, nickname: nickname }).then(function (data) {
        saveSession(data);
        return data;
      });
    },
    logout: function () {
      var refreshToken = localStorage.getItem(REFRESH_KEY);
      var done = function () {
        clearSession();
      };
      if (!refreshToken) {
        done();
        return Promise.resolve();
      }
      return post("/v1/auth/logout", { refreshToken: refreshToken }, { silent: true })
        .then(done, done);
    },
    profile: function (opts) {
      return get("/v1/users/me", opts).then(function (profile) {
        saveUser(profile);
        return profile;
      });
    },
    adminLogin: function (username, password) {
      return post("/v1/admin/auth/login", { username: username, password: password }).then(function (data) {
        saveSession(data);
        return data;
      });
    },
    adminProfile: function () {
      return get("/v1/admin/auth/me").then(function (profile) {
        saveUser(profile);
        return profile;
      });
    },
    /** 后台令牌没有 refreshToken，注销即清本地会话 */
    adminLogout: function () {
      clearSession();
      return Promise.resolve();
    }
  };
})();
