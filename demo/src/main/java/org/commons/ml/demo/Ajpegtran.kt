package org.commons.ml.demo

import android.content.Context
import android.net.Uri
import fr.free.nrw.commons.ajpegtran.Jpegtran
import fr.free.nrw.commons.ajpegtran.blur.BlurRegion

/**
 * Demo adapter using the same high-level API as apps-android-commons.
 */
object Ajpegtran {
    fun pixelize(
        context: Context,
        input: Uri,
        output: Uri,
        regions: List<PixelizeRegion>
    ): Result<Unit> = runCatching {
        require(regions.isNotEmpty()) { "At least one region is required" }
        val jpegtran = Jpegtran(context, input)
        try {
            jpegtran.blur(regions.map {
                BlurRegion(it.width, it.height, it.left, it.top, it.blockWidth, it.blockHeight, false)
            })
            jpegtran.save(output)
        } finally {
            jpegtran.cleanup()
        }
    }

    data class PixelizeRegion(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val blockWidth: Int = -1,
        val blockHeight: Int = -1
    ) {
        init {
            require(left >= 0 && top >= 0 && width > 0 && height > 0)
        }
    }
}
