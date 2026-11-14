package com.hanhyo.shuttle.parent

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.guardianSettings by preferencesDataStore(
    name = "guardian-settings",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "guardian-account",
        keysToMigrate = setOf("askedNotification", "notificationPermissionRequested"))) }
)

@Singleton
class GuardianSettings @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.guardianSettings
    private val baseKey = stringPreferencesKey("serverUrl")
    private val autoPromptKey = booleanPreferencesKey("askedNotification")
    private val runtimePromptKey = booleanPreferencesKey("notificationPermissionRequested")
    private val preferences = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }
    val serverUrl = preferences.map { it[baseKey] }
    val runtimePermissionRequested = preferences.map { it[runtimePromptKey] ?: false }
    suspend fun setServerUrl(value: String) { store.edit { it[baseKey] = value } }
    suspend fun recordRuntimePermissionRequest() { store.edit { it[runtimePromptKey] = true } }
    suspend fun takeAutomaticPrompt(): Boolean {
        var first = false
        store.edit { if (it[autoPromptKey] != true) { it[autoPromptKey] = true; first = true } }
        return first
    }
}
