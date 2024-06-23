package org.akanework.gramophone.ui.components

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.TransitionDrawable
import android.media.AudioManager
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.Size
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.TooltipCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.graphics.TypefaceCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import coil3.annotation.ExperimentalCoilApi
import coil3.dispose
import coil3.imageLoader
import coil3.load
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.error
import coil3.size.Scale
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.clone
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.fadInAnimation
import org.akanework.gramophone.logic.fadOutAnimation
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.getIntStrict
import org.akanework.gramophone.logic.getLyrics
import org.akanework.gramophone.logic.getTimer
import org.akanework.gramophone.logic.hasImagePermission
import org.akanework.gramophone.logic.hasScopedStorageV1
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.setTextAnimation
import org.akanework.gramophone.logic.setTimer
import org.akanework.gramophone.logic.startAnimation
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.ui.placeholderScaleToFit
import org.akanework.gramophone.logic.updateMargin
import org.akanework.gramophone.logic.utils.CalculationUtils
import org.akanework.gramophone.logic.utils.ColorUtils
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.MainActivity
import java.util.LinkedList
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
class FullBottomSheet(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
	ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), Player.Listener,
	SharedPreferences.OnSharedPreferenceChangeListener {
	// 构造方法
	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
			this(context, attrs, defStyleAttr, 0)
	constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
	constructor(context: Context) : this(context, null)

	// 获取活动实例
	private val activity
		get() = context as MainActivity
	// 获取媒体控制器实例
	private val instance: MediaController?
		get() = activity.getPlayer()
	// 定义最小化功能的回调
	var minimize: (() -> Unit)? = null

	// 声明各种变量
	private var wrappedContext: Context? = null
	private var currentJob: Job? = null
	private var isUserTracking = false
	private var runnableRunning = false
	private var firstTime = false
	// 添加AudioManager变量
	private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

	// 初始化音量相关的变量
	private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
	private val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

	private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

	companion object {
		const val SLIDER_UPDATE_INTERVAL: Long = 100
		const val BACKGROUND_COLOR_TRANSITION_SEC: Long = 300
		const val FOREGROUND_COLOR_TRANSITION_SEC: Long = 150
		const val LYRIC_FADE_TRANSITION_SEC: Long = 125
	}

	// 进度条触摸监听器
	private val touchListener = object : SeekBar.OnSeekBarChangeListener, Slider.OnSliderTouchListener {
		override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
			if (fromUser) {
				val dest = instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
				if (dest != null) {
					bottomSheetFullPosition.text =
						CalculationUtils.convertDurationToTimeStamp((progress.toLong()))
				}
			}
		}

		override fun onStartTrackingTouch(seekBar: SeekBar?) {
			isUserTracking = true
			progressDrawable.animate = false
		}

		override fun onStopTrackingTouch(seekBar: SeekBar?) {
			val mediaId = instance?.currentMediaItem?.mediaId
			if (mediaId != null) {
				if (seekBar != null) {
					instance?.seekTo((seekBar.progress.toLong()))
					updateLyric(seekBar.progress.toLong())
				}
			}
			isUserTracking = false
			progressDrawable.animate = instance?.isPlaying == true || instance?.playWhenReady == true
		}

		override fun onStartTrackingTouch(slider: Slider) {
			isUserTracking = true
		}

		override fun onStopTrackingTouch(slider: Slider) {
			val mediaId = instance?.currentMediaItem?.mediaId
			if (mediaId != null) {
				instance?.seekTo((slider.value.toLong()))
				updateLyric(slider.value.toLong())
			}
			isUserTracking = false
		}
	}

	// 音量控制的SeekBar监听器
	private val volumeSeekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
		override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
			if (fromUser) {
				audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
			}
		}

		override fun onStartTrackingTouch(seekBar: SeekBar?) {}

		override fun onStopTrackingTouch(seekBar: SeekBar?) {}
	}

	// BroadcastReceiver来监听音量变化
	private val volumeReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
				val newVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
				bottomSheetVolumeSeekBar.progress = newVolume
			}
		}
	}

	// 声明控件
	private val bottomSheetFullCover: ImageView
	val bottomSheetFullTitle: TextView
	val bottomSheetFullSubtitle: TextView
	private val bottomSheetFullControllerButton: MaterialButton
	private val bottomSheetFullNextButton: MaterialButton
	private val bottomSheetFullPreviousButton: MaterialButton
	private val bottomSheetFullDuration: TextView
	private val bottomSheetFullPosition: TextView
	private val bottomSheetFullSlideUpButton: MaterialButton
	private val bottomSheetShuffleButton: MaterialButton
	private val bottomSheetLoopButton: MaterialButton
	private val bottomSheetPlaylistButton: MaterialButton
	private val bottomSheetFavoriteButton: MaterialButton
	val bottomSheetLyricButton: MaterialButton
	private val bottomSheetFullSeekBar: SeekBar
	private val bottomSheetVolumeSeekBar: SeekBar
	private val bottomSheetFullSlider: Slider
	private val bottomSheetFullCoverFrame: MaterialCardView
	val bottomSheetFullLyricRecyclerView: RecyclerView
	private val bottomSheetFullLyricList: MutableList<MediaStoreUtils.Lyric> = mutableListOf()
	private val bottomSheetFullLyricAdapter

			: LyricAdapter = LyricAdapter(bottomSheetFullLyricList)
	private val bottomSheetFullLyricLinearLayoutManager = LinearLayoutManager(context)
	private val progressDrawable: SquigglyProgress
	private var fullPlayerFinalColor: Int = -1
	private var colorPrimaryFinalColor: Int = -1
	private var colorSecondaryContainerFinalColor: Int = -1
	private var colorOnSecondaryContainerFinalColor: Int = -1
	private var colorContrastFaintedFinalColor: Int = -1
	private var playlistNowPlaying: TextView? = null
	private var playlistNowPlayingCover: ImageView? = null
	private var lastDisposable: Disposable? = null

	// 初始化方法
	init {
		inflate(context, R.layout.full_player, this)
		bottomSheetFullCoverFrame = findViewById(R.id.album_cover_frame)
		bottomSheetFullCover = findViewById(R.id.full_sheet_cover)
		bottomSheetFullTitle = findViewById(R.id.full_song_name)
		bottomSheetFullSubtitle = findViewById(R.id.full_song_artist)
		bottomSheetFullPreviousButton = findViewById(R.id.sheet_previous_song)
		bottomSheetFullControllerButton = findViewById(R.id.sheet_mid_button)
		bottomSheetFullNextButton = findViewById(R.id.sheet_next_song)
		bottomSheetFullPosition = findViewById(R.id.position)
		bottomSheetFullDuration = findViewById(R.id.duration)
		bottomSheetFullSeekBar = findViewById(R.id.slider_squiggly)
		bottomSheetVolumeSeekBar = findViewById(R.id.volume)
		bottomSheetFullSlider = findViewById(R.id.slider_vert)
		bottomSheetFullSlideUpButton = findViewById(R.id.slide_down)
		bottomSheetShuffleButton = findViewById(R.id.sheet_random)
		bottomSheetLoopButton = findViewById(R.id.sheet_loop)
		bottomSheetFavoriteButton = findViewById(R.id.favor)
		bottomSheetPlaylistButton = findViewById(R.id.playlist)
		bottomSheetLyricButton = findViewById(R.id.lyrics)
		bottomSheetFullLyricRecyclerView = findViewById(R.id.lyric_frame)
		// 初始化音量SeekBar
		bottomSheetVolumeSeekBar.max = maxVolume
		bottomSheetVolumeSeekBar.progress = currentVolume
		bottomSheetVolumeSeekBar.setOnSeekBarChangeListener(volumeSeekBarChangeListener)

		// 注册音量变化的BroadcastReceiver
		val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
		context.registerReceiver(volumeReceiver, filter)

		// 获取颜色
		fullPlayerFinalColor = MaterialColors.getColor(
			this,
			com.google.android.material.R.attr.colorSurface
		)
		colorPrimaryFinalColor = MaterialColors.getColor(
			this,
			com.google.android.material.R.attr.colorPrimary
		)
		colorOnSecondaryContainerFinalColor = MaterialColors.getColor(
			this,
			com.google.android.material.R.attr.colorOnSecondaryContainer
		)
		colorSecondaryContainerFinalColor = MaterialColors.getColor(
			this,
			com.google.android.material.R.attr.colorSecondaryContainer
		)

		// 处理WindowInsets
		ViewCompat.setOnApplyWindowInsetsListener(bottomSheetFullLyricRecyclerView) { v, insets ->
			val myInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars()
					or WindowInsetsCompat.Type.displayCutout())
			v.updateMargin {
				left = -myInsets.left
				top = -myInsets.top
				right = -myInsets.right
				bottom = -myInsets.bottom
			}
			v.setPadding(myInsets.left, myInsets.top, myInsets.right, myInsets.bottom)
			return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(insets)
				.setInsets(WindowInsetsCompat.Type.systemBars()
						or WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
				.setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
						or WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
				.build()
		}
		refreshSettings(null)
		prefs.registerOnSharedPreferenceChangeListener(this)
		activity.controllerViewModel.customCommandListeners.addCallback(activity.lifecycle) { _, command, _ ->
			when (command.customAction) {

				// 获取歌词命令
				GramophonePlaybackService.SERVICE_GET_LYRICS -> {
					val parsedLyrics = instance?.getLyrics()
					if (bottomSheetFullLyricList != parsedLyrics) {
						bottomSheetFullLyricList.clear()
						if (parsedLyrics?.isEmpty() != false) {
							bottomSheetFullLyricList.add(
								MediaStoreUtils.Lyric(
									null,
									context.getString(R.string.no_lyric_found)
								)
							)
						} else {
							bottomSheetFullLyricList.addAll(parsedLyrics)
						}
						bottomSheetFullLyricAdapter.notifyDataSetChanged()
						resetToDefaultLyricPosition()
					}
				}

				else -> {
					return@addCallback Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
				}
			}
			return@addCallback Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
		}

		// 进度条相关参数
		val seekBarProgressWavelength =
			context.resources
				.getDimensionPixelSize(R.dimen.media_seekbar_progress_wavelength)
				.toFloat()
		val seekBarProgressAmplitude =
			context.resources
				.getDimensionPixelSize(R.dimen.media_seekbar_progress_amplitude)
				.toFloat()
		val seekBarProgressPhase =
			context.resources
				.getDimensionPixelSize(R.dimen.media_seekbar_progress_phase)
				.toFloat()
		val seekBarProgressStrokeWidth =
			context.resources
				.getDimensionPixelSize(R.dimen.media_seekbar_progress_stroke_width)
				.toFloat()

		bottomSheetFullSeekBar.progressDrawable = SquigglyProgress().also {
			progressDrawable = it
			it.waveLength = seekBarProgressWavelength
			it.lineAmplitude = seekBarProgressAmplitude
			it.phaseSpeed = seekBarProgressPhase
			it.strokeWidth = seekBarProgressStrokeWidth
			it.transitionEnabled = true
			it.animate = false
			it.setTint(
				MaterialColors.getColor(
					bottomSheetFullSeekBar,
					com.google.android.material.R.attr.colorPrimary,
				)
			)
		}

		// 循环按钮点击事件
		bottomSheetLoopButton.setOnClickListener {
			ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
			instance?.repeatMode = when (instance?.repeatMode) {
				Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
				Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
				Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
				else -> throw IllegalStateException()
			}
		}

		// 播放列表按钮点击事件
		bottomSheetPlaylistButton.setOnClickListener {
			ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
			val playlistBottomSheet = BottomSheetDialog(context)
			playlistBottomSheet.setContentView(R.layout.playlist_bottom_sheet)
			val recyclerView = playlistBottomSheet.findViewById<MyRecyclerView>(R.id.recyclerview)!!
			ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, ic ->
				val i = ic.getInsets(WindowInsetsCompat.Type.systemBars()
						or WindowInsetsCompat.Type.displayCutout())
				val i2 = ic.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
						or WindowInsetsCompat.Type.displayCutout())
				v.setPadding(i.left, 0, i.right, i.bottom)
				return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(ic)
					.setInsets(WindowInsetsCompat.Type.systemBars()
							or WindowInsetsCompat.Type.displayCutout(), Insets.of(0, i.top, 0, 0))
					.setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
							or WindowInsetsCompat.Type.displayCutout(), Insets.of(0, i2.top, 0, 0))
					.build()
			}
			val playlistAdapter = PlaylistCardAdapter(activity)
			val callback: ItemTouchHelper.Callback = PlaylistCardMoveCallback(playlistAdapter::onRowMoved)
			val touchHelper = ItemTouchHelper(callback)
			touchHelper.attachToRecyclerView(recyclerView)
			playlistNowPlaying = playlistBottomSheet.findViewById(R.id.now_playing)
			playlistNowPlaying!!.text = instance?.currentMediaItem?.mediaMetadata?.title
			playlistNowPlayingCover = playlistBottomSheet.findViewById(R.id.now_playing_cover)
			playlistNowPlayingCover!!.load(instance?.currentMediaItem?.mediaMetadata?.artworkUri) {
				placeholderScaleToFit(R.drawable.ic_default_cover)
				crossfade(true)
				error(R.drawable.ic_default_cover)
			}
			recyclerView.layoutManager = LinearLayoutManager(context)
			recyclerView.adapter = playlistAdapter
			recyclerView.scrollToPosition(playlistAdapter.playlist.first.indexOfFirst { i ->
				i == (instance?.currentMediaItemIndex ?: 0)
			})
			recyclerView.fastScroll(null, null)
			playlistBottomSheet.setOnDismissListener {
				if (playlistNowPlaying != null) {
					playlistNowPlayingCover!!.dispose()
					playlistNowPlayingCover = null
					playlistNowPlaying = null
				}
			}
			playlistBottomSheet.show()


		}

		// 播放按钮点击事件
		bottomSheetFullControllerButton.setOnClickListener {
			ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
			instance?.playOrPause()
		}

		// 上一曲按钮点击事件
		bottomSheetFullPreviousButton.setOnClickListener {
			ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
			instance?.seekToPreviousMediaItem()
		}

		// 下一曲按钮点击事件
		bottomSheetFullNextButton.setOnClickListener {
			ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
			instance?.seekToNextMediaItem()
		}

		// 随机播放按钮监听事件
		bottomSheetShuffleButton.addOnCheckedChangeListener { _, isChecked ->
			instance?.shuffleModeEnabled = isChecked
		}

		// 进度条变化监听事件
		bottomSheetFullSlider.addOnChangeListener { _, value, isUser ->
			if (isUser) {
				val dest = instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
				if (dest != null) {
					bottomSheetFullPosition.text =
						CalculationUtils.convertDurationToTimeStamp((value).toLong())
				}
			}
		}

		// 进度条触摸监听事件
		bottomSheetFullSeekBar.setOnSeekBarChangeListener(touchListener)
		bottomSheetFullSlider.addOnSliderTouchListener(touchListener)

		// 向上滑动按钮点击事件
		bottomSheetFullSlideUpButton.setOnClickListener {
			ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
			minimize?.invoke()
		}

		// 歌词按钮点击事件
		bottomSheetLyricButton.setOnClickListener {
			ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
			if(bottomSheetFullLyricRecyclerView.visibility == View.VISIBLE) {
				bottomSheetFullLyricRecyclerView.fadOutAnimation(LYRIC_FADE_TRANSITION_SEC)
				bottomSheetFullLyricRecyclerView.visibility = View.GONE
				bottomSheetFullTitle.setTextAnimation(instance?.currentMediaItem?.mediaMetadata?.title, skipAnimation = true)
				bottomSheetFullSubtitle.setTextAnimation(instance?.currentMediaItem?.mediaMetadata?.artist?: context.getString(R.string.unknown_artist), skipAnimation = true )
			} else {
				bottomSheetFullLyricRecyclerView.fadInAnimation(LYRIC_FADE_TRANSITION_SEC)
				bottomSheetFullLyricRecyclerView.visibility = View.VISIBLE
				bottomSheetFullTitle.setTextAnimation(null)
				bottomSheetFullSubtitle.setTextAnimation(null)
			}
		}

		// 随机播放按钮点击事件
		bottomSheetShuffleButton.setOnClickListener {
			ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
		}

		// 设置歌词列表布局管理器和适配器
		bottomSheetFullLyricRecyclerView.layoutManager =
			bottomSheetFullLyricLinearLayoutManager
		bottomSheetFullLyricRecyclerView.adapter =
			bottomSheetFullLyricAdapter

		// 移除颜色方案
		removeColorScheme()

		// 添加控制器回调
		activity.controllerViewModel.addControllerCallback(activity.lifecycle) { _, _ ->
			firstTime = true
			instance?.addListener(this@FullBottomSheet)
			onRepeatModeChanged(instance?.repeatMode ?: Player.REPEAT_MODE_OFF)
			onShuffleModeEnabledChanged(instance?.shuffleModeEnabled ?: false)
			onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
			onMediaItemTransition(
				instance?.currentMediaItem,
				Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
			)
			firstTime = false
		}
	}

	// 取消注册BroadcastReceiver
	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		context.unregisterReceiver(volumeReceiver)
	}

	// 监听SharedPreferences变化
	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		if (key == "color_accuracy" || key == "content_based_color") {
			if (DynamicColors.isDynamicColorAvailable() &&
				prefs.getBooleanStrict("content_based_color", true)) {
				addColorScheme()
			} else {
				removeColorScheme()
			}
		} else {
			refreshSettings(key)
			if (key == "lyric_center" || key == "lyric_bold" || key == "lyric_contrast") {
				@Suppress("NotifyDataSetChanged")
				bottomSheetFullLyricAdapter.notifyDataSetChanged()
			}
		}
	}

	// 刷新设置
	private fun refreshSettings(key: String?) {
		// 显示进度条
//		if (key == null || key == "default_progress_bar") {
//			if (prefs.getBooleanStrict("default_progress_bar", true)) {
//				bottomSheetFullSlider.visibility = View.GONE
//				bottomSheetFullSeekBar.visibility = View.VISIBLE
//			} else {
		bottomSheetFullSlider.visibility = View.VISIBLE
		bottomSheetFullSeekBar.visibility = View.GONE
//			}
//		}

		// 设置标题居中
		if (key == null || key == "centered_title") {
			if (prefs.getBooleanStrict("centered_title", true)) {
				bottomSheetFullTitle.gravity = Gravity.CENTER
				bottomSheetFullSubtitle.gravity = Gravity.CENTER
			} else {
				bottomSheetFullTitle.gravity = Gravity.CENTER_HORIZONTAL or Gravity.START
				bottomSheetFullSubtitle.gravity = Gravity.CENTER_HORIZONTAL or Gravity.START
			}
		}

		// 设置标题加粗
		if (key == null || key == "bold_title") {
			if (prefs.getBooleanStrict("bold_title", true)) {
				bottomSheetFullTitle.typeface = TypefaceCompat.create(context, null, 700, false)
			} else {
				bottomSheetFullTitle.typeface = TypefaceCompat.create(context, null, 500, false)
			}
		}

		// 设置专辑封面圆角
		if (key == null || key == "album_round_corner") {
			bottomSheetFullCoverFrame.radius = prefs.getIntStrict(
				"album_round_corner",
				context.resources.getInteger(R.integer.round_corner_radius)
			).dpToPx(context).toFloat()
		}

		// 设置歌词状态
		if (key == null || key == "lyric_center" || key == "lyric_bold" || key == "lyric_contrast") {
			bottomSheetFullLyricAdapter.updateLyricStatus()
		}
	}

	// 停止播放
	fun onStop() {
		runnableRunning = false
	}

	// 分发WindowInsets
	override fun dispatchApplyWindowInsets(platformInsets: WindowInsets): WindowInsets {
		val insets = WindowInsetsCompat.toWindowInsetsCompat(platformInsets)
		val myInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars()
				or WindowInsetsCompat.Type.displayCutout())
		setPadding(myInsets.left, myInsets.top, myInsets.right, myInsets.bottom)
		ViewCompat.dispatchApplyWindowInsets(bottomSheetFullLyricRecyclerView, insets.clone())
		return WindowInsetsCompat.Builder(insets)
			.setInsets(WindowInsetsCompat.Type.systemBars()
					or WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
			.setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
					or WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
			.build()
			.toWindowInsets()!!
	}

	// 移除颜色方案
	private fun removeColorScheme() {
		currentJob?.cancel()
		wrappedContext = null
		currentJob = CoroutineScope(Dispatchers.Default).launch {
			applyColorScheme()
		}
	}

	// 添加颜色方案
	private fun addColorScheme() {
		currentJob?.cancel()
		currentJob = CoroutineScope(Dispatchers.Default).launch {
			var drawable = bottomSheetFullCover.drawable
			if (drawable is TransitionDrawable) drawable = drawable.getDrawable(1)
			val bitmap = if (drawable is BitmapDrawable) drawable.bitmap else {
				removeColorScheme()
				return@launch
			}
			val colorAccuracy = prefs.getBoolean("color_accuracy", false)
			val targetWidth = if (colorAccuracy) (bitmap.width / 4).coerceAtMost(256) else 16
			val targetHeight = if (colorAccuracy) (bitmap.height / 4).coerceAtMost(256) else 16
			val scaledBitmap =
				Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false)

			val options = DynamicColorsOptions.Builder()
				.setContentBasedSource(scaledBitmap)
				.build() // <-- this is computationally expensive!

			wrappedContext = DynamicColors.wrapContextIfAvailable(
				context,
				options
			).apply {
				resources.configuration.uiMode = context.resources.configuration.uiMode
			}

			applyColorScheme()
		}
	}

	// 应用颜色方案
	private suspend fun applyColorScheme() {
		val ctx = wrappedContext ?: context

		val colorSurface = MaterialColors.getColor(
			ctx,
			com.google.android.material.R.attr.colorSurface,
			-1
		)

		val colorOnSurface = MaterialColors.getColor(
			ctx,
			com.google.android.material

				.R.attr.colorOnSurface,
			-1
		)

		val colorOnSurfaceVariant = MaterialColors.getColor(
			ctx,
			com.google.android.material.R.attr.colorOnSurfaceVariant,
			-1
		)

		val colorPrimary =
			MaterialColors.getColor(
				ctx,
				com.google.android.material.R.attr.colorPrimary,
				-1
			)

		val colorSecondaryContainer =
			MaterialColors.getColor(
				ctx,
				com.google.android.material.R.attr.colorSecondaryContainer,
				-1
			)

		val colorOnSecondaryContainer =
			MaterialColors.getColor(
				ctx,
				com.google.android.material.R.attr.colorOnSecondaryContainer,
				-1
			)

		val selectorBackground =
			AppCompatResources.getColorStateList(
				ctx,
				R.color.sl_check_button
			)

		val selectorFavBackground =
			AppCompatResources.getColorStateList(
				ctx,
				R.color.sl_fav_button
			)

		val colorAccent =
			MaterialColors.getColor(
				ctx,
				com.google.android.material.R.attr.colorAccent,
				-1
			)

		val backgroundProcessedColor = ColorUtils.getColor(
			colorSurface,
			ColorUtils.ColorType.COLOR_BACKGROUND_ELEVATED,
			ctx
		)

		val colorContrastFainted = ColorUtils.getColor(
			colorSecondaryContainer,
			ColorUtils.ColorType.COLOR_CONTRAST_FAINTED,
			ctx
		)

		val surfaceTransition = ValueAnimator.ofArgb(
			fullPlayerFinalColor,
			backgroundProcessedColor
		)

		val primaryTransition = ValueAnimator.ofArgb(
			colorPrimaryFinalColor,
			colorPrimary
		)

		val secondaryContainerTransition = ValueAnimator.ofArgb(
			colorSecondaryContainerFinalColor,
			colorSecondaryContainer
		)

		val onSecondaryContainerTransition = ValueAnimator.ofArgb(
			colorOnSecondaryContainerFinalColor,
			colorOnSecondaryContainer
		)

		val colorContrastFaintedTransition = ValueAnimator.ofArgb(
			colorContrastFaintedFinalColor,
			colorContrastFainted
		)

		surfaceTransition.apply {
			addUpdateListener { animation ->
				setBackgroundColor(
					animation.animatedValue as Int
				)
				bottomSheetFullLyricRecyclerView.setBackgroundColor(
					animation.animatedValue as Int
				)
			}
			duration = BACKGROUND_COLOR_TRANSITION_SEC
		}

		primaryTransition.apply {
			addUpdateListener { animation ->
				val progressColor = animation.animatedValue as Int
				bottomSheetFullSlider.thumbTintList =
					ColorStateList.valueOf(progressColor)
				bottomSheetFullSlider.trackActiveTintList =
					ColorStateList.valueOf(progressColor)
				bottomSheetFullSeekBar.progressTintList =
					ColorStateList.valueOf(progressColor)
				bottomSheetFullSeekBar.thumbTintList =
					ColorStateList.valueOf(progressColor)
			}
			duration = BACKGROUND_COLOR_TRANSITION_SEC
		}

		secondaryContainerTransition.apply {
			addUpdateListener { animation ->
				val progressColor = animation.animatedValue as Int
				bottomSheetFullControllerButton.backgroundTintList =
					ColorStateList.valueOf(progressColor)
			}
			duration = BACKGROUND_COLOR_TRANSITION_SEC
		}

		onSecondaryContainerTransition.apply {
			addUpdateListener { animation ->
				val progressColor = animation.animatedValue as Int
				bottomSheetFullControllerButton.iconTint =
					ColorStateList.valueOf(progressColor)
			}
			duration = BACKGROUND_COLOR_TRANSITION_SEC
		}

		colorContrastFaintedTransition.apply {
			addUpdateListener { animation ->
				val progressColor = animation.animatedValue as Int
				bottomSheetFullSlider.trackInactiveTintList =
					ColorStateList.valueOf(progressColor)
			}
		}

		withContext(Dispatchers.Main) {
			surfaceTransition.start()
			primaryTransition.start()
			secondaryContainerTransition.start()
			onSecondaryContainerTransition.start()
			colorContrastFaintedTransition.start()
		}

		delay(FOREGROUND_COLOR_TRANSITION_SEC)
		fullPlayerFinalColor = backgroundProcessedColor
		colorPrimaryFinalColor = colorPrimary
		colorSecondaryContainerFinalColor = colorSecondaryContainer
		colorOnSecondaryContainerFinalColor = colorOnSecondaryContainer
		colorContrastFaintedFinalColor = colorContrastFainted

		currentJob = null
		withContext(Dispatchers.Main) {
			bottomSheetFullTitle.setTextColor(
				colorOnSurface
			)
			bottomSheetFullSubtitle.setTextColor(
				colorOnSurfaceVariant
			)
			bottomSheetFullCoverFrame.setCardBackgroundColor(
				colorSurface
			)
			bottomSheetFullLyricAdapter.updateTextColor(
				androidx.core.graphics.ColorUtils.setAlphaComponent(colorOnSurfaceVariant, 170),
				androidx.core.graphics.ColorUtils.setAlphaComponent(colorPrimary, 77),
				colorPrimary
			)

			bottomSheetPlaylistButton.iconTint =
				ColorStateList.valueOf(colorOnSurface)
			bottomSheetShuffleButton.iconTint =
				selectorBackground
			bottomSheetLoopButton.iconTint =
				selectorBackground
			bottomSheetLyricButton.iconTint =
				ColorStateList.valueOf(colorOnSurface)
			bottomSheetFavoriteButton.iconTint =
				selectorFavBackground

			bottomSheetFullNextButton.iconTint =
				ColorStateList.valueOf(colorOnSurface)
			bottomSheetFullPreviousButton.iconTint =
				ColorStateList.valueOf(colorOnSurface)
			bottomSheetFullSlideUpButton.iconTint =
				ColorStateList.valueOf(colorOnSurface)

			bottomSheetFullPosition.setTextColor(
				colorAccent
			)
			bottomSheetFullDuration.setTextColor(
				colorAccent
			)
		}
	}

	@OptIn(ExperimentalCoilApi::class)
	@SuppressLint("NotifyDataSetChanged")
	override fun onMediaItemTransition(
		mediaItem: MediaItem?,
		reason: Int
	) {
		if (instance?.mediaItemCount != 0) {
			val req = { data: Any?, block: ImageRequest.Builder.() -> Unit ->
				lastDisposable?.dispose()
				lastDisposable = context.imageLoader.enqueue(ImageRequest.Builder(context).apply {
					data(data)
					scale(Scale.FILL)
					block()
					error(R.drawable.ic_default_cover)
					allowHardware(false)
				}.build())
			}
			val load = { data: Any? ->
				req(data) {
					target(onSuccess = {
						bottomSheetFullCover.setImageDrawable(it.asDrawable(context.resources))
					}, onError = {
						bottomSheetFullCover.setImageDrawable(it?.asDrawable(context.resources))
					}) // do not react to onStart() which sets placeholder
					listener(onSuccess = { _, _ ->
						if (DynamicColors.isDynamicColorAvailable() &&
							prefs.getBooleanStrict("content_based_color", true)
						) {
							addColorScheme()
						}
					}, onError = { _, _ ->
						if (DynamicColors.isDynamicColorAvailable() &&
							prefs.getBooleanStrict("content_based_color", true)
						) {
							removeColorScheme()
						}
					})
				}
			}
			val file = mediaItem?.getFile()
			if (hasScopedStorageV1() && (!hasScopedStorageWithMediaTypes()
						|| context.hasImagePermission()) && file != null) {
				req(Pair(file, Size(bottomSheetFullCover.width, bottomSheetFullCover.height))) {
					target(onSuccess = {
						bottomSheetFullCover.setImageDrawable(it.asDrawable(context.resources))
						if (DynamicColors.isDynamicColorAvailable() &&
							prefs.getBooleanStrict("content_based_color", true)
						) {
							addColorScheme()
						}
					}, onError = {
						load(mediaItem.mediaMetadata.artworkUri)
					})
				}
			} else {
				load(mediaItem?.mediaMetadata?.artworkUri)
			}

			bottomSheetFullTitle.setTextAnimation(mediaItem?.mediaMetadata?.title, skipAnimation = firstTime)
			bottomSheetFullSubtitle.setTextAnimation(
				mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist), skipAnimation = firstTime
			)
			if(bottomSheetFullLyricRecyclerView.visibility == View.VISIBLE) {
				bottomSheetFullTitle.setTextAnimation(null)
				bottomSheetFullSubtitle.setTextAnimation(null)
			}
			bottomSheetFullDuration.text =
				mediaItem?.mediaMetadata?.extras?.getLong("Duration")
					?.let { CalculationUtils.convertDurationToTimeStamp(it) }
			if (playlistNowPlaying != null) {
				playlistNowPlaying!!.text = mediaItem?.mediaMetadata?.title
				playlistNowPlayingCover!!.load(mediaItem?.mediaMetadata?.artworkUri) {
					placeholderScaleToFit(R.drawable.ic_default_cover)
					crossfade(true)
					error(R.drawable.ic_default_cover)
				}
			}

		} else {
			lastDisposable?.dispose()
			lastDisposable = null
			playlistNowPlayingCover?.dispose()
		}
		val position = CalculationUtils.convertDurationToTimeStamp(instance?.currentPosition ?: 0)
		val duration = instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
		if (duration != null && !isUserTracking) {
			bottomSheetFullSeekBar.max = duration.toInt()
			bottomSheetFullSeekBar.progress = instance?.currentPosition?.toInt() ?: 0
			bottomSheetFullSlider.valueTo = duration.toFloat().coerceAtLeast(1f)
			bottomSheetFullSlider.value =
				min(instance?.currentPosition?.toFloat() ?: 0f, bottomSheetFullSlider.valueTo)
			bottomSheetFullPosition.text = position
		}
		updateLyric(duration)
	}

	// 监听随机播放模式改变
	override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
		bottomSheetShuffleButton.isChecked = shuffleModeEnabled
	}

	// 监听循环模式改变
	override fun onRepeatModeChanged(repeatMode: Int) {
		when (repeatMode) {
			Player.REPEAT_MODE_ALL -> {
				bottomSheetLoopButton.isChecked = true
				bottomSheetLoopButton.icon =
					AppCompatResources.getDrawable(context, R.drawable.ic_repeat)
			}

			Player.REPEAT_MODE_ONE -> {
				bottomSheetLoopButton.isChecked = true
				bottomSheetLoopButton.icon =
					AppCompatResources.getDrawable(context, R.drawable.ic_repeat_one)
			}

			Player.REPEAT_MODE_OFF -> {
				bottomSheetLoopButton.isChecked = false
				bottomSheetLoopButton.icon =
					AppCompatResources.getDrawable(context, R.drawable.ic_repeat)
			}
		}
	}

	// 监听播放状态改变
	override fun onIsPlayingChanged(isPlaying: Boolean) {
		onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
	}

	// 处理播放状态改变
	override fun onPlaybackStateChanged(playbackState: Int) {
		if (instance?.isPlaying == true) {
			if (bottomSheetFullControllerButton.getTag(R.id.play_next) as Int? != 1) {
				bottomSheetFullControllerButton.icon =
					AppCompatResources.getDrawable(
						wrappedContext ?: context,
						R.drawable.play_anim
					)
				bottomSheetFullControllerButton.background =
					AppCompatResources.getDrawable(context, R.drawable.bg_play_anim)
				bottomSheetFullControllerButton.icon.startAnimation()
				bottomSheetFullControllerButton.background.startAnimation()
				bottomSheetFullControllerButton.setTag(R.id.play_next, 1)
			}
			if (!isUserTracking) {
				progressDrawable.animate = true
			}
			if (!runnableRunning) {
				handler.postDelayed(positionRunnable, SLIDER_UPDATE_INTERVAL)
				runnableRunning = true
			}
		} else if (playbackState != Player.STATE_BUFFERING) {
			if (bottomSheetFullControllerButton.getTag(R.id.play_next) as Int? != 2) {
				bottomSheetFullControllerButton.icon =
					AppCompatResources.getDrawable(
						wrappedContext ?: context,
						R.drawable.pause_anim
					)
				bottomSheetFullControllerButton.background =
					AppCompatResources.getDrawable(context, R.drawable.bg_pause_anim)
				bottomSheetFullControllerButton.icon.startAnimation()
				bottomSheetFullControllerButton.background.startAnimation()
				bottomSheetFullControllerButton.setTag(R.id.play_next, 2)
			}
			if (!isUserTracking) {
				progressDrawable.animate = false
			}
		}
	}

	// 处理按键事件
	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		return when (keyCode) {
			KeyEvent.KEYCODE_SPACE -> {
				instance?.playOrPause(); true
			}

			KeyEvent.KEYCODE_DPAD_LEFT -> {
				instance?.seekToPreviousMediaItem(); true
			}

			KeyEvent.KEYCODE_DPAD_RIGHT -> {
				instance?.seekToNextMediaItem(); true
			}

			else -> super.onKeyDown(keyCode, event)
		}
	}

	// 转储播放列表
	private fun dumpPlaylist(): Pair<MutableList<Int>, MutableList<MediaItem>> {
		val items = LinkedList<MediaItem>()
		for (i in 0 until instance!!.mediaItemCount) {
			items.add(instance!!.getMediaItemAt(i))
		}
		val indexes = LinkedList<Int>()
		val s = instance!!.shuffleModeEnabled
		var i = instance!!.currentTimeline.getFirstWindowIndex(s)
		while (i != C.INDEX_UNSET) {
			indexes.add(i)
			i = instance!!.currentTimeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, s)
		}
		return Pair(indexes, items)
	}

	// 歌词适配器
	private inner class LyricAdapter(
		private val lyricList: MutableList<MediaStoreUtils.Lyric>
	) : MyRecyclerView.Adapter<LyricAdapter.ViewHolder>() {

		private var defaultTextColor = 0
		private var contrastTextColor = 0
		private var highlightTextColor = 0
		var currentFocusPos = -1
		private var currentTranslationPos = -1
		private var isBoldEnabled = false
		private var isLyricCentered = false
		private var isLyricContrastEnhanced = false

		override fun onCreateViewHolder(
			parent: ViewGroup,
			viewType: Int
		): LyricAdapter.ViewHolder =
			ViewHolder(
				LayoutInflater
					.from(parent.context)
					.inflate(R.layout.lyrics, parent, false),
			)

		override fun onBindViewHolder(holder: LyricAdapter.ViewHolder, position: Int) {
			val lyric = lyricList[position]

			with(holder.lyricCard) {
				setOnClickListener {
					lyric.timeStamp?.let { it1 ->
						ViewCompat.performHapticFeedback(
							it,
							HapticFeedbackConstantsCompat.CONTEXT_CLICK
						)
						val instance = activity.getPlayer()
						if (instance?.isPlaying == false) {
							instance.play()
						}
						instance?.seekTo(it1)
					}
				}
			}

			with(holder.lyricTextView) {
				visibility = if (lyric.content.isNotEmpty()) View.VISIBLE else View.GONE
				text = lyric.content

				if (isBoldEnabled) {
					this.typeface = TypefaceCompat.create(context,null, 700, false)
				} else {
					this.typeface = TypefaceCompat.create(context, null, 500, false)
				}

				if (isLyricCentered) {
					this.gravity = Gravity.CENTER
				} else {
					this.gravity = Gravity.START
				}

				val textSize = if (lyric.isTranslation) 20f else 24f
				val paddingTop = (if (lyric.isTranslation) 2 else 10).dpToPx(context)
				val paddingBottom = (if (position + 1 < lyricList.size &&
					lyricList[position + 1].isTranslation
				) 2 else 10).dpToPx(context)

				this.textSize = textSize
				setPadding(10.dpToPx(context), paddingTop, 10.dpToPx(context), paddingBottom)

				val isFocus = position == currentFocusPos || position == currentTranslationPos
				setTextColor(if (isFocus) highlightTextColor else (if (isLyricContrastEnhanced)
					contrastTextColor else defaultTextColor))
			}
		}

		override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
			super.onAttachedToRecyclerView(recyclerView)
			updateLyricStatus()
		}

		fun updateLyricStatus() {
			isBoldEnabled = prefs.getBooleanStrict("lyric_bold", false)
			isLyricCentered = prefs.getBooleanStrict("lyric_center", false)
			isLyricContrastEnhanced = prefs.getBooleanStrict("lyric_contrast", false)
		}

		override fun getItemCount(): Int = lyricList.size

		inner class ViewHolder(
			view: View
		) : RecyclerView.ViewHolder(view) {
			val lyricTextView: TextView = view.findViewById(R.id.lyric)
			val lyricCard: MaterialCardView = view.findViewById(R.id.cardview)
		}

		@SuppressLint("NotifyDataSetChanged")
		fun updateTextColor(newColorContrast: Int, newColor: Int, newHighlightColor: Int) {
			defaultTextColor = newColor
			contrastTextColor = newColorContrast
			highlightTextColor = newHighlightColor
			notifyDataSetChanged()
		}

		fun updateHighlight(position: Int) {
			if (currentFocusPos == position) return
			if (position >= 0) {
				currentFocusPos.let {
					notifyItemChanged(it)
					currentFocusPos = position
					notifyItemChanged(currentFocusPos)
				}

				if (position + 1 < lyricList.size &&
					lyricList[position + 1].isTranslation
				) {
					currentTranslationPos.let {
						notifyItemChanged(it)
						currentTranslationPos = position + 1
						notifyItemChanged(currentTranslationPos)
					}
				} else if (currentTranslationPos != -1) {
					notifyItemChanged(currentTranslationPos)
					currentTranslationPos = -1
				}
			} else {
				currentFocusPos = -1
				currentTranslationPos = -1
			}
		}
	}

	// 播放列表适配器
	private inner class PlaylistCardAdapter(
		private val activity: MainActivity
	) : MyRecyclerView.Adapter<PlaylistCardAdapter.ViewHolder>() {

		var playlist: Pair<MutableList<Int>, MutableList<MediaItem>> = dumpPlaylist()
		override fun onCreateViewHolder(
			parent: ViewGroup,
			viewType: Int
		): PlaylistCardAdapter.ViewHolder = ViewHolder(
		LayoutInflater.from(parent.context).inflate(R.layout.adapter_list_card_playlist, parent, false),
		)

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val item = playlist.second[playlist.first[holder.bindingAdapterPosition]]
			holder.songName.text = item.mediaMetadata.title
			holder.songArtist.text = item.mediaMetadata.artist
			holder.indicator.text =
				CalculationUtils.convertDurationToTimeStamp(
					item.mediaMetadata.extras?.getLong("Duration")!!
				)
			holder.songCover.load(item.mediaMetadata.artworkUri) {
				placeholderScaleToFit(R.drawable.ic_default_cover)
				crossfade(true)
				error(R.drawable.ic_default_cover)
			}
			holder.closeButton.setOnClickListener { v ->
				ViewCompat.performHapticFeedback(v, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
				val instance = activity.getPlayer()
				val pos = holder.bindingAdapterPosition
				val idx = playlist.first.removeAt(pos)
				playlist.first.replaceAll { if (it > idx) it - 1 else it }
				instance?.removeMediaItem(idx)
				playlist.second.removeAt(idx)
				notifyItemRemoved(pos)
			}
			holder.itemView.setOnClickListener {
				ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
				val instance = activity.getPlayer()
				instance?.seekToDefaultPosition(playlist.first[holder.absoluteAdapterPosition])
			}
		}

		override fun onViewRecycled(holder: ViewHolder) {
			holder.songCover.dispose()
			super.onViewRecycled(holder)
		}

		override fun getItemCount(): Int = if (playlist.first.size != playlist.second.size)
			throw IllegalStateException()
		else playlist.first.size

		inner class ViewHolder(
			view: View,
		) : RecyclerView.ViewHolder(view) {
			val songName: TextView = view.findViewById(R.id.title)
			val songArtist: TextView = view.findViewById(R.id.artist)
			val songCover: ImageView = view.findViewById(R.id.cover)
			val indicator: TextView = view.findViewById(R.id.indicator)
			val closeButton: MaterialButton = view.findViewById(R.id.close)
		}

		// 移动播放列表项目
		fun onRowMoved(from: Int, to: Int) {
			val mediaController = activity.getPlayer()
			val from1 = playlist.first.removeAt(from)
			playlist.first.replaceAll { if (it > from1) it - 1 else it }
			val movedItem = playlist.second.removeAt(from1)
			val to1 = if (to > 0) playlist.first[to - 1] + 1 else 0
			playlist.first.replaceAll { if (it >= to1) it + 1 else it }
			playlist.first.add(to, to1)
			playlist.second.add(to1, movedItem)
			mediaController?.moveMediaItem(from1, to1)
			notifyItemMoved(from, to)
		}
	}

	// 播放列表移动回调
	private class PlaylistCardMoveCallback(private val touchHelperContract: (Int, Int) -> Unit) :
		ItemTouchHelper.Callback() {
		override fun isLongPressDragEnabled(): Boolean {
			return true
		}

		override fun isItemViewSwipeEnabled(): Boolean {
			return false
		}

		override fun getMovementFlags(
			recyclerView: RecyclerView,
			viewHolder: RecyclerView.ViewHolder
		): Int {
			val dragFlag = ItemTouchHelper.UP or ItemTouchHelper.DOWN
			return makeMovementFlags(dragFlag, 0)
		}

		override fun onMove(
			recyclerView: RecyclerView,
			viewHolder: RecyclerView.ViewHolder,
			target: RecyclerView.ViewHolder
		): Boolean {
			touchHelperContract(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
			return false
		}

		override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
			throw IllegalStateException()
		}
	}

	/*
	@Suppress("DEPRECATION")
	private fun insertIntoPlaylist(song: MediaItem) {
		val playlistEntry = ContentValues()
		val playlistId = activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition].id
		playlistEntry.put(MediaStore.Audio.Playlists.Members.PLAYLIST_ID, playlistId)
		playlistEntry.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, song.mediaId)

		context.contentResolver.insert(
			MediaStore.Audio.Playlists.Members.getContentUri(
				"external",
				playlistId
			), playlistEntry
		)
		activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition].songList.add(song)
	}

	@Suppress("DEPRECATION")
	private fun removeFromPlaylist(song: MediaItem) {
		val selection = "${MediaStore.Audio.Playlists.Members.AUDIO_ID} = ?"
		val selectionArgs = arrayOf(song.mediaId)
		val playlistId = activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition].id

		context.contentResolver.delete(
			MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
			selection,
			selectionArgs
		)
		activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition].songList.remove(song)
	}

	 */

	// 更新歌词
	fun updateLyric(duration: Long?) {
		if (bottomSheetFullLyricList.isNotEmpty()) {
			val newIndex: Int

			val filteredList = bottomSheetFullLyricList.filterIndexed { _, lyric ->
				(lyric.timeStamp ?: 0) <= (instance?.currentPosition ?: 0)
			}

			newIndex = if (filteredList.isNotEmpty()) {
				filteredList.indices.maxBy {
					(filteredList[it].timeStamp ?: 0)
				}
			} else {
				-1
			}

			if (newIndex != -1 &&
				duration != null &&
				newIndex != bottomSheetFullLyricAdapter.currentFocusPos
			) {
				val smoothScroller = createSmoothScroller()
				smoothScroller.targetPosition = newIndex
				bottomSheetFullLyricLinearLayoutManager.startSmoothScroll(
					smoothScroller
				)
				bottomSheetFullLyricAdapter.updateHighlight(newIndex)
			}
		}
	}

	// 创建平滑滚动器
	private fun createSmoothScroller() =
		object : LinearSmoothScroller(context) {
			override fun calculateDtToFit(
				viewStart: Int,
				viewEnd: Int,
				boxStart: Int,
				boxEnd: Int,
				snapPreference: Int
			): Int {
				return super.calculateDtToFit(
					viewStart,
					viewEnd,
					boxStart,
					boxEnd,
					snapPreference
				) + context.resources.displayMetrics.heightPixels / 3
			}

			override fun getVerticalSnapPreference(): Int {
				return SNAP_TO_START
			}

			override fun calculateTimeForDeceleration(dx: Int): Int {
				return 500
			}
		}

	// 进度位置Runnable
	private val positionRunnable = object : Runnable {
		override fun run() {
			if (!runnableRunning) return
			val position =
				CalculationUtils.convertDurationToTimeStamp(instance?.currentPosition ?: 0)
			val duration = instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
			if (duration != null && !isUserTracking) {
				bottomSheetFullSeekBar.max = duration.toInt()
				bottomSheetFullSeekBar.progress = instance?.currentPosition?.toInt() ?: 0
				bottomSheetFullSlider.valueTo = duration.toFloat()
				bottomSheetFullSlider.value =
					min(instance?.currentPosition?.toFloat() ?: 0f, bottomSheetFullSlider.valueTo)
				bottomSheetFullPosition.text = position
			}
			updateLyric(duration)
			if (instance?.isPlaying == true) {
				handler.postDelayed(this, SLIDER_UPDATE_INTERVAL)
			} else {
				runnableRunning = false
			}
		}
	}

	// 重置歌词位置
	private fun resetToDefaultLyricPosition() {
		val smoothScroller = createSmoothScroller()
		smoothScroller.targetPosition = 0
		bottomSheetFullLyricLinearLayoutManager.startSmoothScroll(
			smoothScroller
		)
		bottomSheetFullLyricAdapter.updateHighlight(0)
	}
}
