// IXiaService.aidl
// 小a 对外暴露的 Binder 接口（仿 vis VoiceCommandProxyService）
// EC 主程序通过 bindService 拿到此接口的远程引用

package com.aifriend.assistant;

import com.aifriend.assistant.UiObjectLite;
import com.aifriend.assistant.IXiaCallback;

interface IXiaService {

    // 主动触发一次数字助理 session（VIS 等价 triggerNewSession）
    void triggerAssistSession();

    // 同步获取当前快照（VIS 等价 getAssistStructureCache）
    List<UiObjectLite> getCurrentSnapshot();

    // 注册回调
    void registerCallback(IXiaCallback callback);

    // 注销回调
    void unregisterCallback(IXiaCallback callback);

    // 获取快照版本号
    int getSnapshotVersion();
}