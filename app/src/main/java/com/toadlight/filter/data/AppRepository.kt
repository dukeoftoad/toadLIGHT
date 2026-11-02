package com.toadlight.filter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.toadlight.filter.core.model.AppSettings
import com.toadlight.filter.core.model.AppOverride
import com.toadlight.filter.core.model.OverrideMode
import com.toadlight.filter.core.model.Profile
import com.toadlight.filter.core.model.Schedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.toadlightDataStore by preferencesDataStore(name = "toadlight_settings")

/**
 * Single source of truth for profiles and settings, persisted as JSON in
 * Preferences DataStore. All writes are atomic and flows are cold reads on
 * top of DataStore's transactional log.
 */
class AppRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val keySettings = stringPreferencesKey("settings_json")
    private val keyProfiles = stringPreferencesKey("profiles_json")
    private val keyActiveProfile = stringPreferencesKey("active_profile_id")

    val settingsFlow = context.toadlightDataStore.data.map { prefs ->
        decode(prefs[keySettings], AppSettings())
    }

    val profilesFlow = context.toadlightDataStore.data.map { prefs ->
        decode(prefs[keyProfiles], emptyList<Profile>())
    }

    val activeProfileIdFlow = context.toadlightDataStore.data.map { prefs ->
        prefs[keyActiveProfile] ?: ""
    }

    /** Creates the starter profiles the very first time the app runs. */
    suspend fun seedIfEmpty() {
        val profiles = profilesFlow.first()
        if (profiles.isEmpty()) {
            val seeds = listOf(
                Profile(
                    id = UUID.randomUUID().toString(),
                    name = "Evening",
                    colorTempK = 3200,
                    intensityPercent = 55,
                    dimPercent = 15,
                    schedule = Schedule(),
                    builtIn = true,
                ),
                Profile(
                    id = UUID.randomUUID().toString(),
                    name = "Bed reading",
                    colorTempK = 2000,
                    intensityPercent = 75,
                    dimPercent = 35,
                    schedule = Schedule(),
                    builtIn = true,
                ),
            )
            saveProfiles(seeds, seeds.first().id)
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.toadlightDataStore.edit { prefs ->
            prefs[keySettings] = json.encodeToString(transform(settingsFlow.first()))
        }
    }

    suspend fun setMasterEnabled(on: Boolean) = updateSettings { it.copy(masterEnabled = on) }

    /** Forces the filter on for [durationMs] from now, bypassing the schedule. */
    suspend fun startPreview(durationMs: Long) = updateSettings {
        it.copy(previewUntil = System.currentTimeMillis() + durationMs)
    }

    /** Drops any running preview; the schedule takes over immediately. */
    suspend fun endPreview() = updateSettings { it.copy(previewUntil = 0L) }

    suspend fun pauseFor(durationMs: Long) = updateSettings {
        it.copy(pausedUntil = System.currentTimeMillis() + durationMs)
    }

    suspend fun resume() = updateSettings { it.copy(pausedUntil = 0L) }

    suspend fun setFloatingMenuEnabled(on: Boolean) =
        updateSettings { it.copy(floatingMenuEnabled = on) }

    suspend fun saveBubblePosition(x: Int, y: Int) =
        updateSettings { it.copy(bubblePosX = x, bubblePosY = y) }

    suspend fun completeOnboarding() = updateSettings { it.copy(onboardingDone = true) }

    suspend fun setLocation(latitude: Double?, longitude: Double?) =
        updateSettings { it.copy(latitude = latitude, longitude = longitude) }

    suspend fun setAppOverride(override: AppOverride) = updateSettings { settings ->
        if (override.mode == OverrideMode.DEFAULT) {
            settings.copy(appOverrides = settings.appOverrides - override.packageName)
        } else {
            settings.copy(appOverrides = settings.appOverrides + (override.packageName to override))
        }
    }

    suspend fun saveProfiles(profiles: List<Profile>, activeId: String?) {
        context.toadlightDataStore.edit { prefs ->
            prefs[keyProfiles] = json.encodeToString(profiles)
            if (activeId != null) prefs[keyActiveProfile] = activeId
        }
    }

    suspend fun setActiveProfile(id: String) {
        context.toadlightDataStore.edit { prefs ->
            prefs[keyActiveProfile] = id
        }
    }

    /**
     * Switches the active profile/mode to the next one in the list (wrapping
     * around). Used by the notification action, the Mode quick tile and the
     * floating bubble so the mode can be changed from anywhere.
     *
     * @return the newly active profile, or null when switching is impossible
     *   (no profiles stored yet).
     */
    suspend fun cycleNextProfile(): Profile? {
        val list = profilesFlow.first()
        if (list.isEmpty()) return null
        val active = activeProfileIdFlow.first()
        val index = list.indexOfFirst { it.id == active }.takeIf { it >= 0 } ?: 0
        val next = list[(index + 1) % list.size]
        setActiveProfile(next.id)
        return next
    }

    suspend fun addProfile(profile: Profile) {
        saveProfiles(profilesFlow.first() + profile, null)
    }

    suspend fun updateProfile(profile: Profile) {
        saveProfiles(profilesFlow.first().map { if (it.id == profile.id) profile else it }, null)
    }

    suspend fun deleteProfile(id: String) {
        val current = profilesFlow.first()
        if (current.size <= 1) return // never delete the last profile
        val remaining = current.filterNot { it.id == id }
        val active = activeProfileIdFlow.first()
        saveProfiles(
            remaining,
            if (active == id) remaining.firstOrNull()?.id else null,
        )
    }

    suspend fun duplicateProfile(profile: Profile): Profile {
        val current = profilesFlow.first()
        val copy = profile.copy(
            id = UUID.randomUUID().toString(),
            name = uniqueName(current, profile.name),
            builtIn = false,
        )
        saveProfiles(current + copy, copy.id)
        return copy
    }

    suspend fun updateActiveProfile(transform: (Profile) -> Profile) {
        val list = profilesFlow.first()
        val active = activeProfileIdFlow.first()
        val target = list.firstOrNull { it.id == active } ?: list.firstOrNull() ?: return
        saveProfiles(list.map { if (it.id == target.id) transform(it) else it }, null)
    }

    private fun uniqueName(existing: List<Profile>, base: String): String {
        var name = "$base copy"
        var index = 2
        while (existing.any { it.name == name }) {
            name = "$base copy $index"
            index++
        }
        return name
    }

    private inline fun <reified T> decode(raw: String?, default: T): T =
        raw?.let { encoded ->
            runCatching { json.decodeFromString<T>(encoded) }.getOrNull()
        } ?: default
}
