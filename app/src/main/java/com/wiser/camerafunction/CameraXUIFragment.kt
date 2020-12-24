package com.wiser.camerafunction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment

class CameraXUIFragment: Fragment(){

    private var cameraCallBack: CameraCallBack? = null

    private var ibSwitchCameraFace: ImageButton? = null

    private var btnSwitchCameraMode: Button? = null

    private var tvTakeVideo: TextView? = null

    private var isRecordVideo = false

    private var isStartRecording = false

    companion object {
        fun createCameraXUIFragment(cameraCallBack: CameraCallBack): CameraXUIFragment {
            val cameraXUIFragment = CameraXUIFragment()
            cameraXUIFragment.setCameraCallBack(cameraCallBack)
            return cameraXUIFragment
        }
    }

    fun setCameraCallBack(cameraCallBack: CameraCallBack) {
        this.cameraCallBack = cameraCallBack
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera_ui,container,false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ibSwitchCameraFace = view.findViewById(R.id.ib_switch_camera_face)

        btnSwitchCameraMode = view.findViewById(R.id.btn_switch_camera_mode)

        tvTakeVideo = view.findViewById(R.id.tv_take_video)

        view.findViewById<ImageButton>(R.id.ib_switch_camera_face).setOnClickListener{
            cameraCallBack?.switchCameraFace()
        }

        view.findViewById<ImageButton>(R.id.ib_take_camera).setOnClickListener{
            if (isRecordVideo) {
                if (isStartRecording) {
                    cameraCallBack?.stopRecordVideo()
                } else {
                    cameraCallBack?.startRecordVideo()
                }
            } else {
                cameraCallBack?.takeCamera()
            }
        }

        view.findViewById<Button>(R.id.btn_switch_camera_mode).setOnClickListener{
            cameraCallBack?.switchCameraMode()
            if (isRecordVideo) {
                isRecordVideo = false
                btnSwitchCameraMode?.text = "录制"
            } else {
                isRecordVideo = true
                btnSwitchCameraMode?.text = "拍照"
            }
        }
    }

    fun startRecord() {
        isStartRecording = true
        tvTakeVideo?.visibility = View.VISIBLE
        btnSwitchCameraMode?.visibility = View.GONE
        ibSwitchCameraFace?.visibility = View.GONE
    }

    fun stopRecord() {
        isStartRecording = false
        tvTakeVideo?.visibility = View.GONE
        btnSwitchCameraMode?.visibility = View.VISIBLE
        ibSwitchCameraFace?.visibility = View.VISIBLE
    }

}

interface CameraCallBack {
    fun switchCameraFace()
    fun switchCameraMode()
    fun takeCamera()
    fun startRecordVideo()
    fun stopRecordVideo()
}