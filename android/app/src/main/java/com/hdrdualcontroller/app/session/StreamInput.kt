package com.hdrdualcontroller.app.session

import android.content.Context
import android.hardware.*
import android.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.hdrdualcontroller.app.common.Preferences
import com.hdrdualcontroller.app.input.ButtonMapping
import com.hdrdualcontroller.app.lib.ControllerState

class StreamInput(val context: Context, val preferences: Preferences)
{
	var controllerStateChangedCallback: ((ControllerState) -> Unit)? = null

	val controllerState: ControllerState get()
	{
		val controllerState = sensorControllerState or keyControllerState or motionControllerState or padControllerState

		val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
		@Suppress("DEPRECATION")
		when(windowManager.defaultDisplay.rotation)
		{
			Surface.ROTATION_90 -> {
				controllerState.accelX *= -1.0f
				controllerState.accelZ *= -1.0f
				controllerState.gyroX *= -1.0f
				controllerState.gyroZ *= -1.0f
				controllerState.orientX *= -1.0f
				controllerState.orientZ *= -1.0f
			}
			else -> {}
		}

		// prioritize motion controller's l2 and r2 over key
		// (some controllers send only key, others both but key earlier than full press)
		if(motionControllerState.l2State > 0U)
			controllerState.l2State = motionControllerState.l2State
		if(motionControllerState.r2State > 0U)
			controllerState.r2State = motionControllerState.r2State

		return controllerState or touchControllerState
	}

	private val sensorControllerState = ControllerState() // from Motion Sensors
	private val keyControllerState = ControllerState() // from KeyEvents
	private val motionControllerState = ControllerState() // from MotionEvents
	private val padControllerState = ControllerState() // from the DualSense touchpad (captured pointer)
	private val padTouchIds = HashMap<Int, UByte>() // id del puntero de Android -> id de toque de PlayStation
	var touchControllerState = ControllerState()
		set(value)
		{
			field = value
			controllerStateUpdated()
		}

	private val buttonMapping = ButtonMapping.parse(preferences.buttonRemap, preferences.swapCrossMoon)

	private val sensorEventListener = object: SensorEventListener {
		override fun onSensorChanged(event: SensorEvent)
		{
			when(event.sensor.type)
			{
				Sensor.TYPE_ACCELEROMETER -> {
					sensorControllerState.accelX = event.values[1] / SensorManager.GRAVITY_EARTH
					sensorControllerState.accelY = event.values[2] / SensorManager.GRAVITY_EARTH
					sensorControllerState.accelZ = event.values[0] / SensorManager.GRAVITY_EARTH
				}
				Sensor.TYPE_GYROSCOPE -> {
					sensorControllerState.gyroX = event.values[1]
					sensorControllerState.gyroY = event.values[2]
					sensorControllerState.gyroZ = event.values[0]
				}
				Sensor.TYPE_ROTATION_VECTOR -> {
					val q = floatArrayOf(0f, 0f, 0f, 0f)
					SensorManager.getQuaternionFromVector(q, event.values)
					sensorControllerState.orientX = q[2]
					sensorControllerState.orientY = q[3]
					sensorControllerState.orientZ = q[1]
					sensorControllerState.orientW = q[0]
				}
				else -> return
			}
			controllerStateUpdated()
		}

		override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
	}

	private val motionLifecycleObserver = object: LifecycleObserver {
		@OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
		fun onResume()
		{
			val samplingPeriodUs = 4000
			val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
			listOfNotNull(
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
				sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
			).forEach {
				sensorManager.registerListener(sensorEventListener, it, samplingPeriodUs)
			}
		}

		@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
		fun onPause()
		{
			val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
			sensorManager.unregisterListener(sensorEventListener)
		}
	}

	fun observe(lifecycleOwner: LifecycleOwner)
	{
		if(preferences.motionEnabled)
			lifecycleOwner.lifecycle.addObserver(motionLifecycleObserver)
	}

	private fun controllerStateUpdated()
	{
		controllerStateChangedCallback?.let { it(controllerState) }
	}

	fun dispatchKeyEvent(event: KeyEvent): Boolean
	{
		//Log.i("StreamSession", "key event $event")
		if(event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP)
			return false

		// DUALPLAY: la tecla se traduce con la tabla de remapeo (ver input/ButtonMapping.kt) en vez de un `when` fijo.
		val target = buttonMapping.resolve(event.keyCode) ?: return false
		val down = event.action == KeyEvent.ACTION_DOWN
		when(target.analogTrigger)
		{
			1 -> keyControllerState.l2State = if(down) UByte.MAX_VALUE else 0U
			2 -> keyControllerState.r2State = if(down) UByte.MAX_VALUE else 0U
			else -> keyControllerState.buttons = keyControllerState.buttons.let { if(down) it or target.mask else it and target.mask.inv() }
		}

		controllerStateUpdated()
		return true
	}

	/**
	 * DUALPLAY: touchpad del DualSense. Con la captura de puntero activa, Android entrega el touchpad como SOURCE_TOUCHPAD
	 * con la posición ABSOLUTA de cada dedo (View.requestPointerCapture) y deja de dibujar el cursor. La posición se
	 * escala del rango del dispositivo a la del touchpad de PlayStation (1920x942) y el clic va en buttonState.
	 */
	fun onCapturedPointerEvent(event: MotionEvent): Boolean
	{
		if(!event.isFromSource(InputDevice.SOURCE_TOUCHPAD))
			return false
		val xRange = event.device?.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_TOUCHPAD)
		val yRange = event.device?.getMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_TOUCHPAD)
		fun scale(value: Float, min: Float, max: Float, size: UShort): UShort
		{
			val span = max - min
			val f = if(span > 0f) ((value - min) / span).coerceIn(0f, 1f) else 0f
			return (f * (size.toInt() - 1)).toInt().toUShort()
		}
		fun posX(index: Int) = scale(event.getX(index), xRange?.min ?: 0f, xRange?.max ?: ControllerState.TOUCHPAD_WIDTH.toFloat(), ControllerState.TOUCHPAD_WIDTH)
		fun posY(index: Int) = scale(event.getY(index), yRange?.min ?: 0f, yRange?.max ?: ControllerState.TOUCHPAD_HEIGHT.toFloat(), ControllerState.TOUCHPAD_HEIGHT)

		when(event.actionMasked)
		{
			MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
				val index = event.actionIndex
				padControllerState.startTouch(posX(index), posY(index))?.let { padTouchIds[event.getPointerId(index)] = it }
			}
			MotionEvent.ACTION_MOVE -> {
				for(index in 0 until event.pointerCount)
					padTouchIds[event.getPointerId(index)]?.let { padControllerState.setTouchPos(it, posX(index), posY(index)) }
			}
			MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
				padTouchIds.remove(event.getPointerId(event.actionIndex))?.let { padControllerState.stopTouch(it) }
			}
			MotionEvent.ACTION_CANCEL -> {
				padTouchIds.values.forEach { padControllerState.stopTouch(it) }
				padTouchIds.clear()
			}
		}
		padControllerState.buttons =
			if(event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) ControllerState.BUTTON_TOUCHPAD else 0U
		controllerStateUpdated()
		return true
	}

	fun onGenericMotionEvent(event: MotionEvent): Boolean
	{
		if(event.source and InputDevice.SOURCE_CLASS_JOYSTICK != InputDevice.SOURCE_CLASS_JOYSTICK)
			return false
		fun Float.signedAxis() = (this * Short.MAX_VALUE).toInt().toShort()
		fun Float.unsignedAxis() = (this * UByte.MAX_VALUE.toFloat()).toUInt().toUByte()
		motionControllerState.leftX = event.getAxisValue(MotionEvent.AXIS_X).signedAxis()
		motionControllerState.leftY = event.getAxisValue(MotionEvent.AXIS_Y).signedAxis()
		motionControllerState.rightX = event.getAxisValue(MotionEvent.AXIS_Z).signedAxis()
		motionControllerState.rightY = event.getAxisValue(MotionEvent.AXIS_RZ).signedAxis()
		motionControllerState.l2State = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).unsignedAxis()
		motionControllerState.r2State = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).unsignedAxis()
		motionControllerState.buttons = motionControllerState.buttons.let {
			val dpadX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
			val dpadY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
			val dpadButtons =
				(if(dpadX > 0.5f) ControllerState.BUTTON_DPAD_RIGHT else 0U) or
						(if(dpadX < -0.5f) ControllerState.BUTTON_DPAD_LEFT else 0U) or
						(if(dpadY > 0.5f) ControllerState.BUTTON_DPAD_DOWN else 0U) or
						(if(dpadY < -0.5f) ControllerState.BUTTON_DPAD_UP else 0U)
			it and (ControllerState.BUTTON_DPAD_RIGHT or
					ControllerState.BUTTON_DPAD_LEFT or
					ControllerState.BUTTON_DPAD_DOWN or
					ControllerState.BUTTON_DPAD_UP).inv() or
					dpadButtons
		}
		//Log.i("StreamSession", "motionEvent => $motionControllerState")
		controllerStateUpdated()
		return true
	}
}