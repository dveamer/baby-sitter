package com.dveamer.babysitter.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraMonitor(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val diffThreshold: Int = DEFAULT_DIFF_THRESHOLD,
    private val minChangedRatio: Double = DEFAULT_MIN_CHANGED_RATIO,
    override val id: String = "camera"
) : Monitor {

    private val mutableSignals = MutableSharedFlow<MonitorSignal>(extraBufferCapacity = 16)
    override val signals: Flow<MonitorSignal> = mutableSignals.asSharedFlow()

    private var job: Job? = null
    private val latestFrame = AtomicReference<FrameData?>(null)

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var imageReader: ImageReader? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    override suspend fun start() {
        if (job != null) return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "camera permission denied")
            return
        }

        val started = runCatching { startCameraCapture() }
            .onFailure { Log.w(TAG, "camera start failed", it) }
            .isSuccess
        if (!started) return

        job = scope.launch(Dispatchers.Default) {
            var previous: FrameData? = null
            while (isActive) {
                val current = latestFrame.get()
                val active = when {
                    current == null -> false
                    previous == null -> false
                    current.timestampMs == previous.timestampMs -> false
                    else -> detectMovement(previous, current)
                }

                mutableSignals.tryEmit(
                    MonitorSignal(
                        monitorId = id,
                        kind = MonitorKind.CAMERA,
                        active = active
                    )
                )

                if (current != null) {
                    previous = current
                }
                delay(1_000)
            }
        }
    }

    override suspend fun stop() {
        job?.cancel()
        job = null
        stopCameraCapture()
    }

    @Suppress("MissingPermission")
    private fun startCameraCapture() {
        val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = selectRearCameraId(manager) ?: throw IllegalStateException("rear camera not found")

        val thread = HandlerThread("CameraMonitor").apply { start() }
        val callbackHandler = Handler(thread.looper)
        handlerThread = thread
        handler = callbackHandler

        val reader = ImageReader.newInstance(CAPTURE_WIDTH, CAPTURE_HEIGHT, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener({ ir ->
                val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
                onImageAvailable(image)
            }, callbackHandler)
        }
        imageReader = reader

        val latch = CountDownLatch(1)
        var openError: Throwable? = null

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                val requestBuilder = runCatching {
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(reader.surface)
                        set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        )
                    }
                }.getOrElse { e ->
                    openError = e
                    latch.countDown()
                    return
                }

                camera.createCaptureSession(
                    listOf(reader.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            runCatching {
                                session.setRepeatingRequest(requestBuilder.build(), null, callbackHandler)
                            }.onFailure { e ->
                                openError = e
                            }
                            latch.countDown()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            openError = IllegalStateException("camera session configure failed")
                            latch.countDown()
                        }
                    },
                    callbackHandler
                )
            }

            override fun onDisconnected(camera: CameraDevice) {
                openError = IllegalStateException("camera disconnected")
                runCatching { camera.close() }
                latch.countDown()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                openError = IllegalStateException("camera open error=$error")
                runCatching { camera.close() }
                latch.countDown()
            }
        }, callbackHandler)

        if (!latch.await(5, TimeUnit.SECONDS)) {
            stopCameraCapture()
            throw IllegalStateException("camera start timeout")
        }
        openError?.let {
            stopCameraCapture()
            throw it
        }
    }

    private fun stopCameraCapture() {
        runCatching { captureSession?.stopRepeating() }
        runCatching { captureSession?.close() }
        runCatching { cameraDevice?.close() }
        runCatching { imageReader?.close() }
        runCatching { handlerThread?.quitSafely() }
        captureSession = null
        cameraDevice = null
        imageReader = null
        handler = null
        handlerThread = null
        latestFrame.set(null)
    }

    private fun onImageAvailable(image: Image) {
        try {
            val frame = preprocessFrame(image)
            latestFrame.set(frame)
        } catch (t: Throwable) {
            Log.w(TAG, "frame preprocess failed", t)
        } finally {
            image.close()
        }
    }

    private fun preprocessFrame(image: Image): FrameData {
        val plane = image.planes.first()
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        val src = ByteArray(buffer.remaining())
        buffer.get(src)

        val targetW = max(1, width / DOWNSAMPLE_FACTOR)
        val targetH = max(1, height / DOWNSAMPLE_FACTOR)
        val downsampled = IntArray(targetW * targetH)

        for (ty in 0 until targetH) {
            val srcY0 = ty * DOWNSAMPLE_FACTOR
            for (tx in 0 until targetW) {
                val srcX0 = tx * DOWNSAMPLE_FACTOR
                var sum = 0
                var count = 0
                for (dy in 0 until DOWNSAMPLE_FACTOR) {
                    val sy = srcY0 + dy
                    if (sy >= height) continue
                    val rowStart = sy * rowStride
                    for (dx in 0 until DOWNSAMPLE_FACTOR) {
                        val sx = srcX0 + dx
                        if (sx >= width) continue
                        val idx = rowStart + sx
                        if (idx >= src.size) continue
                        sum += src[idx].toInt() and 0xFF
                        count += 1
                    }
                }
                downsampled[ty * targetW + tx] = if (count > 0) sum / count else 0
            }
        }

        val blurred = boxBlur(downsampled, targetW, targetH)
        return FrameData(gray = blurred, width = targetW, height = targetH, timestampMs = System.currentTimeMillis())
    }

    private fun detectMovement(prev: FrameData, current: FrameData): Boolean {
        if (prev.width != current.width || prev.height != current.height) return false
        val size = current.gray.size
        if (size == 0) return false
        val threshold = diffThreshold.coerceIn(1, 255)
        val ratioThreshold = minChangedRatio.coerceIn(0.001, 1.0)

        val binary = IntArray(size)
        for (i in 0 until size) {
            val diff = abs(current.gray[i] - prev.gray[i])
            binary[i] = if (diff >= threshold) 1 else 0
        }

        val opened = dilate(erode(binary, current.width, current.height), current.width, current.height)
        val cleaned = erode(dilate(opened, current.width, current.height), current.width, current.height)

        val changedPixels = cleaned.sum()
        val changedRatio = changedPixels.toDouble() / size.toDouble()

        return changedPixels >= MIN_CHANGED_PIXELS && changedRatio >= ratioThreshold
    }

    private fun boxBlur(src: IntArray, width: Int, height: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny !in 0 until height) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx !in 0 until width) continue
                        sum += src[ny * width + nx]
                        count += 1
                    }
                }
                out[y * width + x] = if (count > 0) sum / count else src[y * width + x]
            }
        }
        return out
    }

    private fun erode(src: IntArray, width: Int, height: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var allOne = true
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny !in 0 until height) {
                        allOne = false
                        break
                    }
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx !in 0 until width || src[ny * width + nx] == 0) {
                            allOne = false
                            break
                        }
                    }
                    if (!allOne) break
                }
                out[y * width + x] = if (allOne) 1 else 0
            }
        }
        return out
    }

    private fun dilate(src: IntArray, width: Int, height: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var anyOne = false
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny !in 0 until height) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx !in 0 until width) continue
                        if (src[ny * width + nx] == 1) {
                            anyOne = true
                            break
                        }
                    }
                    if (anyOne) break
                }
                out[y * width + x] = if (anyOne) 1 else 0
            }
        }
        return out
    }

    private fun selectRearCameraId(manager: CameraManager): String? {
        return manager.cameraIdList.firstOrNull { id ->
            val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull()
    }

    private data class FrameData(
        val gray: IntArray,
        val width: Int,
        val height: Int,
        val timestampMs: Long
    )

    private companion object {
        const val TAG = "CameraMonitor"
        const val CAPTURE_WIDTH = 640
        const val CAPTURE_HEIGHT = 480
        const val DOWNSAMPLE_FACTOR = 4
        const val DEFAULT_DIFF_THRESHOLD = 20
        const val MIN_CHANGED_PIXELS = 120
        const val DEFAULT_MIN_CHANGED_RATIO = 0.03
    }
}
