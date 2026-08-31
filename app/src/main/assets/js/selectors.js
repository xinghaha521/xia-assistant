/**
 * EC JS 伪选择器 - v0.6.0 (Sprint 2)
 *
 * 解析 UIAutomator XML（兼容 uiautomator dump 输出 + 小a AssistStructure 格式）
 *
 * 过滤器（25 个）：
 *   基础:     text / textContains / desc / descContains / id / className / classNameRegex / pkg / pkgRegex / clickable / focusable / enabled
 *   模糊:     textStartsWith / textEndsWith / textRegex / descRegex / idStartsWith / idRegex
 *   几何:     boundsInRegion(x1,y1,x2,y2) / boundsIntersect(...) / minSize(w,h) / maxSize(w,h)
 *   关系:     childOf(parent) / parentOf(child) / nth(n) / last()
 *   状态:     hasText / hasDesc / hasId / hasClass
 *
 * 仿 EC 原生选择器 API（id("xxx").text("yyy").clickable(true).find()）
 *
 * 仿 vis FilterEngine 的 JS 版
 */

/**
 * 解析 UIAutomator XML
 * @param {string} xml UIAutomator XML 字符串
 * @returns {Array<{bounds, cls, text, desc, id, pkg, clickable, focusable, enabled}>}
 */
function parseUiAutomatorXml(xml) {
    var nodes = [];
    if (!xml) return nodes;
    var re = /<node\b([^>]*?)\/?\s*>/g;
    var match;
    while ((match = re.exec(xml)) != null) {
        var attrs = match[1];
        function attr(name) {
            var re2 = new RegExp('\\b' + name + '="([^"]*)"');
            var m = re2.exec(attrs);
            if (!m) return "";
            return decodeXml(m[1]);
        }
        var boundsStr = attr("bounds");
        var b = parseBounds(boundsStr);
        if (!b) continue;
        nodes.push({
            bounds: b,
            cls: attr("class"),
            text: attr("text"),
            desc: attr("content-desc"),
            id: attr("resource-id"),
            pkg: attr("package"),
            clickable: attr("clickable") === "true",
            focusable: attr("focusable") === "true",
            enabled: attr("enabled") !== "false"
        });
    }
    return nodes;
}

function parseBounds(s) {
    if (!s) return null;
    var m = /\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]/.exec(s);
    if (!m) return null;
    return {
        l: parseInt(m[1], 10),
        t: parseInt(m[2], 10),
        r: parseInt(m[3], 10),
        b: parseInt(m[4], 10)
    };
}

function decodeXml(s) {
    if (!s) return "";
    return s
        .replace(/&quot;/g, '"')
        .replace(/&apos;/g, "'")
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&amp;/g, "&");
}

function boundsWidth(b) { return b.r - b.l; }
function boundsHeight(b) { return b.b - b.t; }
function boundsContains(outer, inner) {
    return outer.l <= inner.l && outer.t <= inner.t &&
           outer.r >= inner.r && outer.b >= inner.b;
}
function boundsIntersect(a, b) {
    return !(a.r < b.l || a.l > b.r || a.b < b.t || a.t > b.b);
}

// 节点中心点 + ±3 随机偏移（vis/夜色 同款防封）
function center(bounds, rnd) {
    var r = rnd || Math.random;
    var x = Math.floor((bounds.l + bounds.r) / 2) + Math.floor(r() * 7) - 3;
    var y = Math.floor((bounds.t + bounds.b) / 2) + Math.floor(r() * 7) - 3;
    return { x: x, y: y };
}

// ========== 基础过滤器（10 个） ==========

function findByText(nodes, text) {
    if (!text) return [];
    return nodes.filter(function (n) { return n.text === text; });
}

function findByTextContains(nodes, sub) {
    if (!sub) return [];
    return nodes.filter(function (n) {
        return n.text && n.text.indexOf(sub) >= 0;
    });
}

function findByDesc(nodes, desc) {
    if (!desc) return [];
    return nodes.filter(function (n) { return n.desc === desc; });
}

function findByDescContains(nodes, sub) {
    if (!sub) return [];
    return nodes.filter(function (n) {
        return n.desc && n.desc.indexOf(sub) >= 0;
    });
}

function findById(nodes, id) {
    if (!id) return [];
    return nodes.filter(function (n) {
        if (!n.id) return false;
        return n.id === id || n.id.indexOf(id) >= 0;
    });
}

function findByClass(nodes, cls) {
    if (!cls) return [];
    return nodes.filter(function (n) {
        if (!n.cls) return false;
        return n.cls === cls || n.cls.indexOf(cls) >= 0;
    });
}

function findByClassRegex(nodes, pattern) {
    if (!pattern) return [];
    try {
        var re = new RegExp(pattern);
        return nodes.filter(function (n) {
            return n.cls && re.test(n.cls);
        });
    } catch (e) {
        logw("[selectors] clzRegex 错误: " + e);
        return [];
    }
}

function findByClickable(nodes, clickable) {
    return nodes.filter(function (n) { return n.clickable === (clickable !== false); });
}

function findByFocusable(nodes, focusable) {
    return nodes.filter(function (n) { return n.focusable === (focusable !== false); });
}

function findByEnabled(nodes, enabled) {
    return nodes.filter(function (n) { return n.enabled === (enabled !== false); });
}

function findByPackage(nodes, pkg) {
    if (!pkg) return nodes;
    return nodes.filter(function (n) { return n.pkg === pkg; });
}

function findByPkgRegex(nodes, pattern) {
    if (!pattern) return [];
    try {
        var re = new RegExp(pattern);
        return nodes.filter(function (n) {
            return n.pkg && re.test(n.pkg);
        });
    } catch (e) {
        logw("[selectors] pkgRegex 错误: " + e);
        return [];
    }
}

// ========== 模糊/正则 过滤器（5 个） ==========

function findByTextStartsWith(nodes, prefix) {
    if (!prefix) return [];
    return nodes.filter(function (n) {
        return n.text && n.text.indexOf(prefix) === 0;
    });
}

function findByTextEndsWith(nodes, suffix) {
    if (!suffix) return [];
    return nodes.filter(function (n) {
        return n.text && n.text.lastIndexOf(suffix) >= 0 &&
               n.text.lastIndexOf(suffix) === n.text.length - suffix.length;
    });
}

function findByTextRegex(nodes, pattern) {
    if (!pattern) return [];
    try {
        var re = new RegExp(pattern);
        return nodes.filter(function (n) {
            return n.text && re.test(n.text);
        });
    } catch (e) {
        logw("[selectors] textRegex 错误: " + e);
        return [];
    }
}

function findByDescRegex(nodes, pattern) {
    if (!pattern) return [];
    try {
        var re = new RegExp(pattern);
        return nodes.filter(function (n) {
            return n.desc && re.test(n.desc);
        });
    } catch (e) {
        logw("[selectors] descRegex 错误: " + e);
        return [];
    }
}

function findByIdStartsWith(nodes, prefix) {
    if (!prefix) return [];
    return nodes.filter(function (n) {
        return n.id && n.id.indexOf(prefix) === 0;
    });
}

function findByIdRegex(nodes, pattern) {
    if (!pattern) return [];
    try {
        var re = new RegExp(pattern);
        return nodes.filter(function (n) {
            return n.id && re.test(n.id);
        });
    } catch (e) {
        logw("[selectors] idRegex 错误: " + e);
        return [];
    }
}

// ========== 几何 过滤器（4 个） ==========

/**
 * bounds 完全包含在指定区域内
 * @param {number} l left
 * @param {number} t top
 * @param {number} r right
 * @param {number} b bottom
 */
function findInRegion(nodes, l, t, r, b) {
    return nodes.filter(function (n) {
        return n.bounds.l >= l && n.bounds.t >= t &&
               n.bounds.r <= r && n.bounds.b <= b;
    });
}

/**
 * bounds 与指定区域相交（部分覆盖）
 */
function findIntersect(nodes, l, t, r, b) {
    return nodes.filter(function (n) {
        return !(n.bounds.r < l || n.bounds.l > r ||
                 n.bounds.b < t || n.bounds.t > b);
    });
}

/**
 * 最小尺寸（width, height 像素）
 */
function findByMinSize(nodes, w, h) {
    return nodes.filter(function (n) {
        return boundsWidth(n.bounds) >= w && boundsHeight(n.bounds) >= h;
    });
}

/**
 * 最大尺寸
 */
function findByMaxSize(nodes, w, h) {
    return nodes.filter(function (n) {
        return boundsWidth(n.bounds) <= w && boundsHeight(n.bounds) <= h;
    });
}

// ========== 关系 过滤器（4 个） ==========

/**
 * 子节点：父节点的 bounds 包含子节点 bounds
 * @param {object} parent 父节点（必须带 bounds）
 */
function findChildOf(nodes, parent) {
    if (!parent || !parent.bounds) return [];
    return nodes.filter(function (n) {
        return boundsContains(parent.bounds, n.bounds);
    });
}

/**
 * 父节点：子节点的 bounds 包含于父节点 bounds
 */
function findParentOf(nodes, child) {
    if (!child || !child.bounds) return [];
    return nodes.filter(function (n) {
        return boundsContains(n.bounds, child.bounds);
    });
}

/**
 * 第 N 个匹配（0-based）
 */
function findNth(nodes, n) {
    if (n < 0) n = nodes.length + n;
    return (n >= 0 && n < nodes.length) ? [nodes[n]] : [];
}

/**
 * 最后一个匹配
 */
function findLast(nodes) {
    return nodes.length > 0 ? [nodes[nodes.length - 1]] : [];
}

// ========== 状态 过滤器（4 个） ==========

function findHasText(nodes) {
    return nodes.filter(function (n) { return !!n.text; });
}

function findHasDesc(nodes) {
    return nodes.filter(function (n) { return !!n.desc; });
}

function findHasId(nodes) {
    return nodes.filter(function (n) { return !!n.id; });
}

function findHasClass(nodes) {
    return nodes.filter(function (n) { return !!n.cls; });
}

// ========== 链式调用（仿 EC 选择器） ==========

/**
 * 核心 selector — 支持 30 个 conditions key
 * @param {Array} nodes
 * @param {object} c {
 *   text, textContains, textStartsWith, textEndsWith, textRegex,
 *   desc, descContains, descRegex,
 *   id, idStartsWith, idRegex,
 *   className, clzRegex,
 *   pkg, pkgRegex,
 *   clickable, focusable, enabled,
 *   region: [l,t,r,b], intersect: [l,t,r,b],
 *   minSize: [w,h], maxSize: [w,h],
 *   hasText, hasDesc, hasId, hasClass,
 *   nth, last
 * }
 */
function selector(nodes, c) {
    if (!c) return nodes.slice();
    var r = nodes;

    // 文本
    if (c.text) r = findByText(r, c.text);
    if (c.textContains) r = findByTextContains(r, c.textContains);
    if (c.textStartsWith) r = findByTextStartsWith(r, c.textStartsWith);
    if (c.textEndsWith) r = findByTextEndsWith(r, c.textEndsWith);
    if (c.textRegex) r = findByTextRegex(r, c.textRegex);

    // desc
    if (c.desc) r = findByDesc(r, c.desc);
    if (c.descContains) r = findByDescContains(r, c.descContains);
    if (c.descRegex) r = findByDescRegex(r, c.descRegex);

    // id / class / pkg
    if (c.id) r = findById(r, c.id);
    if (c.idStartsWith) r = findByIdStartsWith(r, c.idStartsWith);
    if (c.idRegex) r = findByIdRegex(r, c.idRegex);
    if (c.className) r = findByClass(r, c.className);
    if (c.clzRegex) r = findByClassRegex(r, c.clzRegex);
    if (c.pkg) r = findByPackage(r, c.pkg);
    if (c.pkgRegex) r = findByPkgRegex(r, c.pkgRegex);

    // 布尔状态
    if (c.clickable !== undefined) r = findByClickable(r, c.clickable);
    if (c.focusable !== undefined) r = findByFocusable(r, c.focusable);
    if (c.enabled !== undefined) r = findByEnabled(r, c.enabled);

    // 几何
    if (c.region && c.region.length === 4) r = findInRegion(r, c.region[0], c.region[1], c.region[2], c.region[3]);
    if (c.intersect && c.intersect.length === 4) r = findIntersect(r, c.intersect[0], c.intersect[1], c.intersect[2], c.intersect[3]);
    if (c.minSize && c.minSize.length === 2) r = findByMinSize(r, c.minSize[0], c.minSize[1]);
    if (c.maxSize && c.maxSize.length === 2) r = findByMaxSize(r, c.maxSize[0], c.maxSize[1]);

    // 字段非空
    if (c.hasText) r = findHasText(r);
    if (c.hasDesc) r = findHasDesc(r);
    if (c.hasId) r = findHasId(r);
    if (c.hasClass) r = findHasClass(r);

    // 序号
    if (c.last) r = findLast(r);
    else if (typeof c.nth === "number") r = findNth(r, c.nth);

    return r;
}

/**
 * 找第一个匹配
 */
function first(nodes, conditions) {
    var list = selector(nodes, conditions);
    return list.length > 0 ? list[0] : null;
}

/**
 * 找第一个匹配的 bounds
 */
function firstBounds(nodes, conditions) {
    var node = first(nodes, conditions);
    return node ? node.bounds : null;
}

/**
 * 打印节点摘要（调试用）
 */
function dump(node, idx) {
    if (!node) return "";
    return "[" + (idx || 0) + "] " + (node.text || node.desc || node.id || "<空>") +
        " | cls=" + (node.cls || "") +
        " | id=" + (node.id || "") +
        " | pkg=" + (node.pkg || "") +
        " | [" + node.bounds.l + "," + node.bounds.t + "][" + node.bounds.r + "," + node.bounds.b + "]" +
        " | clk=" + node.clickable + " foc=" + node.focusable + " en=" + node.enabled;
}

/**
 * 点击函数指针 - 默认 EC 全局 clickPoint（无障碍/代理/root）
 * 业务代码可在启动时调用 setClickHandler() 切换为 bleHid.click 等
 */
var _clickHandler = function (x, y) { return clickPoint(x, y); };

/**
 * 切换点击实现
 * @param {function(x, y): boolean} handler
 */
function setClickHandler(handler) {
    if (typeof handler === "function") {
        _clickHandler = handler;
        logd("[selectors] clickHandler 已切换: " + (handler.name || "anonymous"));
    } else {
        logw("[selectors] setClickHandler 参数必须是函数");
    }
}

/**
 * 找第一个匹配 + 点击中心
 */
function clickOne(nodes, conditions, rnd) {
    var node = first(nodes, conditions);
    if (!node) {
        logw("[selectors] 没找到匹配: " + JSON.stringify(conditions));
        return false;
    }
    var pt = center(node.bounds, rnd || Math.random);
    logd("[selectors] click (" + pt.x + "," + pt.y + ") " + dump(node));
    return _clickHandler(pt.x, pt.y);
}

/**
 * 找所有匹配 + 依次点击（带间隔）
 * @param {number} delayMs 每次点击间隔
 */
function clickAll(nodes, conditions, delayMs) {
    var list = selector(nodes, conditions);
    logd("[selectors] clickAll 匹配 " + list.length + " 个");
    var results = [];
    for (var i = 0; i < list.length; i++) {
        var pt = center(list[i].bounds, Math.random);
        logd("[selectors] clickAll [" + i + "] (" + pt.x + "," + pt.y + ")");
        results.push(_clickHandler(pt.x, pt.y));
        if (delayMs && i < list.length - 1) java.lang.Thread.sleep(delayMs);
    }
    return results;
}

/**
 * 根据文本查找最佳可点击节点（解决「文本节点坐标不准」问题）
 *
 * 策略：
 *   1. 优先找 clickable + textContains 匹配
 *   2. 再找 clickable + descContains 匹配
 *   3. 兜底：找到文本节点 → 往上找 clickable 父节点（选面积最小的）
 *   4. 实在没有就返回文本节点本身
 *
 * @param {Array} nodes 解析后的节点数组
 * @param {string} text 要查找的文本
 * @returns {object|null} 最佳节点（带 bounds）
 */
function findBestClickable(nodes, text) {
    if (!text) return null;

    var list = selector(nodes, { textContains: text, clickable: true });
    if (list.length > 0) return list[0];

    list = selector(nodes, { descContains: text, clickable: true });
    if (list.length > 0) return list[0];

    list = selector(nodes, { textContains: text });
    if (list.length === 0) return null;

    var textNode = list[0];
    var parents = findParentOf(nodes, textNode);
    var clickableParents = findByClickable(parents, true);
    if (clickableParents.length > 0) {
        clickableParents.sort(function (a, b) {
            return (boundsWidth(a.bounds) * boundsHeight(a.bounds)) -
                   (boundsWidth(b.bounds) * boundsHeight(b.bounds));
        });
        return clickableParents[0];
    }
    return textNode;
}

module.exports = {
    // 解析
    parseUiAutomatorXml: parseUiAutomatorXml,
    parseBounds: parseBounds,
    decodeXml: decodeXml,
    boundsWidth: boundsWidth,
    boundsHeight: boundsHeight,
    center: center,

    // 基础 10
    findByText: findByText,
    findByTextContains: findByTextContains,
    findByDesc: findByDesc,
    findByDescContains: findByDescContains,
    findById: findById,
    findByClass: findByClass,
    findByClassRegex: findByClassRegex,
    findByClickable: findByClickable,
    findByFocusable: findByFocusable,
    findByEnabled: findByEnabled,
    findByPackage: findByPackage,
    findByPkgRegex: findByPkgRegex,

    // 模糊/正则 8
    findByTextStartsWith: findByTextStartsWith,
    findByTextEndsWith: findByTextEndsWith,
    findByTextRegex: findByTextRegex,
    findByDescRegex: findByDescRegex,
    findByIdStartsWith: findByIdStartsWith,
    findByIdRegex: findByIdRegex,

    // 几何 4
    findInRegion: findInRegion,
    findIntersect: findIntersect,
    findByMinSize: findByMinSize,
    findByMaxSize: findByMaxSize,

    // 关系 4
    findChildOf: findChildOf,
    findParentOf: findParentOf,
    findNth: findNth,
    findLast: findLast,

    // 状态 4
    findHasText: findHasText,
    findHasDesc: findHasDesc,
    findHasId: findHasId,
    findHasClass: findHasClass,

    // 组合
    selector: selector,
    first: first,
    firstBounds: firstBounds,
    dump: dump,
    clickOne: clickOne,
    clickAll: clickAll,
    setClickHandler: setClickHandler,
    findBestClickable: findBestClickable
};
