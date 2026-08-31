/**
 * 小a 客户端 - v0.5.0 (Sprint 2 增强)
 *
 * EC 通过 adb shell am broadcast 触发小a dump 节点
 * EC 读 /sdcard/xiaoa/screen.xml（UIAutomator XML 格式）+ meta.json
 *
 * v0.5.0 新增：
 *   - triggerAndWait() 一站式封装：trigger + 等节点 + 等小a UI 关闭
 *   - settleDelayMs 配置（默认 2000ms，覆盖小a scheduleFinish(1500)）
 */
var TRIGGER_ACTION = "com.aifriend.assistant.TRIGGER_ASSIST";
var TRIGGER_RECEIVER = "com.aifriend.assistant/.TriggerReceiver";

var NODE_DIR = "/storage/emulated/0/Android/data/com.aifriend.assistant/files/xiaoa/";
var NODE_META = NODE_DIR + "meta.json";
var NODE_XML = NODE_DIR + "screen.xml";

// 默认：小a MyVoiceInteractionSession.scheduleFinish(1500ms) + buffer
var DEFAULT_SETTLE_MS = 2000;

/**
 * 发送 trigger 广播
 * 兼容顺序：shell.exec > execAgentCommand > 直接调 java.lang.Runtime
 */
function triggerAssist() {
    var cmd = "am broadcast -a " + TRIGGER_ACTION + " -n " + TRIGGER_RECEIVER;
    try {
        if (typeof shell !== "undefined" && shell.execCommand) {
            logd("[xiaClient] shell.execCommand 触发: " + cmd);
            var r = shell.execCommand(cmd);
            logd("[xiaClient] shell.execCommand 返回: " + r);
            return true;
        } else if (typeof shell !== "undefined" && shell.exec) {
            logd("[xiaClient] shell.exec 触发: " + cmd);
            var r = shell.exec(cmd);
            logd("[xiaClient] shell.exec 返回: " + r);
            return true;
        } else {
            logw("[xiaClient] 无 shell 可用，请手动执行: " + cmd);
            return false;
        }
    } catch (e) {
        logw("[xiaClient] trigger 失败: " + e);
        return false;
    }
}

// 用 shell.execCommand("cat ...") 读取文件，绕过 EC rhino 的 file.readFile 权限问题
function readFileViaShell(path) {
    if (typeof shell !== "undefined" && shell.execCommand) {
        var r = shell.execCommand("cat \"" + path + "\"");
        if (r && r.length > 100) return r;
        return null;
    }
    return null;
}

function getCurrentMeta() {
    var txt = file.readFile(NODE_META);
    if (!txt) txt = readFileViaShell(NODE_META);
    if (!txt) return null;
    try { return JSON.parse(txt); } catch (e) { return null; }
}

function getCurrentXml() {
    var xml = file.readFile(NODE_XML);
    if (!xml) xml = readFileViaShell(NODE_XML);
    return xml;
}

function waitForFreshXml(timeoutMs) {
    var lastV = -1;
    var deadline = java.lang.System.currentTimeMillis() + timeoutMs;
    while (java.lang.System.currentTimeMillis() < deadline) {
        var meta = getCurrentMeta();
        if (meta && meta.v !== lastV) {
            var xml = getCurrentXml();
            if (xml && xml.indexOf("<hierarchy") >= 0) {
                return xml;
            }
        }
        java.lang.Thread.sleep(300);
    }
    return null;
}

/**
 * 一站式：trigger → 等节点 → 等小a UI 关闭
 *
 * 关键修复（v0.5.2）：
 *   - 拿节点后必须等 settleDelayMs（覆盖 scheduleFinish 1500ms + buffer）
 *   - 否则 BLE HID 点击会被 AssistPreviewPanel / AssistForegroundService 拦截
 *
 * @param {object} opts {timeoutMs=8000, settleMs=2000, reason=""}
 * @returns {string|null} XML 字符串，失败返回 null
 */
function triggerAndWait(opts) {
    opts = opts || {};
    var timeoutMs = opts.timeoutMs || 8000;
    var settleMs = opts.settleMs || DEFAULT_SETTLE_MS;
    var reason = opts.reason || "";

    logd("[xiaClient] triggerAndWait" + (reason ? " [" + reason + "]" : ""));
    if (!triggerAssist()) {
        logw("[xiaClient] triggerAndWait: trigger 失败");
        return null;
    }
    var xml = waitForFreshXml(timeoutMs);
    if (!xml) {
        logw("[xiaClient] triggerAndWait: 未拿到新 XML (timeout=" + timeoutMs + "ms)");
        return null;
    }
    logd("[xiaClient] 拿到新 XML (" + xml.length + " bytes)，等 " + settleMs + "ms (小a UI 关闭)");
    java.lang.Thread.sleep(settleMs);
    return xml;
}

module.exports = {
    triggerAssist: triggerAssist,
    getCurrentMeta: getCurrentMeta,
    getCurrentXml: getCurrentXml,
    waitForFreshXml: waitForFreshXml,
    triggerAndWait: triggerAndWait,
    DEFAULT_SETTLE_MS: DEFAULT_SETTLE_MS,
    TRIGGER_ACTION: TRIGGER_ACTION,
    TRIGGER_RECEIVER: TRIGGER_RECEIVER
};
