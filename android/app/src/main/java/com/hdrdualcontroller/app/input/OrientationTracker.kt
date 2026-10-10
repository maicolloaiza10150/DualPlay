// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.input

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Orientación del control a partir de giroscopio (rad/s) y acelerómetro (g): port a Kotlin de `lib/src/orientation.c`
 * de chiaki-ng (filtro IMU de Madgwick con calentamiento y filtro "fuzz"), para dar a la PS5 el mismo cuaternión que
 * da el escritorio. Los sensores del teléfono no lo necesitan (Android ya entrega TYPE_ROTATION_VECTOR); los del
 * control no tienen vector de rotación, así que se calcula aquí.
 */
class OrientationTracker
{
	// Cuaternión interno de Madgwick: arranca con 90° alrededor de x, igual que chiaki_orientation_init.
	private var w = COS_1_4_PI
	private var x = SIN_1_4_PI
	private var y = 0f
	private var z = 0f

	var gyroX = 0f; private set
	var gyroY = 0f; private set
	var gyroZ = 0f; private set
	var accelX = 0f; private set
	var accelY = 1f; private set
	var accelZ = 0f; private set

	private var timestampUs = 0L
	private var sampleIndex = 0

	/** Cuaternión de orientación para la PS5 (x, y, z, w), con la rotación de -90° alrededor de x ya aplicada. */
	var orientX = 0f; private set
	var orientY = 0f; private set
	var orientZ = 0f; private set
	var orientW = 1f; private set

	/** `timestampUs`: reloj monotónico en µs (se trabaja en 32 bits con vuelta, como `uint32_t timestamp_us`). */
	fun update(gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float, timestampUs: Long)
	{
		gyroX = gx; gyroY = gy; gyroZ = gz
		accelX = ax; accelY = ay; accelZ = az
		sampleIndex++
		val ts = timestampUs and 0xFFFFFFFFL
		if(sampleIndex <= 1)
		{
			this.timestampUs = ts
			return
		}
		var delta = ts
		if(delta < this.timestampUs)
			delta += 1L shl 32
		delta -= this.timestampUs
		this.timestampUs = ts
		madgwick(gx, gy, gz, ax, ay, az, if(sampleIndex < WARMUP_SAMPLES) BETA_WARMUP else BETA_DEFAULT, delta / 1_000_000f)
		// -90° alrededor de x (apply_to_controller_state de la lib)
		orientW = COS_NEG_1_4_PI * w - SIN_NEG_1_4_PI * x
		orientX = COS_NEG_1_4_PI * x + SIN_NEG_1_4_PI * w
		orientY = COS_NEG_1_4_PI * y - SIN_NEG_1_4_PI * z
		orientZ = COS_NEG_1_4_PI * z + SIN_NEG_1_4_PI * y
	}

	private fun madgwick(gx: Float, gy: Float, gz: Float, axIn: Float, ayIn: Float, azIn: Float, beta: Float, dt: Float)
	{
		var ax = axIn; var ay = ayIn; var az = azIn
		var q0 = w; var q1 = x; var q2 = y; var q3 = z
		var qDot1 = 0.5f * (-q1 * gx - q2 * gy - q3 * gz)
		var qDot2 = 0.5f * (q0 * gx + q2 * gz - q3 * gy)
		var qDot3 = 0.5f * (q0 * gy - q1 * gz + q3 * gx)
		var qDot4 = 0.5f * (q0 * gz + q1 * gy - q2 * gx)

		if(!(ax == 0f && ay == 0f && az == 0f))
		{
			var recip = invSqrt(ax * ax + ay * ay + az * az)
			ax *= recip; ay *= recip; az *= recip

			val _2q0 = 2f * q0; val _2q1 = 2f * q1; val _2q2 = 2f * q2; val _2q3 = 2f * q3
			val _4q0 = 4f * q0; val _4q1 = 4f * q1; val _4q2 = 4f * q2
			val _8q1 = 8f * q1; val _8q2 = 8f * q2
			val q0q0 = q0 * q0; val q1q1 = q1 * q1; val q2q2 = q2 * q2; val q3q3 = q3 * q3

			var s0 = _4q0 * q2q2 + _2q2 * ax + _4q0 * q1q1 - _2q1 * ay
			var s1 = _4q1 * q3q3 - _2q3 * ax + 4f * q0q0 * q1 - _2q0 * ay - _4q1 + _8q1 * q1q1 + _8q1 * q2q2 + _4q1 * az
			var s2 = 4f * q0q0 * q2 + _2q0 * ax + _4q2 * q3q3 - _2q3 * ay - _4q2 + _8q2 * q1q1 + _8q2 * q2q2 + _4q2 * az
			var s3 = 4f * q1q1 * q3 - _2q1 * ax + 4f * q2q2 * q3 - _2q2 * ay
			recip = s0 * s0 + s1 * s1 + s2 * s2 + s3 * s3
			if(recip > 0.000001f)
			{
				recip = invSqrt(recip)
				s0 *= recip; s1 *= recip; s2 *= recip; s3 *= recip
				qDot1 -= beta * s0
				qDot2 -= beta * s1
				qDot3 -= beta * s2
				qDot4 -= beta * s3
			}
		}

		q0 += qDot1 * dt; q1 += qDot2 * dt; q2 += qDot3 * dt; q3 += qDot4 * dt
		val recip = invSqrt(q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3)
		q0 *= recip; q1 *= recip; q2 *= recip; q3 *= recip

		w = fuzz(q0, w); x = fuzz(q1, x); y = fuzz(q2, y); z = fuzz(q3, z)
	}

	// Filtro de entrada como el del kernel (fuzz de la lib: ORIENT_FUZZ, pesos 0.75 y 0.6).
	private fun fuzz(cur: Float, prev: Float): Float = when
	{
		abs(cur - prev) < ORIENT_FUZZ / 2f -> prev
		abs(cur - prev) < ORIENT_FUZZ -> PREV_WEIGHT * prev + (1 - PREV_WEIGHT) * cur
		abs(cur - prev) < ORIENT_FUZZ * 2f -> PREV_WEIGHT_2X * prev + (1 - PREV_WEIGHT_2X) * cur
		else -> cur
	}

	private fun invSqrt(v: Float) = 1f / sqrt(v)

	private companion object
	{
		const val SIN_1_4_PI = 0.7071067811865475f
		const val SIN_NEG_1_4_PI = -0.7071067811865475f
		const val COS_1_4_PI = 0.7071067811865476f
		const val COS_NEG_1_4_PI = 0.7071067811865476f
		const val WARMUP_SAMPLES = 30
		const val BETA_WARMUP = 20f
		const val BETA_DEFAULT = 0.05f
		const val ORIENT_FUZZ = 0.0007f
		const val PREV_WEIGHT = 0.75f
		const val PREV_WEIGHT_2X = 0.6f
	}
}
