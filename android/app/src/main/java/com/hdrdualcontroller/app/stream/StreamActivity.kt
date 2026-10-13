// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.stream

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.*
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.common.Preferences
import com.hdrdualcontroller.app.common.ext.viewModelFactory
import com.hdrdualcontroller.app.databinding.ActivityStreamBinding
import com.hdrdualcontroller.app.lib.Codec
import com.hdrdualcontroller.app.lib.ConnectInfo
import com.hdrdualcontroller.app.session.*
import com.hdrdualcontroller.app.touchcontrols.TouchControlsFragment
import com.hdrdualcontroller.app.touchcontrols.TouchpadOnlyFragment
import com.hdrdualcontroller.app.ui.stream.StreamOptions
import com.hdrdualcontroller.app.ui.stream.StreamOverlay
import com.hdrdualcontroller.app.ui.theme.DualPlayTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.min

class StreamActivity : AppCompatActivity()
{
	companion object
	{
		const val EXTRA_CONNECT_INFO = "connect_info"
		const val EXTRA_CONSOLE_NAME = "console_name"
		private const val HIDE_SYSTEM_UI_TIMEOUT_MS = 2000L
	}

	private lateinit var viewModel: StreamViewModel
	private lateinit var binding: ActivityStreamBinding
	private lateinit var insetsController: WindowInsetsControllerCompat

	private val uiVisibilityHandler = Handler(Looper.getMainLooper())

	// Estado del overlay en Compose.
	private var panelOpen by mutableStateOf(false)
	private var micEnabled by mutableStateOf(false)
	private var displayMode by mutableStateOf(TransformMode.FIT)
	private var controlsOpacity by mutableIntStateOf(100)

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		val connectInfo = IntentCompat.getParcelableExtra(intent, EXTRA_CONNECT_INFO, ConnectInfo::class.java)
		if(connectInfo == null)
		{
			finish()
			return
		}
		val consoleName = intent.getStringExtra(EXTRA_CONSOLE_NAME) ?: connectInfo.host

		viewModel = ViewModelProvider(this, viewModelFactory {
			StreamViewModel(application, connectInfo)
		})[StreamViewModel::class.java]

		viewModel.input.observe(this)

		binding = ActivityStreamBinding.inflate(layoutInflater)
		setContentView(binding.root)

		controlsOpacity = viewModel.preferences.touchControlsOpacity

		// DUALPLAY: sin COLOR_MODE_HDR la ventana se compone en SDR y el HDR10 sale lavado. Solo si el stream es HDR
		// (el perfil ya pasó por HdrSupport.adjust, así que la pantalla lo soporta).
		if(connectInfo.videoProfile.codec == Codec.CODEC_H265_HDR)
			window.colorMode = ActivityInfo.COLOR_MODE_HDR

		WindowCompat.setDecorFitsSystemWindows(window, false)
		insetsController = WindowCompat.getInsetsController(window, window.decorView)
		insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

		ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
			// DUALPLAY: isVisible(systemBars()) exige que TODAS las barras estén visibles; con navegación por gestos la
			// barra inferior casi nunca lo está y el panel no salía al deslizar. Basta con que se vea alguna.
			val systemBars = insets.isVisible(WindowInsetsCompat.Type.statusBars()) || insets.isVisible(WindowInsetsCompat.Type.navigationBars())
			if(systemBars)
			{
				panelOpen = true
				uiVisibilityHandler.removeCallbacks(hideSystemUIRunnable)
				uiVisibilityHandler.postDelayed(hideSystemUIRunnable, HIDE_SYSTEM_UI_TIMEOUT_MS)
			}
			insets
		}

		viewModel.session.attachToSurfaceView(binding.surfaceView)

		// DUALPLAY: el touchpad del DualSense llega como puntero; con la captura no sale el cursor y entrega los dedos.
		binding.surfaceView.isFocusable = true
		binding.surfaceView.isFocusableInTouchMode = true
		binding.surfaceView.setOnCapturedPointerListener { _, event -> viewModel.input.onCapturedPointerEvent(event) }

		viewModel.session.state.observe(this, Observer {
			// Una salida normal (no error) cierra la pantalla; los errores los muestra el overlay.
			if(it is StreamStateQuit && !it.reason.isError)
				finish()
		})
		applyDisplayMode(TransformMode.FIT)

		binding.composeOverlay.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		binding.composeOverlay.setContent {
			DualPlayTheme {
				val state by viewModel.session.state.observeAsState(StreamStateIdle)
				val onScreenControls by viewModel.onScreenControlsEnabled.observeAsState(true)
				val touchpadOnly by viewModel.touchpadOnlyEnabled.observeAsState(false)
				StreamOverlay(
					consoleName = consoleName,
					state = state,
					panelOpen = panelOpen,
					onPanelOpenChange = { panelOpen = it },
					options = StreamOptions(
						onScreenControls = onScreenControls,
						touchpadOnly = touchpadOnly,
						microphone = micEnabled,
						displayMode = displayMode,
						controlsOpacityPercent = controlsOpacity,
						onOnScreenControls = {
							viewModel.setOnScreenControlsEnabled(it)
							if(it)
								viewModel.setTouchpadOnlyEnabled(false)
						},
						onTouchpadOnly = {
							viewModel.setTouchpadOnlyEnabled(it)
							if(it)
								viewModel.setOnScreenControlsEnabled(false)
						},
						onMicrophone = ::setMicrophone,
						onDisplayMode = ::applyDisplayMode,
						onControlsOpacity = {
							controlsOpacity = it
							viewModel.preferences.touchControlsOpacity = it
							applyControlsOpacity()
						}),
					onReconnect = ::reconnect,
					onQuit = { finish() },
					onSubmitPin = { viewModel.session.setLoginPin(it) })
			}
		}

		if(Preferences(this).rumbleEnabled)
		{
			val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
			viewModel.session.rumbleState.observe(this, Observer {
				val amplitude = min(255, (it.left.toInt() + it.right.toInt()) / 2)
				vibrator.cancel()
				if(amplitude == 0)
					return@Observer
				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
					vibrator.vibrate(VibrationEffect.createOneShot(1000, amplitude))
				else
					vibrator.vibrate(1000)
			})
		}
	}

	private var controlsJob: Job? = null

	// DUALPLAY: micrófono del teléfono para el chat de voz de la PS5
	private val micPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		if(granted)
			applyMicrophone(true)
		else
		{
			micEnabled = false
			Toast.makeText(this, R.string.stream_microphone_denied, Toast.LENGTH_LONG).show()
		}
	}

	private fun setMicrophone(enabled: Boolean)
	{
		if(enabled)
		{
			if(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
				applyMicrophone(true)
			else
				micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
		}
		else
		{
			micEnabled = false
			viewModel.session.setMicrophoneEnabled(false)
		}
	}

	private fun applyMicrophone(enabled: Boolean)
	{
		if(!viewModel.session.setMicrophoneEnabled(enabled) && enabled)
		{
			micEnabled = false
			Toast.makeText(this, R.string.stream_microphone_failed, Toast.LENGTH_LONG).show()
		}
		else
			micEnabled = enabled
	}

	override fun onAttachFragment(fragment: Fragment)
	{
		super.onAttachFragment(fragment)
		if(fragment is TouchControlsFragment)
		{
			controlsJob?.cancel()
			controlsJob = fragment.controllerState
				.onEach { viewModel.input.touchControllerState = it }
				.launchIn(lifecycleScope)
			fragment.onScreenControlsEnabled = viewModel.onScreenControlsEnabled
			if(fragment is TouchpadOnlyFragment)
				fragment.touchpadOnlyEnabled = viewModel.touchpadOnlyEnabled
		}
	}

	override fun onResume()
	{
		super.onResume()
		micEnabled = false // la sesión arranca con el micrófono silenciado
		applyControlsOpacity()
		hideSystemUI()
		viewModel.session.resume()
	}

	override fun onPause()
	{
		super.onPause()
		viewModel.session.pause()
	}

	override fun onDestroy()
	{
		super.onDestroy()
		controlsJob?.cancel()
	}

	private fun reconnect()
	{
		viewModel.session.shutdown()
		viewModel.session.resume()
	}

	private val hideSystemUIRunnable = Runnable { hideSystemUI() }

	override fun onWindowFocusChanged(hasFocus: Boolean)
	{
		super.onWindowFocusChanged(hasFocus)
		if(hasFocus)
		{
			hideSystemUI()
			binding.surfaceView.requestFocus()
			binding.surfaceView.requestPointerCapture()
		}
		else
			binding.surfaceView.releasePointerCapture()
	}

	private fun hideSystemUI()
	{
		insetsController.hide(WindowInsetsCompat.Type.systemBars())
	}

	private fun applyDisplayMode(mode: TransformMode)
	{
		displayMode = mode
		val videoProfile = viewModel.session.connectInfo.videoProfile
		binding.aspectRatioLayout.aspectRatio = videoProfile.width.toFloat() / videoProfile.height.toFloat()
		binding.aspectRatioLayout.mode = mode
	}

	/** La opacidad se aplica a las vistas de los controles táctiles (View), sin tocar su lógica de toques. */
	private fun applyControlsOpacity()
	{
		val alpha = controlsOpacity / 100f
		supportFragmentManager.fragments.filterIsInstance<TouchControlsFragment>().forEach { it.view?.alpha = alpha }
	}

	override fun dispatchKeyEvent(event: KeyEvent) = viewModel.input.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
	override fun onGenericMotionEvent(event: MotionEvent) = viewModel.input.onGenericMotionEvent(event) || super.onGenericMotionEvent(event)
}

enum class TransformMode
{
	FIT,
	STRETCH,
	ZOOM
}
