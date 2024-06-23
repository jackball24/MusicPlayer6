package org.akanework.gramophone.logic.utils

import android.content.Context
import android.content.res.Configuration
import androidx.core.graphics.ColorUtils
import kotlin.math.min

object ColorUtils {

    enum class ColorType(
        var chroma: Float = 0f,
        var chromaDark: Float = 0f,
        var lighting: Float = 0f,
        var lightingDark: Float = 0f
    ) {
        COLOR_BACKGROUND_ELEVATED(1.2f, 1.2f, 0.99f, 0.99f),
        COLOR_BACKGROUND(1.0f, 0.9f, 1.015f, 1.015f),
        COLOR_CONTRAST_FAINTED(0.7f, 0.8f, 0.97f, 0.5f)
    }

    private fun manipulateHsl(
        color: Int,
        colorType: ColorType,
        context: Context
    ): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)

        if ((context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            hsl[2] *= colorType.lightingDark
            hsl[2] = min(hsl[2], 1f)
            hsl[1] *= colorType.chromaDark
            hsl[1] = min(hsl[1], 1f)
        } else {
            hsl[1] *= colorType.chroma
            hsl[1] = min(hsl[1], 1f)
            hsl[2] *= colorType.lighting
            hsl[2] = min(hsl[2], 1f)
        }

        return ColorUtils.HSLToColor(hsl)
    }

    fun getColor(color: Int, colorType: ColorType, context: Context): Int =
        manipulateHsl(color, colorType, context)
}