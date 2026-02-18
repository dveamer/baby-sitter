package com.dveamer.babysitter.web

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RearCameraMjpegSource(
    context: Context
) {
    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val lock = Any()
    private val clientCount = AtomicInteger(0)
    private val latestFrame = AtomicReference<ByteArray?>(null)

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var imageReader: ImageReader? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var isCameraStarted = false

    fun stream(
        socket: Socket,
        isCameraEnabled: () -> Boolean
    ) {
        addClientOrThrow()
        try {
            val output = socket.getOutputStream()
            val headers = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: multipart/x-mixed-replace; boundary=$BOUNDARY\r\n")
                append("Cache-Control: no-cache\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(headers.toByteArray(Charsets.UTF_8))
            output.flush()

            while (!socket.isClosed) {
                if (!isCameraEnabled()) {
                    break
                }
                val frame = latestFrame.get()
                if (frame == null) {
                    Thread.sleep(100L)
                    continue
                }
                val partHeader = buildString {
                    append("--$BOUNDARY\r\n")
                    append("Content-Type: image/jpeg\r\n")
                    append("Content-Length: ${frame.size}\r\n")
                    append("\r\n")
                }
                output.write(partHeader.toByteArray(Charsets.UTF_8))
                output.write(frame)
                output.write("\r\n".toByteArray(Charsets.UTF_8))
                output.flush()
                Thread.sleep(FRAME_INTERVAL_MS)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "camera stream client disconnected or failed", e)
        } finally {
            removeClient()
        }
    }

    fun stopAll() {
        synchronized(lock) {
            clientCount.set(0)
            stopCameraLocked()
        }
    }

    private fun addClientOrThrow() {
        val next = clientCount.incrementAndGet()
        try {
            synchronized(lock) {
                if (!isCameraStarted) {
                    startCameraLocked()
                }
            }
            Log.d(TAG, "camera stream clients=$next")
        } catch (e: Throwable) {
            clientCount.decrementAndGet()
            throw e
        }
    }

    private fun removeClient() {
        val left = clientCount.decrementAndGet().coerceAtLeast(0)
        if (left == 0) {
            synchronized(lock) {
                stopCameraLocked()
            }
        }
        Log.d(TAG, "camera stream clients=$left")
    }

    @Suppress("MissingPermission")
    private fun startCameraLocked() {
        if (isCameraStarted) return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("camera permission not granted")
        }

        val cameraId = selectRearCameraId() ?: throw IllegalStateException("rear camera not found")
        val thread = HandlerThread("RearCameraMjpeg").apply { start() }
        val callbackHandler = Handler(thread.looper)
        handlerThread = thread
        handler = callbackHandler

        val reader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ ir ->
                val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes.firstOrNull()?.buffer ?: return@setOnImageAvailableListener
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    latestFrame.set(bytes)
                } finally {
                    image.close()
                }
            }, callbackHandler)
        }
        imageReader = reader

        val latch = CountDownLatch(1)
        var startError: Throwable? = null

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
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
                    startError = e
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
                            }.onSuccess {
                                isCameraStarted = true
                            }.onFailure { e ->
                                startError = e
                            }
                            latch.countDown()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            startError = IllegalStateException("camera session configure failed")
                            latch.countDown()
                        }
                    },
                    callbackHandler
                )
            }

            override fun onDisconnected(camera: CameraDevice) {
                startError = IllegalStateException("camera disconnected")
                runCatching { camera.close() }
                latch.countDown()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                startError = IllegalStateException("camera open error=$error")
                runCatching { camera.close() }
                latch.countDown()
            }
        }, callbackHandler)

        if (!latch.await(5, TimeUnit.SECONDS)) {
            stopCameraLocked()
            throw IllegalStateException("camera start timeout")
        }
        startError?.let {
            stopCameraLocked()
            throw it
        }
    }

    private fun stopCameraLocked() {
        runCatching { captureSession?.stopRepeating() }
        runCatching { captureSession?.close() }
        runCatching { cameraDevice?.close() }
        runCatching { imageReader?.close() }
        runCatching { handlerThread?.quitSafely() }
        runCatching { handlerThread?.join(500L) }
        captureSession = null
        cameraDevice = null
        imageReader = null
        handler = null
        handlerThread = null
        latestFrame.set(null)
        isCameraStarted = false
    }

    private fun selectRearCameraId(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val facing = cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.firstOrNull()
    }

    companion object {
        private const val TAG = "RearCameraMjpegSource"
        private const val WIDTH = 640
        private const val HEIGHT = 480
        private const val FRAME_INTERVAL_MS = 120L
        private const val BOUNDARY = "frame"
    }
}
