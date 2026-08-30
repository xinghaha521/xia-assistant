// IXiaCallback.aidl
// EC 注册的回调接口；cache 更新时由小a回调

package com.aifriend.assistant;

interface IXiaCallback {

    // 小a 拿到新的 AssistStructure 时回调
    void onSnapshotUpdated(int version);
}