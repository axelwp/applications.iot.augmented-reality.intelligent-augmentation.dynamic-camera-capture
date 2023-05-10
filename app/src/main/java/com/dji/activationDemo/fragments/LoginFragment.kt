package com.dji.activationDemo.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.dji.activationDemo.DemoApplication
import com.dji.activationDemo.MainActivity
import com.dji.activationDemo.R
import com.dji.activationDemo.databinding.FragmentLoginBinding
import dji.sdk.sdkmanager.BluetoothProductConnector.BluetoothDevicesListCallback
import dji.sdk.sdkmanager.DJISDKManager

class LoginFragment : Fragment() {

    /** Fetch fragment binding */
    private var _fragmentLoginBinding: FragmentLoginBinding? = null
    private val fragmentLoginBinding get() = _fragmentLoginBinding!!
/*
    private val bluetoothProductCallback =
        BluetoothDevicesListCallback { list ->
            Log.d("LoginFragment", "!!!TEST!!!")
            val product = list[0].name
            Log.d("LoginFragment", "onUpdate: $product")
            if (list.isNotEmpty()) {
                DJISDKManager.getInstance().bluetoothProductConnector.connect(list[0], null)
                DJISDKManager.getInstance().startConnectionToProduct()
            }
        }
*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //DJISDKManager.getInstance().bluetoothProductConnector.setBluetoothDevicesListCallback(bluetoothProductCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _fragmentLoginBinding = FragmentLoginBinding.inflate(inflater, container, false)
        return fragmentLoginBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentLoginBinding.loginButton.setOnClickListener {
            val username = fragmentLoginBinding.username.text.toString()
            val password = fragmentLoginBinding.password.text.toString()
            MainActivity.userLogin(username, password, this)
            fragmentLoginBinding.progressBar.isVisible = true
        }
    }

    fun loginSuccess() {
        fragmentLoginBinding.progressBar.isVisible = false
        navigateToHome()
    }

    fun loginFailure() {
        fragmentLoginBinding.progressBar.isVisible = false
        Toast.makeText(context, "Login Failure", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToHome() {
        lifecycleScope.launchWhenStarted {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                LoginFragmentDirections.actionLoginFragmentToHomeFragment()
            )
        }
    }
}