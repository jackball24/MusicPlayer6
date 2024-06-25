package org.akanework.gramophone.logic

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.IllegalSeekPositionException
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util.isBitmapFactorySupportedMimeType
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.preference.PreferenceManager
import coil3.BitmapImage
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.CircularShuffleOrder
import org.akanework.gramophone.logic.utils.LastPlayedManager
import org.akanework.gramophone.logic.utils.LrcUtils.extractAndParseLyrics
import org.akanework.gramophone.logic.utils.LrcUtils.loadAndParseLyricsFile
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.logic.utils.exoplayer.EndedWorkaroundPlayer
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneMediaSourceFactory
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneRenderFactory
import org.akanework.gramophone.ui.MainActivity
import kotlin.random.Random


/**
 * [GramophonePlaybackService] is a server service.
 * It's using exoplayer2 as its player backend.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class GramophonePlaybackService : MediaLibraryService(), MediaSessionService.Listener,
    MediaLibraryService.MediaLibrarySession.Callback, Player.Listener {

    companion object {
        private const val TAG = "GramoPlaybackService"
        private const val NOTIFY_CHANNEL_ID = "serviceFgsError"
        private const val NOTIFY_ID = 1
        private const val PENDING_INTENT_SESSION_ID = 0
        private const val PENDING_INTENT_NOTIFY_ID = 1
        private const val PLAYBACK_SHUFFLE_ACTION_ON = "shuffle_on"
        private const val PLAYBACK_SHUFFLE_ACTION_OFF = "shuffle_off"
        private const val PLAYBACK_REPEAT_OFF = "repeat_off"
        private const val PLAYBACK_REPEAT_ALL = "repeat_all"
        private const val PLAYBACK_REPEAT_ONE = "repeat_one"
        const val SERVICE_SET_TIMER = "set_timer"
        const val SERVICE_QUERY_TIMER = "query_timer"
        const val SERVICE_GET_LYRICS = "get_lyrics"
        const val SERVICE_TIMER_CHANGED = "changed_timer"
    }

    private var mediaSession: MediaLibrarySession? = null
    private var controller: MediaController? = null
    private var lyrics: MutableList<MediaStoreUtils.Lyric>? = null
    private var shuffleFactory:
            ((Int) -> ((CircularShuffleOrder) -> Unit) -> CircularShuffleOrder)? = null
    private lateinit var customCommands: List<CommandButton>
    private lateinit var handler: Handler
    private lateinit var nm: NotificationManagerCompat
    private lateinit var lastPlayedManager: LastPlayedManager
    private val lyricsLock = Semaphore(1)
    private lateinit var prefs: SharedPreferences

    // 获取重复播放命令
    private fun getRepeatCommand() =
        when (controller!!.repeatMode) {
            Player.REPEAT_MODE_OFF -> customCommands[2]
            Player.REPEAT_MODE_ALL -> customCommands[3]
            Player.REPEAT_MODE_ONE -> customCommands[4]
            else -> throw IllegalArgumentException()
        }

    // 获取随机播放命令
    private fun getShufflingCommand() =
        if (controller!!.shuffleModeEnabled)
            customCommands[1]
        else
            customCommands[0]

    private val timer: Runnable = Runnable {
        controller!!.pause()
        timerDuration = null
    }

    private var timerDuration: Long? = null
        set(value) {
            field = value
            if (value != null && value > 0) {
                handler.postDelayed(timer, value - System.currentTimeMillis())
            } else {
                handler.removeCallbacks(timer)
            }
            mediaSession!!.broadcastCustomCommand(
                SessionCommand(SERVICE_TIMER_CHANGED, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }

    // 监听耳机拔出事件
    private val headSetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action.equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                controller?.pause()
            }
        }
    }

    // 服务创建时初始化操作
    @OptIn(ExperimentalCoilApi::class)
    override fun onCreate() {
        handler = Handler(Looper.getMainLooper())
        super.onCreate()
        nm = NotificationManagerCompat.from(this)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setListener(this)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_gramophone_monochrome)
            }
        )
        if (mayThrowForegroundServiceStartNotAllowed()) {
            nm.createNotificationChannel(NotificationChannelCompat.Builder(
                NOTIFY_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH
            ).apply {
                setName(getString(R.string.fgs_failed_channel))
                setVibrationEnabled(true)
                setVibrationPattern(longArrayOf(0L, 200L))
                setLightsEnabled(false)
                setShowBadge(false)
                setSound(null, null)
            }.build())
        } else if (nm.getNotificationChannel(NOTIFY_CHANNEL_ID) != null) {
            nm.deleteNotificationChannel(NOTIFY_CHANNEL_ID)
        }

        // 设置自定义命令按钮
        customCommands =
            listOf(
                CommandButton.Builder() // shuffle currently disabled, click will enable
                    .setDisplayName(getString(R.string.shuffle))
                    .setSessionCommand(
                        SessionCommand(PLAYBACK_SHUFFLE_ACTION_ON, Bundle.EMPTY)
                    )
                    .setIconResId(R.drawable.ic_shuffle)
                    .build(),
                CommandButton.Builder() // shuffle currently enabled, click will disable
                    .setDisplayName(getString(R.string.shuffle))
                    .setSessionCommand(
                        SessionCommand(PLAYBACK_SHUFFLE_ACTION_OFF, Bundle.EMPTY)
                    )
                    .setIconResId(R.drawable.ic_shuffle_on)
                    .build(),
                CommandButton.Builder() // repeat currently disabled, click will repeat all
                    .setDisplayName(getString(R.string.repeat_mode))
                    .setSessionCommand(
                        SessionCommand(PLAYBACK_REPEAT_ALL, Bundle.EMPTY)
                    )
                    .setIconResId(R.drawable.ic_repeat)
                    .build(),
                CommandButton.Builder() // repeat all currently enabled, click will repeat one
                    .setDisplayName(getString(R.string.repeat_mode))
                    .setSessionCommand(
                        SessionCommand(PLAYBACK_REPEAT_ONE, Bundle.EMPTY)
                    )
                    .setIconResId(R.drawable.ic_repeat_on)
                    .build(),
                CommandButton.Builder() // repeat one currently enabled, click will disable
                    .setDisplayName(getString(R.string.repeat_mode))
                    .setSessionCommand(
                        SessionCommand(PLAYBACK_REPEAT_OFF, Bundle.EMPTY)
                    )
                    .setIconResId(R.drawable.ic_repeat_one_on)
                    .build(),
            )

        // 初始化播放器
        val player = EndedWorkaroundPlayer(
            ExoPlayer.Builder(
                this,
                GramophoneRenderFactory(this)
                    .setEnableAudioFloatOutput(
                        prefs.getBooleanStrict("floatoutput", false)
                    )
                    .setEnableDecoderFallback(true)
                    .setEnableAudioTrackPlaybackParams(
                        prefs.getBooleanStrict("ps_hardware_acc", true)
                    )
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
                GramophoneMediaSourceFactory(this)
            )
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setSkipSilenceEnabled(prefs.getBooleanStrict("skip_silence", false))
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true
                )
                .build()
        )

        // 打开音效控制会话
        sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.exoPlayer.audioSessionId)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        })
        lastPlayedManager = LastPlayedManager(this, player)
        lastPlayedManager.allowSavingState = false

        // 创建媒体会话
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, this)
                .setBitmapLoader(object : BitmapLoader {
                    // 使用Coil加载位图

                    override fun decodeBitmap(data: ByteArray) =
                        throw UnsupportedOperationException("decodeBitmap() not supported")

                    override fun loadBitmap(
                        uri: Uri
                    ): ListenableFuture<Bitmap> {
                        return CallbackToFutureAdapter.getFuture { completer ->
                            imageLoader.enqueue(
                                ImageRequest.Builder(this@GramophonePlaybackService)
                                    .data(uri)
                                    .allowHardware(false)
                                    .target(
                                        onStart = { _ ->
                                            // We don't need or want a placeholder.
                                        },
                                        onSuccess = { result ->
                                            completer.set((result as BitmapImage).bitmap)
                                        },
                                        onError = { _ ->
                                            completer.setException(
                                                Exception(
                                                    "coil onError called" +
                                                            " (normal if no album art exists)"
                                                )
                                            )
                                        }
                                    )
                                    .build())
                                .also {
                                    completer.addCancellationListener(
                                        { it.dispose() },
                                        ContextCompat.getMainExecutor(
                                            this@GramophonePlaybackService
                                        )
                                    )
                                }
                            "coil load for $uri"
                        }
                    }

                    override fun supportsMimeType(mimeType: String): Boolean {
                        return isBitmapFactorySupportedMimeType(mimeType)
                    }

                    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
                        return metadata.artworkUri?.let { loadBitmap(it) }
                    }
                })
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        PENDING_INTENT_SESSION_ID,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .build()
        controller = MediaController.Builder(this, mediaSession!!.token).buildAsync().get()
        /*
        将mediaSession添加到消息队列末尾，在主线程执行
        * */
        handler.post {
            if (mediaSession == null) return@post
            lastPlayedManager.restore { items, factory ->
                if (mediaSession == null) return@restore
                applyShuffleSeed(true, factory.toFactory(controller!!))
                if (items != null) {
                    try {
                        mediaSession?.player?.setMediaItems(
                            items.mediaItems, items.startIndex, items.startPositionMs
                        )
                    } catch (e: IllegalSeekPositionException) {
                        Log.e(TAG, "failed to restore: " + Log.getStackTraceString(e))
                    }
                    handler.post {
                        controller?.prepare()
                    }
                }
                lastPlayedManager.allowSavingState = true
            }
        }
        onShuffleModeEnabledChanged(controller!!.shuffleModeEnabled)
        controller!!.addListener(this)
        registerReceiver(
            headSetReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
    }


    override fun onDestroy() {
        lastPlayedManager.save()
        mediaSession!!.player.stop()
        sendBroadcast(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(
                AudioEffect.EXTRA_AUDIO_SESSION,
                (mediaSession!!.player as EndedWorkaroundPlayer).exoPlayer.audioSessionId
            )
        })
        controller!!.release()
        controller = null
        mediaSession!!.release()
        mediaSession!!.player.release()
        mediaSession = null
        lyrics = null
        unregisterReceiver(headSetReceiver)
        super.onDestroy()
    }

    // 获取当前会话
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    // 配置连接时可用的命令
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo)
            : MediaSession.ConnectionResult {
        val availableSessionCommands =
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
        if (session.isMediaNotificationController(controller)
            || session.isAutoCompanionController(controller)
            || session.isAutomotiveController(controller)) {
            for (commandButton in customCommands) {
                commandButton.sessionCommand?.let { availableSessionCommands.add(it) }
            }
        }
        availableSessionCommands.add(SessionCommand(SERVICE_SET_TIMER, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QUERY_TIMER, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY))
        handler.post {
            session.sendCustomCommand(
                controller,
                SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(availableSessionCommands.build())
            .build()
    }

    // 处理自定义命令
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        return Futures.immediateFuture(when (customCommand.customAction) {
            PLAYBACK_SHUFFLE_ACTION_ON -> {
                this.controller!!.shuffleModeEnabled = true
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            PLAYBACK_SHUFFLE_ACTION_OFF -> {
                this.controller!!.shuffleModeEnabled = false
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            SERVICE_SET_TIMER -> {
                customCommand.customExtras.getInt("duration").let {
                    timerDuration = if (it > 0) System.currentTimeMillis() + it else null
                }
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            SERVICE_QUERY_TIMER -> {
                SessionResult(SessionResult.RESULT_SUCCESS).also {
                    timerDuration?.let { td ->
                        it.extras.putInt("duration", (td - System.currentTimeMillis()).toInt())
                    }
                }
            }

            SERVICE_GET_LYRICS -> {
                SessionResult(SessionResult.RESULT_SUCCESS).also {
                    it.extras.putParcelableArray("lyrics", lyrics?.toTypedArray())
                }
            }

            PLAYBACK_REPEAT_OFF -> {
                this.controller!!.repeatMode = Player.REPEAT_MODE_OFF
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            PLAYBACK_REPEAT_ONE -> {
                this.controller!!.repeatMode = Player.REPEAT_MODE_ONE
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            PLAYBACK_REPEAT_ALL -> {
                this.controller!!.repeatMode = Player.REPEAT_MODE_ALL
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            else -> {
                SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
            }
        })
    }

    // 播放恢复时执行的操作
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val settable = SettableFuture.create<MediaItemsWithStartPosition>()
        lastPlayedManager.restore { items, factory ->
            applyShuffleSeed(true, factory.toFactory(this.controller!!))
            if (items == null) {
                settable.setException(NullPointerException(
                    "null MediaItemsWithStartPosition, see former logs for root cause"))
            } else if (items.mediaItems.isNotEmpty()) {
                settable.set(items)
            } else {
                settable.setException(IndexOutOfBoundsException(
                    "LastPlayedManager restored empty MediaItemsWithStartPosition"))
            }
        }
        return settable
    }

    override fun onTracksChanged(tracks: Tracks) {
        val mediaItem = controller!!.currentMediaItem
        lyricsLock.runInBg {
            val trim = prefs.getBoolean("trim_lyrics", false)
            val multiLine = prefs.getBoolean("lyric_multiline", false)
            var lrc = loadAndParseLyricsFile(mediaItem?.getFile(), trim, multiLine)
            if (lrc == null) {
                loop@ for (i in tracks.groups) {
                    for (j in 0 until i.length) {
                        if (!i.isTrackSelected(j)) continue
                        val trackMetadata = i.getTrackFormat(j).metadata ?: continue
                        lrc = extractAndParseLyrics(trackMetadata, trim, multiLine) ?: continue
                        lrc.add(0, MediaStoreUtils.Lyric())
                        break@loop
                    }
                }
            }
            //协程执行，挂起函数，不阻塞线程，生命周期为viewmodel
            CoroutineScope(Dispatchers.Main).launch {
                mediaSession?.let {
                    lyrics = lrc
                    it.broadcastCustomCommand(
                        SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY),
                        Bundle.EMPTY
                    )
                }
            }.join()
        }
    }

    // 当媒体项切换时执行的操作
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        lyrics = null
        lastPlayedManager.save()
    }

    // 当播放状态变化时执行的操作
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        lastPlayedManager.save()
    }

    // 处理播放器事件
    override fun onEvents(player: Player, events: Player.Events) {
        super.onEvents(player, events)
        if (events.contains(EVENT_SHUFFLE_MODE_ENABLED_CHANGED) &&
            shuffleFactory == null && !events.contains(Player.EVENT_TIMELINE_CHANGED)) {
            applyShuffleSeed(false) { c -> { CircularShuffleOrder(
                it, c, controller!!.mediaItemCount, Random.nextLong()) } }
        }
    }

    // 当启用随机播放模式时执行的操作
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled)
        mediaSession!!.setCustomLayout(ImmutableList.of(getRepeatCommand(), getShufflingCommand()))
        if (needsMissingOnDestroyCallWorkarounds()) {
            handler.post { lastPlayedManager.save() }
        }
    }

    // 当时间线变化时执行的操作
    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        super.onTimelineChanged(timeline, reason)
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            shuffleFactory?.let {
                applyShuffleSeed(false, it)
                shuffleFactory = null
            }
        }
    }

    // 当重复模式变化时执行的操作
    override fun onRepeatModeChanged(repeatMode: Int) {
        super.onRepeatModeChanged(repeatMode)
        mediaSession!!.setCustomLayout(ImmutableList.of(getRepeatCommand(), getShufflingCommand()))
        if (needsMissingOnDestroyCallWorkarounds()) {
            handler.post { lastPlayedManager.save() }
        }
    }

    // 处理前台服务启动不允许异常
    @SuppressLint("MissingPermission")
    override fun onForegroundServiceStartNotAllowedException() {
        Log.w(TAG, "Failed to resume playback :/")
        if (mayThrowForegroundServiceStartNotAllowed()) {
            nm.notify(NOTIFY_ID, NotificationCompat.Builder(this, NOTIFY_CHANNEL_ID).apply {
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                setAutoCancel(true)
                setCategory(NotificationCompat.CATEGORY_ERROR)
                setSmallIcon(R.drawable.ic_error)
                setContentTitle(this@GramophonePlaybackService.getString(R.string.fgs_failed_title))
                setContentText(this@GramophonePlaybackService.getString(R.string.fgs_failed_text))
                setContentIntent(
                    PendingIntent.getActivity(
                        this@GramophonePlaybackService,
                        PENDING_INTENT_NOTIFY_ID,
                        Intent(this@GramophonePlaybackService, MainActivity::class.java)
                            .putExtra(MainActivity.PLAYBACK_AUTO_START_FOR_FGS, true),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                setVibrate(longArrayOf(0L, 200L))
                setLights(0, 0, 0)
                setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                setSound(null)
            }.build())
        } else {
            handler.post {
                throw IllegalStateException("onForegroundServiceStartNotAllowedException shouldn't be called on T+")
            }
        }
    }

    // 应用随机播放种子
    private fun applyShuffleSeed(lazy: Boolean, factory:
        (Int) -> ((CircularShuffleOrder) -> Unit) -> CircularShuffleOrder) {
        if (lazy) {
            shuffleFactory = factory
        } else {
            (mediaSession?.player as EndedWorkaroundPlayer?)?.let {
                val data = try {
                    factory(it.currentMediaItemIndex)
                } catch (e: IllegalStateException) {
                    lastPlayedManager.eraseShuffleOrder()
                    throw e
                }
                it.setShuffleOrder(data)
            }
        }
    }
}