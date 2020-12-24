package com.wiser.camerafunction

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class HelperUtils {

    companion object {
        /**
         * @param activity
         *          父Activity
         * @param tagFragment
         *          tagFragment
         * @return 返回值
         */
        fun replaceFragment(activity: AppCompatActivity?, id: Int, tagFragment: Fragment?) {
            activity?.let { act ->
                tagFragment?.let { fragment ->
                    act.supportFragmentManager.beginTransaction()
                        .replace(id, fragment, fragment::class.java.simpleName)
                        .commitAllowingStateLoss()
                }
            }
        }
    }

}