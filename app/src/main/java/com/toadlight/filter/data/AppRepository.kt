package com.toadlight.filter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.toadlightDataStore by preferencesDataStore(name = "toadlight_settings")

/**
 * Single source of truth for profiles and settings, persisted as JSON in
 * Preferences DataStore.
 *
 * ## Concurrency contract
 *
 * The app writes from many independent coroutines on different threads:
 * the Compose UI (settings/profile screens, onboarding), the ViewModel, the
 * overlay [com.toadlight.filter.filter.FilterService] loop, both quick-setting
 * tiles, the boot receiver and the floating bubble. DataStore serializes
 * individual `edit` transactions, but a *read-modify-write* cycle is only as
 * atomic as the gap between its read and its write: two overlapping cycles
 * silently drop one writer's change (the classic lost update — e.g. a
 * profile added while the tile cycles modes simply vanishes).
 *
 * Every public mutating function here therefore runs its whole
 * read → transform → write cycle under [writeMutex], a single-writer
 * discipline. This class is the *only* writer of the store (verified: no
 * other component touches the DataStore directly), so the mutex plus
 * DataStore's own atomic commits make all updates linearizable.
 *
 * Public functions never call each other (only the private `*Locked`
 * primitives, which must be invoked while holding the mutex) — a Mutex is
 * not re-entrant, so nesting would deadlock.
 *
 * @param store injected so JVM unit tests can run a real DataStore against
 *   a temp file without Android.
 */
class AppRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.toadlightDataStore)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Serializes every read-modify-write cycle; see the class docs. */
    private val writeMutex = Mutex()

    private val keySettings = stringPreferencesKey("settings_json")
    private val keyProfiles = stringPreferencesKey("profiles_json")
    private val keyActiveProfile = stringPreferencesKey("active_profile_id")

    val settingsFlow = store.data.map { prefs ->
        decode(prefs[keySettings], AppSettings())
    }

    val profilesFlow = store.data.map { prefs ->
        decode(prefs[keyProfiles], emptyList<Profile>())
    }

    val activeProfileIdFlow = store.data.map { prefs ->
        prefs[keyActiveProfile] ?: ""
    }

    /** Creates the starter profiles the very first time the app runs. */
    suspend fun seedIfEmpty() = writeMutex.withLock {
        if (readProfilesLocked().isEmpty()) {
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
            writeProfilesLocked(seeds, seeds.first().id)
        }
    }

    /**
     * Applies [transform] to the settings. The base settings are decoded from
     * the transaction's own preferences (not from a side flow read), so the
     * transform always sees the store state as of this write — and the whole
     * cycle is serialized by [writeMutex] against the other writers.
     */
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        writeMutex.withLock {
            store.edit { prefs ->
                val current = decode(prefs[keySettings], AppSettings())
                prefs[keySettings] = json.encodeToString(transform(current))
            }
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

    /**
     * Replaces the whole profile list (and optionally the active profile id)
     * in one atomic DataStore transaction. Direct writes of a *complete*
     * list — no read involved — so it only needs the mutex for ordering
     * against the other read-modify-write cycles.
     */
    suspend fun saveProfiles(profiles: List<Profile>, activeId: String?) {
        writeMutex.withLock {
            writeProfilesLocked(profiles, activeId)
        }
    }

    suspend fun setActiveProfile(id: String) {
        writeMutex.withLock {
            store.edit { prefs -> prefs[keyActiveProfile] = id }
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
    suspend fun cycleNextProfile(): Profile? = writeMutex.withLock {
        val list = readProfilesLocked()
        if (list.isEmpty()) return null
        val active = readActiveProfileIdLocked()
        val index = list.indexOfFirst { it.id == active }.takeIf { it >= 0 } ?: 0
        val next = list[(index + 1) % list.size]
        store.edit { prefs -> prefs[keyActiveProfile] = next.id }
        next
    }

    suspend fun addProfile(profile: Profile) {
        writeMutex.withLock {
            writeProfilesLocked(readProfilesLocked() + profile, null)
        }
    }

    suspend fun updateProfile(profile: Profile) {
        writeMutex.withLock {
            writeProfilesLocked(
                readProfilesLocked().map { if (it.id == profile.id) profile else it },
                null,
            )
        }
    }

    suspend fun deleteProfile(id: String) {
        writeMutex.withLock {
            val current = readProfilesLocked()
            if (current.size <= 1) return // never delete the last profile
            val remaining = current.filterNot { it.id == id }
            val active = readActiveProfileIdLocked()
            writeProfilesLocked(
                remaining,
                if (active == id) remaining.firstOrNull()?.id else null,
            )
        }
    }

    suspend fun duplicateProfile(profile: Profile): Profile = writeMutex.withLock {
        val current = readProfilesLocked()
        val copy = profile.copy(
            id = UUID.randomUUID().toString(),
            name = uniqueName(current, profile.name),
            builtIn = false,
        )
        writeProfilesLocked(current + copy, copy.id)
        copy
    }

    suspend fun updateActiveProfile(transform: (Profile) -> Profile) {
        writeMutex.withLock {
            val list = readProfilesLocked()
            val active = readActiveProfileIdLocked()
            val target = list.firstOrNull { it.id == active } ?: list.firstOrNull() ?: return
            writeProfilesLocked(list.map { if (it.id == target.id) transform(it) else it }, null)
        }
    }

    // ------------------------------------------------------------------
    // Private primitives — call ONLY while holding [writeMutex].
    // ------------------------------------------------------------------

    private suspend fun readProfilesLocked(): List<Profile> =
        decode(store.data.first()[keyProfiles], emptyList())

    private suspend fun readActiveProfileIdLocked(): String =
        store.data.first()[keyActiveProfile] ?: ""

    /** Writes the list (and active id when non-null) in one atomic edit. */
    private suspend fun writeProfilesLocked(profiles: List<Profile>, activeId: String?) {
        store.edit { prefs ->
            prefs[keyProfiles] = json.encodeToString(profiles)
            if (activeId != null) prefs[keyActiveProfile] = activeId
        }
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
