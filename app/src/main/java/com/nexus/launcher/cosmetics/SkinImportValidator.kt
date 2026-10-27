package com.nexus.launcher.cosmetics

import android.graphics.BitmapFactory
import java.io.File

data class SkinValidationResult(
    val ok: Boolean,
    val message: String,
    val width: Int = 0,
    val height: Int = 0,
    val modelHint: String? = null
)

/**
 * Validates Minecraft skin PNG dimensions (64x64 or 64x32 legacy).
 */
object SkinImportValidator {

    private const val MAX_BYTES = 2 * 1024 * 1024 // 2 MB

    fun validate(file: File): SkinValidationResult {
        if (!file.exists()) return SkinValidationResult(false, "File not found")
        if (file.length() > MAX_BYTES) return SkinValidationResult(false, "File too large (max 2 MB)")
        if (!file.name.endsWith(".png", ignoreCase = true)) {
            return SkinValidationResult(false, "Only PNG skins are supported")
        }

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) return SkinValidationResult(false, "Not a valid image")

        return when {
            w == 64 && h == 64 -> SkinValidationResult(true, "Valid 64×64 skin", w, h, "classic/slim")
            w == 64 && h == 32 -> SkinValidationResult(true, "Valid legacy 64×32 skin", w, h, "classic")
            w == 128 && h == 128 -> SkinValidationResult(true, "Valid HD 128×128 skin", w, h, "classic/slim")
            else -> SkinValidationResult(
                false,
                "Unsupported size ${w}×${h}. Use 64×64, 64×32, or 128×128 PNG."
            )
        }
    }
}
