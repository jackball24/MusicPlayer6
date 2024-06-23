package org.akanework.gramophone.logic

import android.app.Application
import android.app.NotificationManager
import android.media.ThumbnailUtils
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatDelegate
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.preference.PreferenceManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asCoilImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.NullRequestDataException
import coil3.size.pxOrElse
import coil3.util.Logger
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

/**
 * MusicPlayer
 *
 * @author 时空L0k1
 */
class GramophoneApplication : Application(), SingletonImageLoader.Factory {

    companion object {
        private const val TAG = "GramophoneApplication"
    }

    // Application创建时调用
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        // 设置BugHandlerActivity来处理未捕获的异常
        Thread.setDefaultUncaughtExceptionHandler { _, paramThrowable ->
            val exceptionMessage = Log.getStackTraceString(paramThrowable)
            val threadName = Thread.currentThread().name
            Log.e(TAG, "Error on thread $threadName:\n $exceptionMessage")

            exitProcess(10)
        }
        super.onCreate()
        // 在设置StrictMode之前加载首选项
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // 如果需要缺少OnDestroy调用的解决方法，取消默认通知ID的通知
        if (needsMissingOnDestroyCallWorkarounds()) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        }

        // 应用启动时设置应用主题
        when (prefs.getStringStrict("theme_mode", "0")) {
            "0" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }

            "1" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }

            "2" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }

    // 创建新的ImageLoader
    @kotlin.OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .diskCache(null)
            .components {
                // 如果支持ScopedStorageV1，则添加自定义的Fetcher
                if (hasScopedStorageV1()) {
                    add(Fetcher.Factory { data, options, _ ->
                        if (data !is Pair<*, *>) return@Factory null
                        val size = data.second
                        if (size !is Size?) return@Factory null
                        val file = data.first as? File ?: return@Factory null
                        return@Factory Fetcher {
                            ImageFetchResult(
                                ThumbnailUtils.createAudioThumbnail(file, options.size.let {
                                    Size(it.width.pxOrElse { size?.width ?: 10000 },
                                        it.height.pxOrElse { size?.height ?: 10000 })
                                }, null).asCoilImage(), true, DataSource.DISK)
                        }
                    })
                }
            }
            .run {
                // 设置日志记录器
                logger(object : Logger {
                    override var minLevel = Logger.Level.Verbose
                    override fun log(
                        tag: String,
                        level: Logger.Level,
                        message: String?,
                        throwable: Throwable?
                    ) {
                        if (level < minLevel) return
                        val priority = level.ordinal + 2 // 根据日志级别设置优先级
                        if (message != null) {
                            Log.println(priority, tag, message)
                        }
                        // 保持日志可读，忽略正常事件的堆栈跟踪
                        if (throwable != null && throwable !is NullRequestDataException
                            && (throwable !is IOException
                                    || throwable.message != "No album art found")) {
                            Log.println(priority, tag, Log.getStackTraceString(throwable))
                        }
                    }
                })
            }
            .build()
    }
}

