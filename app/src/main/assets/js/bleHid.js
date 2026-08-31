/**
 * BLE HID 适配层 - v0.5.0
 *
 * 作用：
 *   - 封装 EC 的 bleEvent 全局对象（脚本模式可用）
 *   - 提供 connect / click / isConnected / disconnect 接口
 *   - 点击失败时自动重连一次
 *
 * 设备：
 *   - 蓝牙 MAC: 28:84:85:43:64:BE
 *   - startConnect 参数留空 → EC 从 app 设置读取（后8位小写）
 *
 * 重要：
 *   - bleEvent 是 EC 内置全局对象，rhino 引擎可直接调用
 *   - 不需要 require 任何模块
 *   - 失败返回值是 string 错误信息，成功返回 null
 */

var BLE_DEVICE_NAME = "";     // 留空，用 EC app 设置里的蓝牙名（mac 后8位）
var BLE_TIMEOUT_MS = 15000;   // 连接超时
var BLE_AUTO_RECONNECT = true; // 点击失败自动重连

/**
 * 连接 BLE HID 设备
 * @returns {boolean} true=成功
 */
function connect() {
    try {
        // 已在连接则跳过
        if (isConnected()) {
            logd("[bleHid] 已在连接状态");
            return true;
        }

        // 先断开旧连接，避免缓存
        try { bleEvent.stopConnect(); } catch (e) { /* ignore */ }
        java.lang.Thread.sleep(500);

        logd("[bleHid] 开始连接 BLE HID 设备...");
        var cr = bleEvent.startConnect(BLE_DEVICE_NAME, false, BLE_TIMEOUT_MS);

        if (cr == null || cr === "") {
            logd("[bleHid] 连接成功");
            // 等连接稳定
            java.lang.Thread.sleep(800);
            return isConnected();
        } else {
            logw("[bleHid] 连接失败: " + cr);
            return false;
        }
    } catch (e) {
        logw("[bleHid] connect 异常: " + e);
        return false;
    }
}

/**
 * 检查连接状态
 * @returns {boolean}
 */
function isConnected() {
    try {
        return bleEvent.isConnected() === true;
    } catch (e) {
        return false;
    }
}

/**
 * 断开连接
 */
function disconnect() {
    try {
        var r = bleEvent.stopConnect();
        logd("[bleHid] 断开: " + r);
    } catch (e) {
        logw("[bleHid] disconnect 异常: " + e);
    }
}

/**
 * 真实点击（物理层）
 *
 * 注意：bleEvent.clickPoint 内部是同步阻塞 BLE 发送
 *       单次约 30-80ms，不会卡 UI 线程
 *
 * @param {number} x 横坐标（物理像素）
 * @param {number} y 纵坐标（物理像素）
 * @returns {boolean} true=成功
 */
function click(x, y) {
    if (!isConnected()) {
        logw("[bleHid] 未连接，尝试重连...");
        if (!connect()) {
            logw("[bleHid] 重连失败，跳过点击");
            return false;
        }
    }

    try {
        var r = bleEvent.clickPoint(x, y);
        if (r == null || r === "") {
            logd("[bleHid] click(" + x + "," + y + ") OK");
            return true;
        } else {
            logw("[bleHid] click 失败: " + r);
            // 自动重连 + 重试一次
            if (BLE_AUTO_RECONNECT) {
                logd("[bleHid] 自动重连后重试...");
                if (connect()) {
                    var r2 = bleEvent.clickPoint(x, y);
                    if (r2 == null || r2 === "") {
                        logd("[bleHid] 重试 click(" + x + "," + y + ") OK");
                        return true;
                    }
                }
            }
            return false;
        }
    } catch (e) {
        logw("[bleHid] click 异常: " + e);
        return false;
    }
}

/**
 * 长按
 * @param {number} x
 * @param {number} y
 * @param {number} ms
 * @returns {boolean}
 */
function press(x, y, ms) {
    if (!isConnected()) return false;
    try {
        var r = bleEvent.press(x, y, ms || 1000);
        return r == null || r === "";
    } catch (e) {
        logw("[bleHid] press 异常: " + e);
        return false;
    }
}

/**
 * 滑动
 * @param {number} x1
 * @param {number} y1
 * @param {number} x2
 * @param {number} y2
 * @param {number} ms
 * @returns {boolean}
 */
function swipe(x1, y1, x2, y2, ms) {
    if (!isConnected()) return false;
    try {
        var r = bleEvent.swipe(x1, y1, x2, y2, ms || 500);
        return r == null || r === "";
    } catch (e) {
        logw("[bleHid] swipe 异常: " + e);
        return false;
    }
}

module.exports = {
    connect: connect,
    isConnected: isConnected,
    disconnect: disconnect,
    click: click,
    press: press,
    swipe: swipe
};
