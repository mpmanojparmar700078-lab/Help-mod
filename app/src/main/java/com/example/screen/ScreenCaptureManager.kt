package com.example.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class ScreenCaptureManager(private val context: Context) {
    companion object {
        private const val TAG = "ScreenCaptureManager"
    }

    private val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val backgroundHandler = Handler(Looper.getMainLooper())

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _latestFrame = MutableStateFlow<Bitmap?>(null)
    val latestFrame: StateFlow<Bitmap?> = _latestFrame.asStateFlow()

    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 320

    private var lastSampleHash: Long = 0L
    private var consecutiveStaticCount = 0

    fun startCapture(resultCode: Int, data: Intent) {
        stopCapture()

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        screenDensity = metrics.densityDpi
        // Scale down resolution for efficiency & mobile network optimization
        val maxDimension = 1080
        val scale = if (metrics.widthPixels > maxDimension || metrics.heightPixels > maxDimension) {
            maxDimension.toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels)
        } else {
            1.0f
        }

        screenWidth = (metrics.widthPixels * scale).toInt()
        screenHeight = (metrics.heightPixels * scale).toInt()
        // Ensure even dimensions
        if (screenWidth % 2 != 0) screenWidth--
        if (screenHeight % 2 != 0) screenHeight--

        Log.d(TAG, "Starting capture with size: ${screenWidth}x${screenHeight}, density: $screenDensity")

        try {
            mediaProjection = try {
                mediaProjectionManager.getMediaProjection(resultCode, data)
            } catch (se: SecurityException) {
                Log.w(TAG, "SecurityException on initial getMediaProjection attempt (${se.message}). Waiting for foreground service transition and retrying...")
                Thread.sleep(200)
                mediaProjectionManager.getMediaProjection(resultCode, data)
            }

            if (mediaProjection == null) {
                Log.e(TAG, "mediaProjection is null")
                _isCapturing.value = false
                return
            }

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped by system callback")
                    stopCapture()
                }
            }, backgroundHandler)

            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "LiveScreenGuideProjection",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                backgroundHandler
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                processAvailableImage(reader)
            }, backgroundHandler)

            _isCapturing.value = true
            Log.i(TAG, "Screen capture initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting capture: ${e.message}", e)
            stopCapture()
        }
    }

    private fun processAvailableImage(reader: ImageReader) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return
            val planes = image.planes
            if (planes.isEmpty()) return

            val buffer = planes[0].buffer
            buffer.rewind()
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val cleanBitmap = if (rowPadding != 0) {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                bitmap.recycle()
                cropped
            } else {
                bitmap
            }

            // Adaptive frame check: sample pixel hash to detect changes
            val currentHash = computeSampleHash(cleanBitmap)
            if (abs(currentHash - lastSampleHash) > 1000L) {
                lastSampleHash = currentHash
                consecutiveStaticCount = 0
                _latestFrame.value = cleanBitmap
            } else {
                consecutiveStaticCount++
                // Update frame reference periodically or if no frame is set
                if (consecutiveStaticCount % 10 == 0 || _latestFrame.value == null) {
                    _latestFrame.value = cleanBitmap
                }
            }
        } catch (e: Exception) {
            // Transient image buffer acquisition errors
        } finally {
            try {
                image?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun computeSampleHash(bitmap: Bitmap): Long {
        var hash = 0L
        val stepX = maxOf(1, bitmap.width / 16)
        val stepY = maxOf(1, bitmap.height / 16)
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                hash = 31 * hash + bitmap.getPixel(x, y)
            }
        }
        return hash
    }

    fun getLatestFrame(): Bitmap? {
        return _latestFrame.value
    }

    fun stopCapture() {
        _isCapturing.value = false
        try {
            virtualDisplay?.release()
            virtualDisplay = null

            imageReader?.close()
            imageReader = null

            mediaProjection?.stop()
            mediaProjection = null

            _latestFrame.value = null
            Log.i(TAG, "Screen capture stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture: ${e.message}", e)
        }
    }
}
