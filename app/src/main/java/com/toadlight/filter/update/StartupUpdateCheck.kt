package com.toadlight.filter.update

import com.toadlight.filter.BuildConfig
import com.toadlight.filter.data.GithubReleaseChecker
import com.toadlight.filter.data.GitHubRelease
import com.toadlight.filter.data.UpdateChecker
import com.toadlight.filter.domain.VersionCompare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The silent once-per-process update check on app open (ported from toadFILES v1.7.1).
 *
 * PRIVACY STANCE: toadLIGHT contacts exactly one endpoint (the repo's
 * releases/latest) and sends nothing but a plain GET. The check runs once, silently,
 * when the app is opened — never periodically, never WorkManager, no auto-download.
 * It only fires when the app process comes up, and at most once per process
 * ([checkOnce] is a no-op after the first call).
 *
 * Failure is intentionally invisible: airplane mode, no network, GitHub being down —
 * the auto-check swallows everything and stays quiet. Only the manual check on the
 * Settings update panel shows errors. When a newer release WITH an APK asset is
 * found, the banner state ([banner]) is populated so MainActivity can show the subtle
 * "toadLIGHT vX available" card; tapping it deep-links to the Settings update panel
 * (UpdateViewModel seeds its state from [banner]).
 */
class StartupUpdateCheck(
    private val defaultChecker: UpdateChecker = GithubReleaseChecker(),
) {

    private val _banner = MutableStateFlow<GitHubRelease?>(null)

    /** Whether the user dismissed the banner for this process. */
    private val _bannerDismissed = MutableStateFlow(false)

    /** The newer release to advertise in the banner; null when there is nothing to show. */
    val banner: StateFlow<GitHubRelease?> = _banner

    /** True once the user dismissed the banner; the release itself stays available for panel seeding. */
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed

    /** True once [checkOnce] has actually started a check in this instance (= this process). */
    var started: Boolean = false
        private set

    /**
     * Runs the silent check at most once per instance. Guarded by [started] (set
     * synchronously before launching) so a re-composed LaunchedEffect in MainActivity
     * can never double-fire. Offline/other failures resolve silently.
     */
    fun checkOnce(
        scope: CoroutineScope,
        installedVersion: String = BuildConfig.VERSION_NAME,
        checker: UpdateChecker = defaultChecker,
    ) {
        if (started) return
        started = true
        scope.launch {
            // Silent by design: any failure (airplane mode, DNS, rate limit) is swallowed.
            val release = runCatching { checker.fetchLatestRelease() }.getOrNull() ?: return@launch
            if (release.apkUrl != null &&
                VersionCompare.isNewer(installedVersion, release.tagName)
            ) {
                _banner.value = release
            }
        }
    }

    /** Hides the banner for the rest of the process (the release itself is not forgotten by panel seeding). */
    fun dismissBanner() {
        _bannerDismissed.value = true
    }

    companion object {
        /** Process-wide instance MainActivity wires up; tests build their own. */
        val instance = StartupUpdateCheck()
    }
}
