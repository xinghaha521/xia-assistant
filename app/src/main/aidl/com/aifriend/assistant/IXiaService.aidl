// IXiaService.aidl
// 小a 对外暴露的 Binder 接口（仿 vis VoiceCommandProxyService）
// EC 主程序通过 bindService 拿到此接口的远程引用

package com.aifriend.assistant;

import com.aifriend.assistant.UiObjectLite;
import com.aifriend.assistant.IXiaCallback;

interface IXiaService {
    void triggerAssistSession();
    List<UiObjectLite> getCurrentSnapshot();
    void registerCallback(IXiaCallback callback);
    void unregisterCallback(IXiaCallback callback);
    int getSnapshotVersion();
}