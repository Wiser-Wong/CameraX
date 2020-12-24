package com.wiser.camerafunction

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.core.content.ContextCompat
import com.wiser.camerax.CameraTakeCaptureCallBack
import com.wiser.camerax.CameraXFragment
import com.wiser.camerax.OnCameraStatusChangeListener
import java.io.File

class CameraXActivity: AppCompatActivity(), CameraCallBack, OnCameraStatusChangeListener {

    private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_EXTERNAL_STORAGE)

    private var cameraXFragment: CameraXFragment? = null

    private var cameraXUIFragment: CameraXUIFragment? = null

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 10
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        if (!hasPermissions(this)) {
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        } else {
            cameraXFragment = CameraXFragment.createCameraXFragment()
            cameraXFragment?.initCameraFaceDirection(CameraSelector.LENS_FACING_BACK)
            cameraXFragment?.setOnCameraStatusChangeListener(this)
            HelperUtils.replaceFragment(this, R.id.fl_camera_controller,
                cameraXFragment
            )
            cameraXUIFragment = CameraXUIFragment.createCameraXUIFragment(this)
            HelperUtils.replaceFragment(this,R.id.fl_camera_ui_controller,cameraXUIFragment)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (PackageManager.PERMISSION_GRANTED == grantResults.firstOrNull()) {
                cameraXFragment = CameraXFragment.createCameraXFragment()
                cameraXFragment?.initCameraFaceDirection(CameraSelector.LENS_FACING_BACK)
                cameraXFragment?.setOnCameraStatusChangeListener(this)
                HelperUtils.replaceFragment(this, R.id.fl_camera_controller,
                    cameraXFragment
                )
                cameraXUIFragment = CameraXUIFragment.createCameraXUIFragment(this)
                HelperUtils.replaceFragment(this,R.id.fl_camera_ui_controller,cameraXUIFragment)
            } else {
                Toast.makeText(this, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
        window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    override fun switchCameraFace() {
        cameraXFragment?.switchCameraFace()
    }

    override fun switchCameraMode() {
        cameraXFragment?.switchCameraMode()
    }

    override fun switchCameraFlash() {
        cameraXFragment?.switchCameraFlash()
    }

    override fun takeCamera() {
//        val mediaDir = this.externalMediaDirs.firstOrNull()?.let {
//            File(it, "cameraX").apply { mkdirs() }
//        }
//        val file = File(
//            if (mediaDir != null && mediaDir.exists()) mediaDir else this.filesDir, SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.CHINA)
//                .format(System.currentTimeMillis()) + ".jpg"
//        )
        cameraXFragment?.takeCapture(takeCaptureCallBack = object : CameraTakeCaptureCallBack {
            override fun takeCaptureCallBack(picFile: File?, picUri: Uri?) {
//                findViewById<AppCompatImageView>(R.id.iv_preview).post(Runnable {
//                    findViewById<AppCompatImageView>(R.id.iv_preview).setImageBitmap(BitmapFactory.decodeFile(PhotoBitmapUtils.amendRotatePhoto(picFile?.absolutePath,2,this@CameraXActivity)))
//                })
            }
        })
    }

    override fun startRecordVideo() {
        cameraXFragment?.startRecordVideo()
    }

    override fun stopRecordVideo() {
        cameraXFragment?.stopRecordVideo()
    }

    override fun onStartRecord() {
        cameraXUIFragment?.startRecord()
    }

    override fun onStopRecord() {
        cameraXUIFragment?.stopRecord()
    }

    override fun onToggleCameraFace(lensFacing: Int) {
    }

}