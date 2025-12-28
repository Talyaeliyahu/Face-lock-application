package com.facelockapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.facelockapp.data.PreferenceManager
import com.facelockapp.navigation.Screen
import com.facelockapp.ui.components.FaceEmbedding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class InitialState {
    object Loading : InitialState()
    data class Ready(val startDestination: String) : InitialState()
}

// New state for Lock Screen to solve race condition
sealed class LockScreenState {
    object Loading : LockScreenState()
    data class Ready(
        val storedEmbedding: FaceEmbedding?,
        val hasPin: Boolean,
        val hasPattern: Boolean
    ) : LockScreenState()
}

class LockViewModel(application: Application) : AndroidViewModel(application) {
    private val preferenceManager = PreferenceManager(application)

    // State for the main app graph (Setup/Auth)
    private val _initialState = MutableStateFlow<InitialState>(InitialState.Loading)
    val initialState: StateFlow<InitialState> = _initialState.asStateFlow()

    // State for the lock screen activity
    private val _lockScreenState = MutableStateFlow<LockScreenState>(LockScreenState.Loading)
    val lockScreenState: StateFlow<LockScreenState> = _lockScreenState.asStateFlow()

    // Live states for settings/auth screens
    val isLockEnabled: StateFlow<Boolean> = preferenceManager.isLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val storedEmbedding: StateFlow<FaceEmbedding?> = preferenceManager.faceEmbedding
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasPin: StateFlow<Boolean> = preferenceManager.pinCode.map { !it.isNullOrEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasPattern: StateFlow<Boolean> = preferenceManager.patternLock.map { !it.isNullOrEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasFace: StateFlow<Boolean> = storedEmbedding.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            // Read all settings once to determine initial states
            val enabled = preferenceManager.isLockEnabled.first()
            val pin = preferenceManager.pinCode.first()
            val pattern = preferenceManager.patternLock.first()
            val embedding = preferenceManager.faceEmbedding.first()

            val pinExists = !pin.isNullOrEmpty()
            val patternExists = !pattern.isNullOrEmpty()
            val faceExists = embedding != null

            // Set state for the main activity's navigation
            val isConfigured = enabled && faceExists && (pinExists || patternExists)
            val startDestination = if (isConfigured) Screen.Auth.route else Screen.Setup.route
            _initialState.value = InitialState.Ready(startDestination)

            // Set state for the lock screen activity to use
            _lockScreenState.value = LockScreenState.Ready(
                storedEmbedding = embedding,
                hasPin = pinExists,
                hasPattern = patternExists
            )
        }
    }

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // רק עדכן את מצב ההפעלה - אל תמחק את הנתונים!
            // כך שהמשתמש יוכל להפעיל שוב את הנעילה בלי להגדיר הכל מחדש
            preferenceManager.setLockEnabled(enabled)
            
            if (!enabled) {
                // Lock disabled - keeping all data
            } else {
                // Lock enabled
            }
        }
    }

    fun savePinCode(pin: String) { viewModelScope.launch { preferenceManager.savePinCode(pin) } }
    fun savePattern(pattern: String) { viewModelScope.launch { preferenceManager.savePattern(pattern) } }
    fun removePin() { viewModelScope.launch { preferenceManager.removePinCode() } }
    fun removePattern() { viewModelScope.launch { preferenceManager.removePattern() } }
    fun saveFaceEmbedding(embedding: FaceEmbedding) { viewModelScope.launch { preferenceManager.saveFaceEmbedding(embedding) } }

    suspend fun verifyPin(enteredPin: String): Boolean {
        // Always read the latest value from storage for verification
        return preferenceManager.pinCode.first() == enteredPin
    }

    suspend fun verifyPattern(enteredPattern: String): Boolean {
        // Always read the latest value from storage for verification
        return preferenceManager.patternLock.first() == enteredPattern
    }
}
