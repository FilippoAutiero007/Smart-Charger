package com.example.smartdisplay

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

data class TrackInfo(
    val title: String = "Nessun brano",
    val artist: String = "",
    val albumArt: android.graphics.Bitmap? = null,
    val isPlaying: Boolean = false,
    val duration: Long = 0,
    val position: Long = 0,
    val packageName: String = ""
)

object MusicController {
    private var lastTrack: TrackInfo = TrackInfo()

    fun getCurrentTrack(context: Context): TrackInfo {
        try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = msm.getActiveSessions(null)

            for (controller in controllers) {
                val metadata = controller.metadata
                val state = controller.playbackState

                if (metadata != null) {
                    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Nessun brano"
                    val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    val albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                    val position = state?.position ?: 0
                    val isPlaying = state?.state == PlaybackState.STATE_PLAYING
                    val pkg = controller.packageName

                    lastTrack = TrackInfo(title, artist, albumArt, isPlaying, duration, position, pkg)
                    return lastTrack
                }
            }
        } catch (_: Exception) { }

        return lastTrack
    }

    fun sendMediaAction(context: Context, action: String) {
        try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = msm.getActiveSessions(null)

            for (controller in controllers) {
                val controls = controller.transportControls
                when (action) {
                    "play" -> controls.play()
                    "pause" -> controls.pause()
                    "next" -> controls.skipToNext()
                    "prev" -> controls.skipToPrevious()
                }
                break
            }
        } catch (_: Exception) { }
    }
}
