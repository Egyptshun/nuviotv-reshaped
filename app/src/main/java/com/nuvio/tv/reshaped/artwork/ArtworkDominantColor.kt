package com.nuvio.tv.reshaped.artwork

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import kotlin.math.max

private const val PALETTE_SAMPLE_PX = 96
private const val CACHE_CAPACITY = 256

object ArtworkDominantColorCache {
    private val entries = LinkedHashMap<String, Color>(CACHE_CAPACITY, 0.75f, true)

    fun get(url: String): Color? {
        val color = entries.remove(url) ?: return null
        entries[url] = color
        return color
    }

    fun put(url: String, color: Color) {
        entries.remove(url)
        entries[url] = color
        if (entries.size > CACHE_CAPACITY) {
            entries.remove(entries.keys.first())
        }
    }
}

suspend fun resolveArtworkDominantColor(context: Context, imageUrl: String): Color? {
    val key = imageUrl.takeIf { it.isNotBlank() } ?: return null
    ArtworkDominantColorCache.get(key)?.let { return it }
    return withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(key)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .size(Size(PALETTE_SAMPLE_PX, PALETTE_SAMPLE_PX))
            .build()
        val result = try {
            context.imageLoader.execute(request)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        val bitmap = ((result as? SuccessResult)?.image as? BitmapImage)?.bitmap
            ?: return@withContext null
        val color = sampledProminentColor(bitmap) ?: return@withContext null
        ArtworkDominantColorCache.put(key, color)
        color
    }
}

internal fun Color.blendTowards(target: Color, fraction: Float): Color {
    val clamped = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * clamped,
        green = green + (target.green - green) * clamped,
        blue = blue + (target.blue - blue) * clamped,
        alpha = alpha + (target.alpha - alpha) * clamped,
    )
}

private fun sampledProminentColor(bitmap: Bitmap): Color? {
    if (bitmap.width <= 0 || bitmap.height <= 0) return null

    val stepX = max(1, bitmap.width / 14)
    val stepY = max(1, bitmap.height / 14)
    val hsv = FloatArray(3)
    var weightedRed = 0f
    var weightedGreen = 0f
    var weightedBlue = 0f
    var totalWeight = 0f

    for (y in 0 until bitmap.height step stepY) {
        for (x in 0 until bitmap.width step stepX) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel) / 255f
            if (alpha < 0.35f) continue
            android.graphics.Color.colorToHSV(pixel, hsv)
            if (hsv[2] < 0.08f) continue
            val saturation = hsv[1].coerceIn(0f, 1f)
            val value = hsv[2].coerceIn(0f, 1f)
            val weight = alpha * (0.35f + saturation * 1.65f) * (0.50f + value)
            weightedRed += android.graphics.Color.red(pixel) * weight
            weightedGreen += android.graphics.Color.green(pixel) * weight
            weightedBlue += android.graphics.Color.blue(pixel) * weight
            totalWeight += weight
        }
    }

    if (totalWeight <= 0f) return null

    return stabilizeDominantColor(
        Color(
            red = (weightedRed / totalWeight) / 255f,
            green = (weightedGreen / totalWeight) / 255f,
            blue = (weightedBlue / totalWeight) / 255f,
            alpha = 1f
        )
    )
}

private fun stabilizeDominantColor(color: Color): Color {
    val opaque = color.copy(alpha = 1f)
    val balanced = when {
        opaque.luminance() < 0.16f -> lerp(opaque, Color.White, 0.34f)
        opaque.luminance() > 0.72f -> lerp(opaque, Color.Black, 0.32f)
        else -> opaque
    }
    return balanced.copy(alpha = 1f)
}
