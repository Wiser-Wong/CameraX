package com.wiser.camerax

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView

/**
 * @author Wiser
 *
 *      无UI的CameraXFragment
 */
class CameraXFragment : BaseCameraXFragment() {

    private var previewView: PreviewView? = null

    companion object {
        fun createCameraXFragment(): CameraXFragment = CameraXFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera, container, false)

    override fun container(): View? = view?.findViewById(R.id.cl_preview)

    override fun previewView(): PreviewView? = previewView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewView = view.findViewById(R.id.preview)

        previewView?.post {

            displayId = previewView?.display?.displayId?:-1

            setUpCamera()
        }
    }

    fun setOnCameraStatusChangeListener(onCameraStatusChangeListener: OnCameraStatusChangeListener?) {
        setOnCameraStatusChangeListenerBase(onCameraStatusChangeListener)
    }

    /**
     * 获取相机预览帧
     */
    fun getPreviewBitmap(): Bitmap? = previewView?.bitmap

    /**
     * 获取前置后置摄像头方向参数
     * @see CameraSelector.LENS_FACING_BACK  后置
     * @see CameraSelector.LENS_FACING_FRONT 前置
     */
    fun getCameraFaceDirection(): Int = lensFacing

    fun initCameraFaceDirection(faceDirection: Int) {
        lensFacing = faceDirection
    }

    /**
     * 切换闪光灯
     */
    fun switchCameraFlash(mode: Int = -100) {
        flashMode = if (mode != -100) {
            mode
        } else {
            if (ImageCapture.FLASH_MODE_OFF == flashMode) {
                ImageCapture.FLASH_MODE_ON
            } else {
                ImageCapture.FLASH_MODE_OFF
            }
        }

        setFlashMode()
    }

    /**
     * 切换摄像头
     */
    fun switchCameraFace() {
        lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }

        getOnCameraStatusChangeListener()?.onToggleCameraFace(lensFacing)

        setCameraPreview()
    }

    /**
     * 切换拍照模式 录制和拍照
     */
    fun switchCameraMode() {
        isRecordVideo = !isRecordVideo
        setCameraPreview()
    }

}