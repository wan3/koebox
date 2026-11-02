# 声匣 ProGuard 规则
# Media3 / Compose / AndroidX 都自带 consumer 规则，这里只补充本项目的少量需要

# 保留数据模型（JSON 手写序列化不用反射，但保留一份更稳）
-keep class com.koebox.app.data.** { *; }

# Media3 session 服务需要被系统按类名实例化
-keep class com.koebox.app.playback.PlaybackService { *; }

# Kotlin 协程内部反射兜底
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**
