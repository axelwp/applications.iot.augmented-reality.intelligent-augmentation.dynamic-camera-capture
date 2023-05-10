package com.dji.activationDemo.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.dji.activationDemo.DemoApplication
import com.dji.activationDemo.MainActivity
import com.dji.activationDemo.R
import com.dji.activationDemo.databinding.FragmentSettingsBinding
import dji.sdk.sdkmanager.DJISDKManager

class SettingsFragment : Fragment() {
    //Fetch fragment binding
    private var _fragmentSettingsBinding: FragmentSettingsBinding? = null
    private val fragmentSettingsBinding get() = _fragmentSettingsBinding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register the broadcast receiver for receiving the device connection's changes.
        val filter = IntentFilter()
        filter.addAction(DemoApplication.FLAG_CONNECTION_CHANGE)
        activity?.registerReceiver(mReceiver, filter)

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _fragmentSettingsBinding = FragmentSettingsBinding.inflate(inflater, container, false)
        return fragmentSettingsBinding.root
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        /* Set input field values from saved values */
        var ipsText = fragmentSettingsBinding.imagesPerScanSetting
        var spsText = fragmentSettingsBinding.shotsPerSecSetting
        var haText = fragmentSettingsBinding.horizontalAngleSetting
        var vaText = fragmentSettingsBinding.verticalAngleSetting
        var rows = fragmentSettingsBinding.rowsCount
        var columns = fragmentSettingsBinding.columnsCount
        var buff = fragmentSettingsBinding.pauseBuffer
        var iHorSwitch = fragmentSettingsBinding.horizontalInversion
        var iVerSwitch = fragmentSettingsBinding.verticalInversion
        ipsText.text.append(MainActivity.getImgNum().toString())
        spsText.text.append(MainActivity.getIps().toString())
        haText.text.append(MainActivity.getHorAngle().toString())
        vaText.text.append(MainActivity.getVerAngle().toString())
        rows.number = MainActivity.getRows().toString()
        columns.number = MainActivity.getColumns().toString()
        buff.text.append(MainActivity.getBuff().toString())
        iHorSwitch.isChecked = MainActivity.getHorInvert()
        iVerSwitch.isChecked = MainActivity.getVerInvert()

        // Set the range of the row and column buttons
        rows.setRange(1, 999)
        columns.setRange(1, 999)
        /* see if we have a gimbal connected and update UI accordingly */
        updateGimbalCheck()

        fragmentSettingsBinding.btnBack.setOnClickListener {
            /* Fetch the values in settings */
            val ips : Int = ipsText.text.toString().toInt()
            val sps : Float = spsText.text.toString().toFloat()
            val ha : Float = haText.text.toString().toFloat()
            val va : Float = vaText.text.toString().toFloat()
            val row: Int = rows.number.toInt()
            val col: Int = columns.number.toInt()
            val buffer: Float = buff.text.toString().toFloat()
            val iHor: Boolean = iHorSwitch.isChecked
            val iVer: Boolean = iVerSwitch.isChecked
            MainActivity.setAnimation(ips, sps, ha, va, row, col, buffer, iHor, iVer) // Send the animation settings to MainActivity, which will be used in gimbal animation
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp()
        }
        fragmentSettingsBinding.gimbalSearch.setOnClickListener {
            DJISDKManager.getInstance().bluetoothProductConnector.searchBluetoothProducts(null)
            fragmentSettingsBinding.gimbalSearch.isVisible = false
        }

    }
    private fun updateGimbalCheck() {
        fragmentSettingsBinding.gimbalSearch.isVisible = true
        if (DJISDKManager.getInstance().product != null) {
            fragmentSettingsBinding.gimbalCheck.isEnabled = true
            fragmentSettingsBinding.gimbalText.text = "DJI gimbal detected! "
        }
        else {
            fragmentSettingsBinding.gimbalCheck.isEnabled = false
            fragmentSettingsBinding.gimbalText.text = "No gimbal detected "
        }
    }


    protected var mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("BroadcastReceive", intent.action!!)
            updateGimbalCheck()
        }
    }
}