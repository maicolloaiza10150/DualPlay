// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.stream

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import android.view.Display
import com.hdrdualcontroller.app.lib.Codec
import com.hdrdualcontroller.app.lib.ConnectVideoProfile

/**
 * Decide si el teléfono puede mostrar el stream HDR (docs/ARCHITECTURE.md 2.2): pantalla con HDR10 y un decoder
 * HEVC Main10 por hardware. Si no, se baja a HEVC SDR sin que el usuario tenga que tocar nada.
 */
object HdrSupport
{
	private const val TAG = "HdrSupport"

	@Suppress("DEPRECATION")
	fun displaySupportsHdr10(context: Context): Boolean
	{
		val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return false
		return display.hdrCapabilities?.supportedHdrTypes?.contains(Display.HdrCapabilities.HDR_TYPE_HDR10) == true
	}

	/** Decoder HEVC por hardware que acepte Main10 (nunca decodificación por software). */
	fun hasHardwareHevcMain10Decoder(): Boolean =
		MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
			!info.isEncoder && !info.isSoftwareOnly && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) } &&
				info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC).profileLevels.any {
					it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
						it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
				}
		}

	fun isAvailable(context: Context) = displaySupportsHdr10(context) && hasHardwareHevcMain10Decoder()

	/** Devuelve el perfil pedido, o el mismo en HEVC SDR si se pidió HDR y no hay soporte. */
	fun adjust(context: Context, profile: ConnectVideoProfile): ConnectVideoProfile
	{
		if(profile.codec != Codec.CODEC_H265_HDR || isAvailable(context))
			return profile
		Log.w(TAG, "HDR no disponible (pantalla HDR10=${displaySupportsHdr10(context)}, decoder Main10=${hasHardwareHevcMain10Decoder()}); se usa HEVC SDR")
		return profile.copy(codec = Codec.CODEC_H265)
	}
}
