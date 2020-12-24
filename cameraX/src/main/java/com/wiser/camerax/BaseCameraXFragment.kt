package com.wiser.camerax

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.fragment.app.Fragment
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/**
 * @author Wiser
 *
 *      cameraX封装基类
 */
abstract class BaseCameraXFragment : Fragment() {

    var cameraProvider: ProcessCameraProvider? = null

    /**
     * 前后摄像头
     */
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK

    /**
     * 闪光灯默认关闭
     */
    var flashMode: Int = ImageCapture.FLASH_MODE_OFF

    /**
     * 是否录制视频
     */
    var isRecordVideo = false

    var preview: Preview? = null

    var imageCapture: ImageCapture? = null

    var videoCapture: VideoCapture? = null

    var imageAnalyzer: ImageAnalysis? = null

    var camera: Camera? = null

    var cameraExecutor: ExecutorService? = null

    private var picOutputDirectory: File? = null

    private var picFile: File? = null

    private var videoOutputDirectory: File? = null

    private var videoFile: File? = null

    var displayId: Int = -1

    private var onCameraStatusChangeListener: OnCameraStatusChangeListener? = null

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    }

    abstract fun container(): View?

    abstract fun previewView(): PreviewView?

    companion object {
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        private const val TAG = "BaseCameraXFragment"
        private const val ANIMATION_FAST_MILLIS = 50L
        private const val ANIMATION_SLOW_MILLIS = 100L
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val VIDEO_EXTENSION = ".mp4"
        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(
                baseFolder, SimpleDateFormat(format, Locale.CHINA)
                    .format(System.currentTimeMillis()) + extension
            )

        fun getPicOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, "camera_photo").apply { mkdirs() }
            }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else appContext.filesDir
        }

        fun getVideoOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, "camera_video").apply { mkdirs() }
            }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else appContext.filesDir
        }
    }

    fun setOnCameraStatusChangeListenerBase(onCameraStatusChangeListener: OnCameraStatusChangeListener?) {
        this.onCameraStatusChangeListener = onCameraStatusChangeListener
    }

    fun getOnCameraStatusChangeListener(): OnCameraStatusChangeListener? = onCameraStatusChangeListener

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        picOutputDirectory = context?.let { getPicOutputDirectory(it) }
        videoOutputDirectory = context?.let { getVideoOutputDirectory(it) }

        displayManager?.registerDisplayListener(displayListener, null)
    }

    override fun onResume() {
        super.onResume()
        if (cameraProvider != null && isRecordVideo) {
            setUpCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRecordVideo) {
            stopRecordVideo()
        }
    }

    fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(Runnable {

            if (cameraProvider == null)
                cameraProvider = cameraProviderFuture.get()

            if (!hasBackCamera()) {
                Toast.makeText(context, "后置摄像头不可用", Toast.LENGTH_LONG).show()
                return@Runnable
            }
            if (!hasFrontCamera()) {
                Toast.makeText(context, "前置摄像头不可用", Toast.LENGTH_LONG).show()
                return@Runnable
            }
            setCameraPreview()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    fun setFlashMode(mode: Int? = flashMode) {
        if (mode != null) {
            imageCapture?.flashMode = mode
        }
    }

    /**
     * 初始化相机视图
     */
    fun setCameraPreview() {
        if (previewView() == null) return
        val metrics = DisplayMetrics().also { previewView()?.display?.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = previewView()?.display?.rotation

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        if (preview == null)
            preview = rotation?.let {
                Preview.Builder()
                    // We request aspect ratio but no resolution
                    .setTargetAspectRatio(screenAspectRatio)
                    // Set initial target rotation
                    .setTargetRotation(it)
                    .build()
            }

        // ImageCapture
        if (imageCapture == null)
            imageCapture = rotation?.let {
                ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    // We request aspect ratio but no resolution to match preview config, but letting
                    // CameraX optimize for whatever specific resolution best fits our use cases
                    .setTargetAspectRatio(screenAspectRatio)
                    // Set initial target rotation, we will have to call this again if rotation changes
                    // during the lifecycle of this use case
                    .setTargetRotation(it)
                    .build()
            }

        // VideoCapture
        @SuppressLint("RestrictedApi")
        if (videoCapture == null)
            videoCapture = rotation?.let {
                VideoCapture.Builder()
                    .setTargetAspectRatio(screenAspectRatio)
                    .setTargetRotation(it)
                    .build()
            }

        // ImageAnalysis
        if (imageAnalyzer == null)
            imageAnalyzer = rotation?.let {
                ImageAnalysis.Builder()
                    // We request aspect ratio but no resolution
                    .setTargetAspectRatio(screenAspectRatio)
                    // Set initial target rotation, we will have to call this again if rotation changes
                    // during the lifecycle of this use case
                    .setTargetRotation(it)
                    .build()
                    // The analyzer can then be assigned to the instance
                    .also {
                        cameraExecutor?.let { it1 ->
                            it.setAnalyzer(it1, LuminosityAnalyzer { luma ->
                                // Values returned from our analyzer are passed to the attached listener
                                // We log image analysis results here - you should do something useful
                                // instead!
                                Log.d(TAG, "Average luminosity: $luma")
                            })
                        }
                    }
            }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = if (isRecordVideo) {
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videoCapture
                )
            } else {
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            }

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(previewView()?.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    /**
     * 拍照
     */
    fun takeCapture(
        photoFile: File? = picOutputDirectory?.let { createFile(it, FILENAME, PHOTO_EXTENSION) },
        takeCaptureCallBack: CameraTakeCaptureCallBack? = null
    ) {
        imageCapture?.let { imageCapture ->

            picOutputDirectory = photoFile?.parentFile

            picFile = photoFile

            // Setup image capture metadata
            val metadata = ImageCapture.Metadata().apply {

                // Mirror image when using the front camera
                isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
            }

            // Create output options object which contains file + metadata
            val outputOptions = photoFile?.let {
                ImageCapture.OutputFileOptions.Builder(it)
                    .setMetadata(metadata)
                    .build()
            }

            // Setup image capture listener which is triggered after photo has been taken
            cameraExecutor?.let {
                if (outputOptions != null) {
                    imageCapture.takePicture(
                        outputOptions, it, object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exc: ImageCaptureException) {
                                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                            }

                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                                Log.d(TAG, "Photo capture succeeded: $savedUri")

                                // We can only change the foreground Drawable using API level 23+ API
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    // Update the gallery thumbnail with latest picture taken
                                }

                                // Implicit broadcasts will be ignored for devices running API level >= 24
                                // so if you only target API level 24+ you can remove this statement
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                                    requireActivity().sendBroadcast(
                                        Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
                                    )
                                }

                                // If the folder selected is an external media directory, this is
                                // unnecessary but otherwise other apps will not be able to access our
                                // images unless we scan them using [MediaScannerConnection]
                                val mimeType = MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension(savedUri.toFile().extension)
                                MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(savedUri.toFile().absolutePath),
                                    arrayOf(mimeType)
                                ) { _, uri ->
                                    Log.d(TAG, "Image capture scanned into media store: $uri")
                                }

                                takeCaptureCallBack?.takeCaptureCallBack(photoFile, savedUri)
                            }
                        })
                }
            }

            // We can only change the foreground Drawable using API level 23+ API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                // Display flash animation to indicate that photo was captured
                container()?.postDelayed({
                    container()?.foreground = ColorDrawable(Color.WHITE)
                    container()?.postDelayed(
                        { container()?.foreground = null }, ANIMATION_FAST_MILLIS
                    )
                }, ANIMATION_SLOW_MILLIS)
            }
        }
    }

    /**
     * 录制视频
     */
    @SuppressLint("RestrictedApi")
    fun startRecordVideo(
        videoFile: File? = videoOutputDirectory?.let { createFile(it, FILENAME, VIDEO_EXTENSION) },
        takeVideoCallBack: CameraTakeVideoCallBack? = null
    ) {

        onCameraStatusChangeListener?.onStartRecord()

        videoOutputDirectory = videoFile?.parentFile

        this.videoFile = videoFile

        val options: VideoCapture.OutputFileOptions? = videoFile?.let {
            VideoCapture.OutputFileOptions.Builder(
                it
            ).build()
        }
        //开始录像
        cameraExecutor?.let { it ->
            if (options != null) {
                videoCapture?.startRecording(
                    options,
                    it,
                    object : VideoCapture.OnVideoSavedCallback {

                        override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                            //保存视频成功回调，会在停止录制时被调用
                            val savedUri = outputFileResults.savedUri ?: Uri.fromFile(videoFile)
                            Log.d(TAG, "Video capture succeeded: $savedUri")

                            container()?.post(Runnable {
                                onCameraStatusChangeListener?.onStopRecord()
                                takeVideoCallBack?.takeVideoCallBack(videoFile, savedUri)
                            })

                            // We can only change the foreground Drawable using API level 23+ API
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                // Update the gallery thumbnail with latest picture taken
                            }

                            // Implicit broadcasts will be ignored for devices running API level >= 24
                            // so if you only target API level 24+ you can remove this statement
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                                requireActivity().sendBroadcast(
                                    Intent(android.hardware.Camera.ACTION_NEW_VIDEO, savedUri)
                                )
                            }

                            // If the folder selected is an external media directory, this is
                            // unnecessary but otherwise other apps will not be able to access our
                            // images unless we scan them using [MediaScannerConnection]
                            val mimeType = MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(savedUri.toFile().extension)
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(savedUri.toFile().absolutePath),
                                arrayOf(mimeType)
                            ) { _, uri ->
                                Log.d(TAG, "Image capture scanned into media store: $uri")
                            }
                        }

                        override fun onError(
                            videoCaptureError: Int,
                            message: String,
                            cause: Throwable?
                        ) {
                            //保存失败的回调，可能在开始或结束录制时被调用
                            Log.e("", "onError: $message")
                        }
                    })
            }
        }
    }

    @SuppressLint("RestrictedApi")
    fun stopRecordVideo() {
        videoCapture?.stopRecording()
    }

    /**
     * 屏幕比例
     */
    fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    // 后置
    fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    // 前置
    fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    fun setPicDirectory(outputDirectory: File) {
        this.picOutputDirectory = outputDirectory
    }

    fun setVideoDirectory(outputDirectory: File) {
        this.videoOutputDirectory = outputDirectory
    }

    /**
     * 获取图片存储路径
     */
    fun getPicDirectoryFile(): File? = picOutputDirectory

    /**
     * 获取视频存储路径
     */
    fun getVideoDirectoryFile(): File? = videoOutputDirectory

    /**
     * 获取图片存储列表
     */
    fun getPicFileList(): Array<String>? = picOutputDirectory?.list()

    /**
     * 获取视频存储列表
     */
    fun getVideoFileList(): Array<String>? = videoOutputDirectory?.list()

    /**
     * 获取当前拍照图片文件
     */
    fun getPicFile(): File? = picFile

    /**
     * 获取当前录制的文件
     */
    fun getVideoFile(): File? = videoFile

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        @SuppressLint("RestrictedApi")
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@BaseCameraXFragment.displayId) {
                Log.d(TAG, "Rotation changed: ${view.display.rotation}")
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
                videoCapture?.setTargetRotation(view.display.rotation)
            }
        } ?: Unit
    }

    @SuppressLint("RestrictedApi")
    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor?.shutdown()

        displayManager?.unregisterDisplayListener(displayListener)

        cameraProvider?.unbindAll()
        cameraProvider = null

        picOutputDirectory = null
        picFile = null
        videoOutputDirectory = null
        videoFile = null

        camera = null
        preview?.onDetached()
        preview = null
    }

    /**
     * Our custom image analysis class.
     *
     * <p>All we need to do is override the function `analyze` with our desired operations. Here,
     * we compute the average luminosity of the image by looking at the Y plane of the YUV frame.
     */
    class LuminosityAnalyzer(listener: LumaListener? = null) : ImageAnalysis.Analyzer {
        private val frameRateWindow = 8
        private val frameTimestamps = ArrayDeque<Long>(5)
        private val listeners = ArrayList<LumaListener>().apply { listener?.let { add(it) } }
        private var lastAnalyzedTimestamp = 0L
        var framesPerSecond: Double = -1.0
            private set

        /**
         * Used to add listeners that will be called with each luma computed
         */
        fun onFrameAnalyzed(listener: LumaListener) = listeners.add(listener)

        /**
         * Helper extension function used to extract a byte array from an image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        /**
         * Analyzes an image to produce a result.
         *
         * <p>The caller is responsible for ensuring this analysis method can be executed quickly
         * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
         * images will not be acquired and analyzed.
         *
         * <p>The image passed to this method becomes invalid after this method returns. The caller
         * should not store external references to this image, as these references will become
         * invalid.
         *
         * @param image image being analyzed VERY IMPORTANT: Analyzer method implementation must
         * call image.close() on received images when finished using them. Otherwise, new images
         * may not be received or the camera may stall, depending on back pressure setting.
         *
         */
        override fun analyze(image: ImageProxy) {
            // If there are no listeners attached, we don't need to perform analysis
            if (listeners.isEmpty()) {
                image.close()
                return
            }

            // Keep track of frames analyzed
            val currentTime = System.currentTimeMillis()
            frameTimestamps.push(currentTime)

            // Compute the FPS using a moving average
            while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
            val timestampFirst = frameTimestamps.peekFirst() ?: currentTime
            val timestampLast = frameTimestamps.peekLast() ?: currentTime
            framesPerSecond = 1.0 / ((timestampFirst - timestampLast) /
                    frameTimestamps.size.coerceAtLeast(1).toDouble()) * 1000.0

            // Analysis could take an arbitrarily long amount of time
            // Since we are running in a different thread, it won't stall other use cases

            lastAnalyzedTimestamp = frameTimestamps.first

            // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance plane
            val buffer = image.planes[0].buffer

            // Extract image data from callback object
            val data = buffer.toByteArray()

            // Convert the data into an array of pixel values ranging 0-255
            val pixels = data.map { it.toInt() and 0xFF }

            // Compute average luminance for the image
            val luma = pixels.average()

            // Call all listeners with new value
            listeners.forEach { it(luma) }

            image.close()
        }
    }
}

/** Helper type alias used for analysis use case callbacks */
typealias LumaListener = (luma: Double) -> Unit

interface CameraTakeCaptureCallBack {
    fun takeCaptureCallBack(picFile: File?, picUri: Uri?)
}

interface CameraTakeVideoCallBack {
    fun takeVideoCallBack(videoFile: File?, videoUri: Uri?)
}

interface OnCameraStatusChangeListener{
    fun onStartRecord()
    fun onStopRecord()
    fun onToggleCameraFace(lensFacing: Int)
}