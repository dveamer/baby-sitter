package com.dveamer.babysitter.collect

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
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import com.dveamer.babysitter.monitor.CameraFrameBus
import com.dveamer.babysitter.monitor.CameraFrameSnapshot
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CollectCameraSource(
    context: Context,
    private val paths: CollectStoragePaths,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val lock = Any()

    private var handlerThread: HandlerThread? = null
    private var jpegImageReader: ImageReader? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recorderSurface: Surface? = null
    private var currentOutputFile: File? = null
    private var currentOutputStartMs: Long? = null
    private var rotateJob: Job? = null

    fun start() {
        synchronized(lock) {
            if (captureSession != null) return
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "camera permission denied")
                return
            }
            paths.ensureDirectories()
            runCatching { startCameraCapture() }
                .onFailure { Log.w(TAG, "collect camera start failed", it) }
            if (rotateJob == null) {
                rotateJob = scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        delay(msUntilNextMinute())
                        synchronized(lock) {
                            if (captureSession != null) {
                                runCatching {
                                    stopCameraCapture()
                                    startCameraCapture()
                                }.onFailure { Log.w(TAG, "camera collect rotation failed", it) }
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            rotateJob?.cancel()
            rotateJob = null
            stopCameraCapture()
        }
    }

    @Suppress("MissingPermission")
    private fun startCameraCapture() {
        val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = selectRearCameraId(manager) ?: throw IllegalStateException("rear camera not found")

        val thread = HandlerThread("CollectCameraSource").apply { start() }
        val callbackHandler = Handler(thread.looper)
        handlerThread = thread

        val jpegReader = ImageReader.newInstance(CAPTURE_WIDTH, CAPTURE_HEIGHT, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ ir ->
                val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
                onJpegImageAvailable(image)
            }, callbackHandler)
        }
        jpegImageReader = jpegReader

        val recorder = createRecorderForCurrentMinute()
        mediaRecorder = recorder
        val recordSurface = recorder.surface
        recorderSurface = recordSurface

        val latch = CountDownLatch(1)
        var openError: Throwable? = null

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                val requestBuilder = runCatching {
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(jpegReader.surface)
                        addTarget(recordSurface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }
                }.getOrElse { e ->
                    openError = e
                    latch.countDown()
                    return
                }

                camera.createCaptureSession(
                    listOf(jpegReader.surface, recordSurface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            runCatching {
                                session.setRepeatingRequest(requestBuilder.build(), null, callbackHandler)
                                recorder.start()
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
        runCatching { captureSession?.abortCaptures() }
        runCatching { captureSession?.close() }
        runCatching { cameraDevice?.close() }
        runCatching { jpegImageReader?.close() }
        mediaRecorder?.let {
            runCatching { it.stop() }
            runCatching { it.reset() }
            runCatching { it.release() }
        }
        runCatching { recorderSurface?.release() }
        currentOutputFile?.let { output ->
            val startMs = currentOutputStartMs ?: CollectFileNaming.minuteFloor(System.currentTimeMillis())
            if (output.exists() && output.length() > 0L) {
                Log.d(TAG, "closed collect video file=${output.name} size=${output.length()} startMs=$startMs")
                CollectClosedFileBus.publish(
                    CollectClosedFileMeta(
                        type = CollectFileType.VIDEO,
                        file = output,
                        startMs = startMs,
                        closedAtMs = System.currentTimeMillis()
                    )
                )
            } else {
                Log.w(TAG, "collect video file not published file=${output.name} exists=${output.exists()} size=${output.length()}")
            }
        }
        runCatching { handlerThread?.quitSafely() }
        captureSession = null
        cameraDevice = null
        jpegImageReader = null
        recorderSurface = null
        mediaRecorder = null
        currentOutputFile = null
        currentOutputStartMs = null
        handlerThread = null
        CameraFrameBus.clear()
    }

    private fun onJpegImageAvailable(image: Image) {
        try {
            val buffer = image.planes.firstOrNull()?.buffer ?: return
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            CameraFrameBus.publish(CameraFrameSnapshot(jpeg = bytes, capturedAtMs = System.currentTimeMillis()))
        } catch (t: Throwable) {
            Log.w(TAG, "jpeg frame read failed", t)
        } finally {
            image.close()
        }
    }

    private fun createRecorderForCurrentMinute(): MediaRecorder {
        val startMs = CollectFileNaming.minuteFloor(System.currentTimeMillis())
        val file = File(paths.collectDir, CollectFileNaming.collectVideoFileName(startMs))
        currentOutputFile = file
        currentOutputStartMs = startMs
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(3_000_000)
            setVideoFrameRate(20)
            setVideoSize(CAPTURE_WIDTH, CAPTURE_HEIGHT)
            setOrientationHint(90)
            setOutputFile(file.absolutePath)
            prepare()
        }
    }

    private fun selectRearCameraId(manager: CameraManager): String? {
        return manager.cameraIdList.firstOrNull { id ->
            val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull()
    }

    private fun msUntilNextMinute(nowMs: Long = System.currentTimeMillis()): Long {
        val next = CollectFileNaming.minuteFloor(nowMs) + 60_000L
        return (next - nowMs).coerceIn(500L, 60_000L)
    }

    private companion object {
        const val TAG = "CollectCameraSource"
        const val CAPTURE_WIDTH = 640
        const val CAPTURE_HEIGHT = 480
    }
}
