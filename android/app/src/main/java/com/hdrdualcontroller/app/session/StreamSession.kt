// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.session

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.*
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.hdrdualcontroller.app.common.LogManager
import com.hdrdualcontroller.app.common.Preferences
import com.hdrdualcontroller.app.dualsense.TriggerEffect
import com.hdrdualcontroller.app.dualsense.TriggerEffects
import com.hdrdualcontroller.app.dualsense.TriggerFeedback
import com.hdrdualcontroller.app.lib.*

sealed class StreamState
object StreamStateIdle: StreamState()
object StreamStateConnecting: StreamState()
object StreamStateConnected: StreamState()
data class StreamStateCreateError(val error: CreateError): StreamState()
data class StreamStateQuit(val reason: QuitReason, val reasonString: String?): StreamState()
data class StreamStateLoginPinRequest(val pinIncorrect: Boolean): StreamState()

class StreamSession(val context: Context, val connectInfo: ConnectInfo, val logManager: LogManager, val logVerbose: Boolean, val input: StreamInput)
{
	// DUALPLAY: efectos de gatillo hacia el DualSense (solo si se activó en Ajustes).
	private var triggerFeedback: TriggerFeedback? = null
	private var hapticLogged = false

	var session: Session? = null
		private set

	private val _state = MutableLiveData<StreamState>(StreamStateIdle)
	val state: LiveData<StreamState> get() = _state
	private val _rumbleState = MutableLiveData<RumbleEvent>(RumbleEvent(0U, 0U))
	val rumbleState: LiveData<RumbleEvent> get() = _rumbleState

	private var surfaceTexture: SurfaceTexture? = null
	private var surface: Surface? = null

	init
	{
		input.controllerStateChangedCallback = {
			session?.setControllerState(it)
		}
	}

	fun shutdown()
	{
		triggerFeedback?.clearAndClose()
		triggerFeedback = null
		session?.stop()
		session?.dispose()
		session = null
		_state.value = StreamStateIdle
		//surfaceTexture?.release()
	}

	fun pause()
	{
		shutdown()
	}

	fun resume()
	{
		if(session != null)
			return
		try
		{
			val session = Session(connectInfo, logManager.createNewFile().file.absolutePath, logVerbose)
			_state.value = StreamStateConnecting
			session.eventCallback = this::eventCallback
			if(connectInfo.enableDualSense && triggerFeedback == null)
				triggerFeedback = TriggerFeedback(context, connectInfo.enableHdHaptics, Preferences(context).hdHapticsGainPercent / 100f)
			session.start()
			val surface = surface
			if(surface != null)
				session.setSurface(surface)
			this.session = session
		}
		catch(e: CreateError)
		{
			_state.value = StreamStateCreateError(e)
		}
	}

	private fun eventCallback(event: Event)
	{
		when(event)
		{
			is ConnectedEvent -> _state.postValue(StreamStateConnected)
			is QuitEvent -> _state.postValue(
				StreamStateQuit(
					event.reason,
					event.reasonString
				)
			)
			is LoginPinRequestEvent -> _state.postValue(
				StreamStateLoginPinRequest(
					event.pinIncorrect
				)
			)
			is RumbleEvent -> _rumbleState.postValue(event)
			is HapticFrameEvent -> triggerFeedback?.onHapticFrame(event.pcm)
			is HapticRumbleEvent ->
			{
				if(!hapticLogged)
				{
					hapticLogged = true
					Log.i("DualPlay", "Háptica de la PS5 recibida (primer rumble emulado, fuerza ${event.strength}/65535)")
				}
				triggerFeedback?.onRumble(event.strength shr 8)
			}
			is TriggerEffectsEvent -> {
				Log.i("DualPlay", "Efectos de gatillo de la PS5: izq=0x%02x der=0x%02x (feedback ${if(triggerFeedback != null) "activo" else "desactivado"})".format(event.typeLeft, event.typeRight))
				triggerFeedback?.onTriggerEffects(
				TriggerEffects(TriggerEffect(event.typeLeft, event.left), TriggerEffect(event.typeRight, event.right)))
			}
		}
	}

	fun attachToSurfaceView(surfaceView: SurfaceView)
	{
		surfaceView.holder.addCallback(object: SurfaceHolder.Callback {
			override fun surfaceCreated(holder: SurfaceHolder) { }

			override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int)
			{
				val surface = holder.surface
				// DUALPLAY: el stream es de ritmo fijo (30/60 fps); sin esto la pantalla de 120 Hz puede hacer judder.
				// Solo cambia de modo si es sin parpadeo.
				surface.setFrameRate(
					connectInfo.videoProfile.maxFPS.toFloat(),
					Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
					Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
				this@StreamSession.surface = surface
				session?.setSurface(surface)
			}

			override fun surfaceDestroyed(holder: SurfaceHolder)
			{
				this@StreamSession.surface = null
				session?.setSurface(null)
			}
		})
	}

	fun attachToTextureView(textureView: TextureView)
	{
		textureView.surfaceTextureListener = object: TextureView.SurfaceTextureListener {
			override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int)
			{
				if(surfaceTexture != null)
					return
				surfaceTexture = surface
				this@StreamSession.surface = Surface(surfaceTexture)
				session?.setSurface(Surface(surface))
			}

			override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean
			{
				// return false if we want to keep the surface texture
				return surfaceTexture == null
			}

			override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { }
			override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
		}

		val surfaceTexture = surfaceTexture
		if(surfaceTexture != null)
			textureView.setSurfaceTexture(surfaceTexture)
	}

	fun setLoginPin(pin: String)
	{
		session?.setLoginPin(pin)
	}
}