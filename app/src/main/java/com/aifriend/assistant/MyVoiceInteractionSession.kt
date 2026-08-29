package com.aifriend.assistant

import android.content.Context
import android.service.voice.VoiceInteractionSession

/**
 * 数字助理会话（最小实现）
 *
 * 读取屏幕数据走无障碍服务（NodeService），会话仅需可被系统创建即可，
 * 不实现具体 assist 处理逻辑。
 */
class MyVoiceInteractionSession(context: Context) : VoiceInteractionSession(context)