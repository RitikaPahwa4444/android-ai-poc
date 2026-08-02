package github.kamemak.ajpegtran_example

import android.os.ParcelFileDescriptor

/**
 * Small Kotlin-facing adapter for the ajpegtran JNI API.
 *
 * The native library is supplied by the Commons ajpegtran module:
 * https://github.com/commons-app/ajpegtran
 */
object Ajpegtran {
    init {
        System.loadLibrary("ajpegtran")
    }

    @JvmStatic
    private external fun ajpegtran(rfd: Int, wfd: Int, options: String): String

    fun pixelize(
        input: ParcelFileDescriptor,
        output: ParcelFileDescriptor,
        regions: List<PixelizeRegion>
    ): Result<Unit> = runCatching {
        require(regions.isNotEmpty()) { "At least one region is required" }
        val options = regions.joinToString(" ") {
            "-pixelize ${it.width}x${it.height}+${it.left}+${it.top}"
        } + " -optimize -copy all -rmgeotag -rmthumbnail"
        val result = ajpegtran(input.detachFd(), output.detachFd(), options)
        check(result == "OK") { result }
    }

    data class PixelizeRegion(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    ) {
        init {
            require(left >= 0 && top >= 0 && width > 0 && height > 0)
        }
    }
}
