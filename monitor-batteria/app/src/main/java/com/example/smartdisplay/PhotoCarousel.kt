package com.example.smartdisplay

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

@Composable
fun PhotoCarousel(
    photoUris: List<String>,
    effect: TransitionEffect = TransitionEffect.FADE,
    intervalSecs: Int = 10,
    modifier: Modifier = Modifier,
    contentDescription: String = "Photo"
) {
    if (photoUris.isEmpty()) return

    val context = LocalContext.current
    val bitmaps = remember(photoUris) {
        mutableStateOf(emptyList<ImageBitmap>())
    }
    var currentIndex by remember { mutableIntStateOf(0) }
    var prevIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(photoUris) {
        val loaded = photoUris.mapNotNull { uriStr ->
            try {
                val uri = Uri.parse(uriStr)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            } catch (_: Exception) { null }
        }
        bitmaps.value = loaded
    }

    val bmps = bitmaps.value
    if (bmps.isEmpty()) return

    LaunchedEffect(intervalSecs, currentIndex) {
        delay((intervalSecs * 1000).toLong())
        prevIndex = currentIndex
        currentIndex = (currentIndex + 1) % bmps.size
    }

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = when (effect) {
            TransitionEffect.CUT -> 1
            else -> 600
        }),
        label = "transition"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height

        val prevBmp = bmps.getOrNull(prevIndex) ?: bmps[currentIndex]
        val curBmp = bmps[currentIndex]
        val dstSize = IntSize(canvasW.toInt(), canvasH.toInt())

        when (effect) {
            TransitionEffect.FADE -> {
                drawImage(prevBmp, dstSize = dstSize, alpha = 1f - animProgress)
                drawImage(curBmp, dstSize = dstSize, alpha = animProgress)
            }
            TransitionEffect.SLIDE -> {
                drawImage(prevBmp, dstSize = dstSize)
                val offsetX = canvasW * (1f - animProgress)
                withTransform({
                    translate(left = offsetX)
                }) {
                    drawImage(curBmp, dstSize = dstSize)
                }
            }
            TransitionEffect.CUT -> {
                drawImage(
                    image = if (animProgress >= 0.5f) curBmp else prevBmp,
                    dstSize = dstSize
                )
            }
            TransitionEffect.ZOOM -> {
                drawImage(prevBmp, dstSize = dstSize, alpha = 1f - animProgress)
                val s = 0.8f + 0.2f * animProgress
                withTransform({
                    scale(s, s, pivot = Offset(canvasW / 2f, canvasH / 2f))
                }) {
                    drawImage(curBmp, dstSize = dstSize, alpha = animProgress)
                }
            }
        }
    }
}
