package com.facelockapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.facelockapp.ui.components.FaceEmbedding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "face_lock_preferences")

class PreferenceManager(context: Context) {
    private val dataStore = context.dataStore
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("face_lock_cache", Context.MODE_PRIVATE)

    companion object {
        private val KEY_IS_LOCK_ENABLED = booleanPreferencesKey("is_lock_enabled")
        private val KEY_PIN_CODE = stringPreferencesKey("pin_code")
        private val KEY_PATTERN = stringPreferencesKey("pattern_lock")
        private val KEY_FACE_EMBEDDING = stringPreferencesKey("face_embedding")
        private const val SHARED_PREFS_KEY_LOCK_ENABLED = "is_lock_enabled"
    }

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // סנכרן את הערך מ-DataStore ל-SharedPreferences ברקע (לקריאה מהירה בעתיד)
        syncScope.launch {
            try {
                val value = dataStore.data.first()[KEY_IS_LOCK_ENABLED] ?: false
                sharedPrefs.edit().putBoolean(SHARED_PREFS_KEY_LOCK_ENABLED, value).apply()
            } catch (e: Exception) {
                // אם יש שגיאה, השתמש בערך מה-SharedPreferences או false כברירת מחדל
            }
        }
    }

    // קריאה מהירה מ-SharedPreferences (ללא suspend, ללא Flow)
    fun isLockEnabledSync(): Boolean {
        return sharedPrefs.getBoolean(SHARED_PREFS_KEY_LOCK_ENABLED, false)
    }

    val isLockEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        val value = preferences[KEY_IS_LOCK_ENABLED] ?: false
        // עדכן את ה-SharedPreferences בכל פעם שהערך משתנה
        sharedPrefs.edit().putBoolean(SHARED_PREFS_KEY_LOCK_ENABLED, value).apply()
        value
    }
    suspend fun setLockEnabled(isEnabled: Boolean) {
        dataStore.edit { it[KEY_IS_LOCK_ENABLED] = isEnabled }
        // עדכן גם ב-SharedPreferences לקריאה מהירה
        sharedPrefs.edit().putBoolean(SHARED_PREFS_KEY_LOCK_ENABLED, isEnabled).apply()
    }

    val pinCode: Flow<String?> = dataStore.data.map { it[KEY_PIN_CODE] }
    suspend fun savePinCode(pin: String) {
        dataStore.edit { it[KEY_PIN_CODE] = pin }
    }
    suspend fun removePinCode() {
        dataStore.edit { it.remove(KEY_PIN_CODE) }
    }

    val patternLock: Flow<String?> = dataStore.data.map { it[KEY_PATTERN] }
    suspend fun savePattern(pattern: String) {
        dataStore.edit { it[KEY_PATTERN] = pattern }
    }
    suspend fun removePattern() {
        dataStore.edit { it.remove(KEY_PATTERN) }
    }

    // Face Embedding - stored as a comma-separated string
    val faceEmbedding: Flow<FaceEmbedding?> = dataStore.data.map { preferences ->
        preferences[KEY_FACE_EMBEDDING]?.let {
            val features = it.split(',').map { str -> str.toFloat() }.toFloatArray()
            // Face embedding loaded
            FaceEmbedding(features)
        }
    }

    suspend fun saveFaceEmbedding(embedding: FaceEmbedding) {
        val embeddingString = embedding.features.joinToString(",")
        // Face embedding saved
        dataStore.edit { it[KEY_FACE_EMBEDDING] = embeddingString }
    }

    suspend fun removeFaceEmbedding() {
        dataStore.edit { it.remove(KEY_FACE_EMBEDDING) }
    }
}
