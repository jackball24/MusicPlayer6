package org.akanework.gramophone.ui

import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import androidx.fragment.app.commit
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import coil3.imageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgeProperly
import org.akanework.gramophone.logic.hasScopedStorageV2
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.needsMissingOnDestroyCallWorkarounds
import org.akanework.gramophone.logic.postAtFrontOfQueueAsync
import org.akanework.gramophone.logic.utils.MediaStoreUtils.updateLibraryWithInCoroutine
import org.akanework.gramophone.ui.components.PlayerBottomSheet
import org.akanework.gramophone.ui.fragments.BaseFragment

/**
 * MainActivity:
 *   Core of gramophone, one and the only activity
 * used across the application.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_READ_MEDIA_AUDIO = 100
        const val PLAYBACK_AUTO_START_FOR_FGS = "AutoStartFgs"
    }

    // Import our viewModels.
    private val libraryViewModel: LibraryViewModel by viewModels()
    val controllerViewModel: MediaControllerViewModel by viewModels()
    val startingActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private val handler = Handler(Looper.getMainLooper())
    private val reportFullyDrawnRunnable = Runnable { if (!ready) reportFullyDrawn() }
    private var ready = false
    private var autoPlay = false
    lateinit var playerBottomSheet: PlayerBottomSheet
        private set
    lateinit var intentSender: ActivityResultLauncher<IntentSenderRequest>
        private set
    var intentSenderAction: (() -> Boolean)? = null

    /**
     * updateLibrary:
     *   Calls [updateLibraryWithInCoroutine] in MediaStoreUtils and updates library.
     */
    fun updateLibrary(then: (() -> Unit)? = null) {
        // If library load takes more than 3s, exit splash to avoid ANR
        if (!ready) handler.postDelayed(reportFullyDrawnRunnable, 3000)
        CoroutineScope(Dispatchers.Default).launch {
            updateLibraryWithInCoroutine(libraryViewModel, this@MainActivity) {
                if (!ready) reportFullyDrawn()
                then?.let { it() }
            }
        }
    }

    /**
     * onCreate - core of MainActivity.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(controllerViewModel)
        enableEdgeToEdgeProperly()
        autoPlay = intent?.extras?.getBoolean(PLAYBACK_AUTO_START_FOR_FGS, false) == true
        intentSender = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == RESULT_OK) {
                if (intentSenderAction != null) {
                    intentSenderAction!!()
                } else {
                    Toast.makeText(this, getString(
                        R.string.delete_in_progress), Toast.LENGTH_LONG).show()
                }
            }
            intentSenderAction = null
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(object :
            FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                super.onFragmentStarted(fm, f)
                if (fm.fragments.lastOrNull() != f) return
                // this won't be called in case we show()/hide() so
                // we handle that case in BaseFragment
                if (f is BaseFragment && f.wantsPlayer != null) {
                    playerBottomSheet.visible = f.wantsPlayer
                }
            }
        }, false)

        // Set content Views.
        setContentView(R.layout.activity_main)
        playerBottomSheet = findViewById(R.id.player_layout)
        val container = findViewById<FragmentContainerView>(R.id.container)
        // Modifies FragmentContainerView's insets to account for bottom sheet size.
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            playerBottomSheet.generateBottomSheetInsets(insets)
        }

        // 检查并请求所有权限
        checkAndRequestPermissions()

    }


    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        // Android 13 及以上版本需要请求通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Android 11 及以上版本需要请求媒体权限
        if (hasScopedStorageWithMediaTypes() &&
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_MEDIA_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else if (!hasScopedStorageV2() &&
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else if (!hasScopedStorageWithMediaTypes() &&
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }


        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                PERMISSION_READ_MEDIA_AUDIO
            )
        } else {
            // 如果所有权限都已授予，则更新库
            if (libraryViewModel.mediaItemList.value == null) {
                updateLibrary()
            } else {
                reportFullyDrawn() // <-- 当由于旋转重新创建活动时
            }
        }
    }

    override fun reportFullyDrawn() {
        handler.removeCallbacks(reportFullyDrawnRunnable)
        if (ready) throw IllegalStateException("ready is already true")
        ready = true
        Choreographer.getInstance().postFrameCallback {
            handler.postAtFrontOfQueueAsync {
                super.reportFullyDrawn()
            }
        }
    }

    /**
     * onRequestPermissionResult:
     *   Update library after permission is granted.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_READ_MEDIA_AUDIO) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }

            if (allPermissionsGranted) {
                updateLibrary()
            } else {
                reportFullyDrawn()
                // TODO: Show a prompt here
            }
        }
    }

    /**
     * startFragment:
     *   Used by child fragments / drawer to start
     * a fragment inside MainActivity's fragment
     * scope.
     *
     * @param frag: Target fragment.
     */
    fun startFragment(frag: Fragment, args: (Bundle.() -> Unit)? = null) {
        supportFragmentManager.commit {
            addToBackStack(System.currentTimeMillis().toString())
            hide(supportFragmentManager.fragments.last())
            add(R.id.container, frag.apply { args?.let { arguments = Bundle().apply(it) } })
        }
    }

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        // https://github.com/androidx/media/issues/805
        if (needsMissingOnDestroyCallWorkarounds()
            && (getPlayer()?.playWhenReady != true || getPlayer()?.mediaItemCount == 0)) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        }
        super.onDestroy()
        // we don't ever want covers to be the cause of service being killed by too high mem usage
        // (this is placed after super.onDestroy() to make sure all ImageViews are dead)
        imageLoader.memoryCache?.clear()
    }

    /**
     * getPlayer:
     *   Returns a media controller.
     */
    fun getPlayer() = controllerViewModel.get()

    fun consumeAutoPlay(): Boolean {
        return autoPlay.also { autoPlay = false }
    }
}
