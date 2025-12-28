package com.facelockapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.facelockapp.navigation.AppNavGraph
import com.facelockapp.navigation.Screen
import com.facelockapp.ui.screens.PermissionsScreen
import com.facelockapp.ui.theme.FaceLockAppTheme
import com.facelockapp.ui.viewmodel.InitialState
import com.facelockapp.ui.viewmodel.LockViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: LockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FaceLockAppTheme {
                PermissionsScreen(onPermissionsGranted = {
                    setContent {
                        FaceLockAppTheme {
                            val initialState by viewModel.initialState.collectAsState()
                            when (val state = initialState) {
                                is InitialState.Loading -> {
                                    // You can show a loading indicator here as well
                                }
                                is InitialState.Ready -> {
                                    AppNavGraph(
                                        viewModel = viewModel,
                                        startDestination = state.startDestination
                                    )
                                }
                            }
                        }
                    }
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("facelockapp_prefs", MODE_PRIVATE)
        prefs.edit().putLong("last_main_activity_time", System.currentTimeMillis()).apply()
    }
}
