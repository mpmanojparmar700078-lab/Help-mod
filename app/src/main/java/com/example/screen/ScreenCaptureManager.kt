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
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenCaptureManager(private val context: Context) {
    companion object {
        private const val TAG = "ScreenCaptureManager"
    }

    private val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    // Background thread for non-blocking image acquisition
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _latestFrame = MutableStateFlow<Bitmap?>(null)
    val latestFrame: StateFlow<Bitmap?> = _latestFrame.asStateFlow()

    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 320
    private val frameLock = Any()

    fun startCapture(resultCode: Int, data: Intent) {
        stopCapture()

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        screenDensity = metrics.densityDpi

        // Normalize max dimension to 1080 for quick encoding & high OCR clarity
        val maxDimension = 1080
        val scale = if (metrics.widthPixels > maxDimension || metrics.heightPixels > maxDimension) {
            maxDimension.toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels)
        } else {
            1.0f
        }

        screenWidth = (metrics.widthPixels * scale).toInt()
        screenHeight = (metrics.heightPixels * scale).toInt()
        if (screenWidth % 2 != 0) screenWidth--
        if (screenHeight % 2 != 0) screenHeight--

        Log.d(TAG, "Starting capture with dimensions: ${screenWidth}x${screenHeight}, density: $screenDensity")

        try {
            val thread = HandlerThread("ScreenCaptureBackgroundThread").apply { start() }
            handlerThread = thread
            val handler = Handler(thread.looper)
            backgroundHandler = handler

            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            if (mediaProjection == null) {
                Log.e(TAG, "mediaProjection returned null")
                _isCapturing.value = false
                return
            }

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped by system")
                    stopCapture()
                }
            }, handler)

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
                handler
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                processAvailableImage(reader)
            }, handler)

            _isCapturing.value = true
            Log.i(TAG, "Screen capture started successfully")
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
            buffer.rewind() // Ensure read position is 0

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

            synchronized(frameLock) {
                val old = _latestFrame.value
                _latestFrame.value = cleanBitmap
                if (old != null && old != cleanBitmap && !old.isRecycled) {
                    old.recycle()
                }
            }
        } catch (e: Exception) {
            // Transient image buffer read errors (e.g. buffer closed)
        } finally {
            try {
                image?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Obtains an immutable, safe copy of the latest screen frame for AI analysis
     */
    fun getLatestFrame(): Bitmap? {
        synchronized(frameLock) {
            val frame = _latestFrame.value
            return if (frame != null && !frame.isRecycled) {
                try {
                    frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, false)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to copy bitmap: ${e.message}")
                    null
                }
            } else {
                null
            }
        }
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

            handlerThread?.quitSafely()
            handlerThread = null
            backgroundHandler = null

            synchronized(frameLock) {
                _latestFrame.value?.let {
                    if (!it.isRecycled) it.recycle()
                }
                _latestFrame.value = null
            }
            Log.i(TAG, "Screen capture stopped and resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture: ${e.message}", e)
        }
    }
}
