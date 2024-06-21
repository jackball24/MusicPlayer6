package org.akanework.gramophone.ui

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.akanework.gramophone.logic.GramophoneApplication
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.utils.LifecycleCallbackListImpl

class MediaControllerViewModel(application: Application) : AndroidViewModel(application),
	DefaultLifecycleObserver, MediaController.Listener {

	private val context: GramophoneApplication
		get() = getApplication()
	private var controllerLifecycle: LifecycleHost? = null
	private var controllerFuture: ListenableFuture<MediaController>? = null
	private val customCommandListenersImpl = LifecycleCallbackListImpl<
				(MediaController,SessionCommand, Bundle) -> ListenableFuture<SessionResult>>()
	private val connectionListenersImpl = LifecycleCallbackListImpl<LifecycleCallbackListImpl.Disposable.(MediaController, Lifecycle) -> Unit>()
	val customCommandListeners
		get() = customCommandListenersImpl.toBaseInterface()
	val connectionListeners
		get() = connectionListenersImpl.toBaseInterface()

	override fun onStart(owner: LifecycleOwner) {
		val sessionToken =
			SessionToken(context, ComponentName(context, GramophonePlaybackService::class.java))
		val lc = LifecycleHost()
		controllerLifecycle = lc
		controllerFuture =
			MediaController
				.Builder(context, sessionToken)
				.setListener(this)
				.buildAsync()
				.apply {
					addListener(
						{
							if (isCancelled) return@addListener
							val instance = get()
							if (this != controllerFuture) {
								// If there is a race condition that would cause this controller
								// to leak, which can happen, just make sure we don't leak.
								lc.destroy()
								instance.release()
							} else {
								lc.lifecycleRegistry.currentState = Lifecycle.State.CREATED
								connectionListenersImpl.dispatch { it(instance, lc.lifecycle) }
							}
						}, ContextCompat.getMainExecutor(context)
					)
				}
	}

	fun addControllerCallback(lifecycle: Lifecycle?,
	                          callback: LifecycleCallbackListImpl.Disposable.(MediaController, Lifecycle) -> Unit) {
		// TODO migrate this to kt flows or LiveData?
		val instance = get()
		var skip = false
		if (instance != null) {
			val ds = LifecycleCallbackListImpl.DisposableImpl()
			ds.callback(instance, controllerLifecycle!!.lifecycle)
			skip = ds.disposed
		}
		if (instance == null || !skip) {
			connectionListeners.addCallback(lifecycle, callback)
		}
	}

	fun addRecreationalPlayerListener(lifecycle: Lifecycle, callback: (Player) -> Player.Listener) {
		addControllerCallback(lifecycle) { controller, controllerLifecycle ->
			controller.registerLifecycleCallback(
				LifecycleIntersection(lifecycle, controllerLifecycle).lifecycle, callback(controller))
		}
	}

	fun get(): MediaController? {
		if (controllerFuture?.isDone == true && controllerFuture?.isCancelled == false) {
			return controllerFuture!!.get()
		}
		return null
	}

	override fun onDisconnected(controller: MediaController) {
		controllerLifecycle?.destroy()
		controllerLifecycle = null
		controllerFuture = null
	}

	override fun onStop(owner: LifecycleOwner) {
		if (controllerFuture?.isDone == true) {
			if (controllerFuture?.isCancelled == false) {
				controllerFuture?.get()?.release()
			} else {
				throw IllegalStateException("controllerFuture?.isCancelled != false")
			}
		} else {
			controllerFuture?.cancel(true)
			controllerLifecycle?.destroy()
			controllerLifecycle = null
			controllerFuture = null
		}
	}

	override fun onDestroy(owner: LifecycleOwner) {
		customCommandListenersImpl.release()
		connectionListenersImpl.release()
	}

	override fun onCustomCommand(
		controller: MediaController,
		command: SessionCommand,
		args: Bundle
	): ListenableFuture<SessionResult> {
		var future: ListenableFuture<SessionResult>? = null
		val listenerIterator = customCommandListenersImpl.iterator()
		while (future == null || (future.isDone &&
					future.get().resultCode == SessionResult.RESULT_ERROR_NOT_SUPPORTED)) {
			future = listenerIterator.next()(controller, command, args)
		}
		return future
	}

	private class LifecycleHost : LifecycleOwner {
		val lifecycleRegistry = LifecycleRegistry(this)
		override val lifecycle
			get() = lifecycleRegistry

		fun destroy() {
			// you cannot set DESTROYED before setting CREATED
			// this would leak observers if the LifecycleHost is exposed to clients before ON_CREATE
			// but it's not and that is apparently what google wanted to achieve with this check
			if (lifecycle.currentState != Lifecycle.State.INITIALIZED)
				lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
		}
	}

	class LifecycleIntersection(private val lifecycleOne: Lifecycle,
	                            private val lifecycleTwo: Lifecycle)
		: LifecycleOwner, LifecycleEventObserver {
		private val lifecycleRegistry = LifecycleRegistry(this)
		override val lifecycle
			get() = lifecycleRegistry

		init {
			lifecycleRegistry.addObserver(object : DefaultLifecycleObserver {
				override fun onDestroy(owner: LifecycleOwner) {
					lifecycleOne.removeObserver(this@LifecycleIntersection)
					lifecycleTwo.removeObserver(this@LifecycleIntersection)
				}
			})
			lifecycleOne.addObserver(this)
			lifecycleTwo.addObserver(this)
		}

		override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
			if (lifecycleOne.currentState == Lifecycle.State.DESTROYED ||
				lifecycleTwo.currentState == Lifecycle.State.DESTROYED
			) {
				lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
				return
			}
			val target = lifecycleOne.currentState.coerceAtMost(lifecycleTwo.currentState)
			if (target == lifecycleRegistry.currentState) return
			lifecycleRegistry.currentState = target
		}
	}
}

fun Player.registerLifecycleCallback(lifecycle: Lifecycle, callback: Player.Listener) {
	addListener(callback)
	lifecycle.addObserver(object : DefaultLifecycleObserver {
		override fun onDestroy(owner: LifecycleOwner) {
			removeListener(callback)
		}
	})
}