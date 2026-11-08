package com.toadlight.filter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.toadlight.filter.core.model.AppSettings
import com.toadlight.filter.core.model.Profile
import com.toadlight.filter.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Concurrency contract tests for [AppRepository]: every read-modify-write
 * cycle must be serialized (single-writer discipline) so that concurrent
 * callers from the UI, the overlay service, the quick tiles and the boot
 * receiver can never drop each other's updates.
 *
 * Uses a real Preferences DataStore on a temp file (JVM artifact — no
 * Android runtime needed). The stress tests launch genuinely parallel
 * coroutines on [Dispatchers.Default]; without the repository's write mutex
 * these tests fail with lost updates.
 */
class AppRepositoryTest {

    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var repo: AppRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        file = File.createTempFile("toadlight-repo-test", ".preferences_pb")
        store = PreferenceDataStoreFactory.create(scope = scope) { file }
        repo = AppRepository(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    // ------------------------------------------------------------------
    // Lost update: profiles
    // ------------------------------------------------------------------

    @Test
    fun `concurrent addProfile never loses a profile`() = runBlocking {
        repo.seedIfEmpty()

        val jobs = (1..20).map { i ->
            launch(Dispatchers.Default) {
                repo.addProfile(Profile(id = "added-$i", name = "P$i"))
            }
        }
        jobs.forEach { it.join() }

        val names = repo.profilesFlow.first().map { it.id }
        assertEquals("2 seeds + 20 added profiles must all survive", 22, names.size)
        (1..20).forEach { i ->
            assertTrue("profile added-$i was lost to a concurrent writer", "added-$i" in names)
        }
    }

    @Test
    fun `seeding racing profile adds keeps both seed and additions`() = runBlocking {
        // seedIfEmpty reads "is the store empty" and then writes the seeds;
        // a late seed write used to erase profiles added in between.
        val jobs = listOf(
            launch(Dispatchers.Default) { repo.seedIfEmpty() },
        ) + (1..10).map { i ->
            launch(Dispatchers.Default) {
                repo.addProfile(Profile(id = "added-$i", name = "P$i"))
            }
        }
        jobs.forEach { it.join() }

        // Depending on who wins the empty check the seed either runs (2 seeds
        // + 10 additions) or is skipped (10 additions) — but every addition
        // must survive and the seed must never run twice.
        val ids = repo.profilesFlow.first().map { it.id }
        val seeds = ids.count { !it.startsWith("added-") }
        assertTrue(
            "expected 10 additions + (0 or 2) seeds, got ${ids.size} ids with $seeds seeds",
            ids.size == 10 + seeds && (seeds == 0 || seeds == 2),
        )
        (1..10).forEach { i ->
            assertTrue("profile added-$i was erased by the seed write", "added-$i" in ids)
        }
    }

    @Test
    fun `concurrent updateProfile and addProfile compose`() = runBlocking {
        repo.seedIfEmpty()
        val seed = repo.profilesFlow.first().first()

        val jobs = (1..10).map { i ->
            launch(Dispatchers.Default) {
                repo.addProfile(Profile(id = "added-$i", name = "P$i"))
            }
        } + (1..10).map { i ->
            launch(Dispatchers.Default) {
                repo.updateProfile(seed.copy(name = "renamed-$i"))
            }
        }
        jobs.forEach { it.join() }

        val profiles = repo.profilesFlow.first()
        assertEquals(12, profiles.size)
        assertEquals(seed.id, profiles.first { it.id == seed.id }.id)
        // rename raced 10x — the surviving name is one of the candidates
        assertTrue(profiles.first { it.id == seed.id }.name.startsWith("renamed-"))
    }

    // ------------------------------------------------------------------
    // Lost update: settings fields
    // ------------------------------------------------------------------

    @Test
    fun `concurrent settings writers keep every field`() = runBlocking {
        repo.seedIfEmpty()

        val jobs =
            (1..10).map { i -> launch(Dispatchers.Default) { repo.saveBubblePosition(i, -i) } } +
                (1..10).map { _ -> launch(Dispatchers.Default) { repo.setMasterEnabled(true) } } +
                (1..10).map { _ -> launch(Dispatchers.Default) { repo.completeOnboarding() } } +
                (1..10).map { _ -> launch(Dispatchers.Default) { repo.setFloatingMenuEnabled(true) } }

        jobs.forEach { it.join() }

        val settings = repo.settingsFlow.first()
        assertTrue("masterEnabled flag lost to a concurrent writer", settings.masterEnabled)
        assertTrue("onboardingDone flag lost to a concurrent writer", settings.onboardingDone)
        assertTrue("floatingMenuEnabled flag lost to a concurrent writer", settings.floatingMenuEnabled)
        // bubble position is whichever writer won — but it must be one of them,
        // not the (0, 0) default of a stale read.
        assertTrue(
            "bubble position ${settings.bubblePosX},${settings.bubblePosY} is not from any writer",
            settings.bubblePosX in 1..10 && settings.bubblePosY in -10..-1,
        )
    }

    @Test
    fun `updateSettings transform sees the previous writer's commit`() = runBlocking {
        repo.setMasterEnabled(true)
        repo.saveBubblePosition(7, 9)

        val settings = repo.settingsFlow.first()
        assertTrue(settings.masterEnabled)
        assertEquals(7, settings.bubblePosX)
        assertEquals(9, settings.bubblePosY)
    }

    // ------------------------------------------------------------------
    // cycleNextProfile integrity under contention
    // ------------------------------------------------------------------

    @Test
    fun `concurrent cycleNextProfile keeps a valid active id`() = runBlocking {
        repo.seedIfEmpty()
        val ids = repo.profilesFlow.first().map { it.id }.toSet()

        val jobs = (1..15).map {
            launch(Dispatchers.Default) {
                val next = repo.cycleNextProfile()
                assertNotNull("cycling must succeed while profiles exist", next)
            }
        }
        jobs.forEach { it.join() }

        assertEquals("profile list mutated by cycling", 2, repo.profilesFlow.first().size)
        assertTrue("active id must point at a real profile", repo.activeProfileIdFlow.first() in ids)
    }

    // ------------------------------------------------------------------
    // Sequential behavior guards (regressions while refactoring)
    // ------------------------------------------------------------------

    @Test
    fun `deleteProfile reassigns the active profile and never deletes the last one`() = runBlocking {
        repo.seedIfEmpty()
        val first = repo.profilesFlow.first()[0]
        val second = repo.profilesFlow.first()[1]

        repo.setActiveProfile(first.id)
        repo.deleteProfile(first.id)

        val remaining = repo.profilesFlow.first()
        assertEquals(listOf(second.id), remaining.map { it.id })
        assertEquals(second.id, repo.activeProfileIdFlow.first())

        // deleting the last profile is a no-op
        repo.deleteProfile(second.id)
        assertEquals(1, repo.profilesFlow.first().size)
    }

    @Test
    fun `duplicateProfile gets a unique name and activates the copy`() = runBlocking {
        repo.seedIfEmpty()
        val original = repo.profilesFlow.first()[0]

        val first = repo.duplicateProfile(original)
        val second = repo.duplicateProfile(original)

        assertEquals("Evening copy", first.name)
        assertEquals("Evening copy 2", second.name)
        assertEquals(4, repo.profilesFlow.first().size)
        assertEquals(second.id, repo.activeProfileIdFlow.first())
    }

    @Test
    fun `app overrides apply and clear atomically per package`() = runBlocking {
        repo.setAppOverride(
            com.toadlight.filter.core.model.AppOverride(
                packageName = "com.example.app",
                mode = com.toadlight.filter.core.model.OverrideMode.CUSTOM,
            )
        )
        repo.setAppOverride(
            com.toadlight.filter.core.model.AppOverride(
                packageName = "com.other.app",
                mode = com.toadlight.filter.core.model.OverrideMode.NO_FILTER,
            )
        )

        var settings = repo.settingsFlow.first()
        assertEquals(2, settings.appOverrides.size)

        repo.setAppOverride(
            com.toadlight.filter.core.model.AppOverride(
                packageName = "com.example.app",
                mode = com.toadlight.filter.core.model.OverrideMode.DEFAULT,
            )
        )

        settings = repo.settingsFlow.first()
        assertEquals(1, settings.appOverrides.size)
        assertTrue("com.other.app" in settings.appOverrides)
    }

    // ------------------------------------------------------------------
    // Raw store interaction guard
    // ------------------------------------------------------------------

    @Test
    fun `repository writes survive an external single-key edit`() = runBlocking {
        // The settings JSON and a foreign key share the same DataStore file;
        // an atomic edit of the foreign key must not disturb settings.
        val foreignKey = stringPreferencesKey("foreign")
        repo.setMasterEnabled(true)
        store.edit { it[foreignKey] = "x" }

        assertTrue(repo.settingsFlow.first().masterEnabled)
    }
}
