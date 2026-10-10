// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log

/**
 * Captura el micrófono del teléfono para el chat de voz de la PS5: 48 kHz mono, frames de 480 muestras (10 ms) que se
 * duplican a estéreo (la PS5 espera 2 canales, como el escritorio de chiaki-ng) y se entregan a `onFrame`.
 *
 * Usa siempre el micrófono INTEGRADO, no el de unos auriculares Bluetooth: abrir el micrófono por HFP/SCO degrada la
 * salida de audio del juego a banda telefónica. La fuente VOICE_COMMUNICATION activa la cancelación de eco y ruido del
 * sistema, y además se piden explícitamente `AcousticEchoCanceler` y `NoiseSuppressor` si existen (el juego suena por
 * los altavoces del teléfono y se colaría en el micrófono).
 */
class MicrophoneCapture(private val onFrame: (ShortArray) -> Unit)
{
	private var record: AudioRecord? = null
	private var thread: Thread? = null
	@Volatile private var running = false
	private var echoCanceler: AcousticEchoCanceler? = null
	private var noiseSuppressor: NoiseSuppressor? = null

	/** false si falta el permiso o no se pudo abrir el micrófono. */
	fun start(context: Context): Boolean
	{
		if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
			return false
		try
		{
			val format = AudioFormat.Builder()
				.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
				.setSampleRate(SAMPLE_RATE)
				.setChannelMask(AudioFormat.CHANNEL_IN_MONO)
				.build()
			val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
			val rec = AudioRecord.Builder()
				.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
				.setAudioFormat(format)
				.setBufferSizeInBytes(maxOf(minBuffer, FRAME_SAMPLES * 2 * 4))
				.build()
			if(rec.state != AudioRecord.STATE_INITIALIZED)
			{
				rec.release()
				return false
			}
			val audioManager = context.getSystemService(AudioManager::class.java)
			audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
				.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
				?.let { rec.preferredDevice = it }
			if(AcousticEchoCanceler.isAvailable())
				echoCanceler = AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.enabled = true }
			if(NoiseSuppressor.isAvailable())
				noiseSuppressor = NoiseSuppressor.create(rec.audioSessionId)?.also { it.enabled = true }
			Log.i(TAG, "Micrófono: eco=${echoCanceler != null} ruido=${noiseSuppressor != null} dispositivo=${rec.routedDevice?.productName}")

			rec.startRecording()
			record = rec
			running = true
			thread = Thread(::loop, "MicrophoneCapture").also { it.start() }
			return true
		}
		catch(e: Exception)
		{
			Log.w(TAG, "No se pudo abrir el micrófono: ${e.javaClass.simpleName}: ${e.message}")
			stop()
			return false
		}
	}

	private fun loop()
	{
		Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
		val rec = record ?: return
		val mono = ShortArray(FRAME_SAMPLES)
		val stereo = ShortArray(FRAME_SAMPLES * 2)
		var frames = 0L
		while(running)
		{
			var filled = 0
			while(running && filled < FRAME_SAMPLES)
			{
				val n = rec.read(mono, filled, FRAME_SAMPLES - filled, AudioRecord.READ_BLOCKING)
				if(n < 0)
				{
					Log.w(TAG, "Error de lectura del micrófono: $n")
					return
				}
				filled += n
			}
			if(!running)
				break
			for(i in 0 until FRAME_SAMPLES)
			{
				stereo[i * 2] = mono[i]
				stereo[i * 2 + 1] = mono[i]
			}
			onFrame(stereo)
			if(++frames == 1L)
				Log.i(TAG, "Micrófono: primer frame enviado")
		}
	}

	fun stop()
	{
		running = false
		try { thread?.join(500) } catch(_: InterruptedException) {}
		thread = null
		echoCanceler?.release(); echoCanceler = null
		noiseSuppressor?.release(); noiseSuppressor = null
		record?.let {
			try { it.stop() } catch(_: IllegalStateException) {}
			it.release()
		}
		record = null
	}

	private companion object
	{
		const val TAG = "DualPlay"
		const val SAMPLE_RATE = 48000
		const val FRAME_SAMPLES = 480 // 10 ms
	}
}
