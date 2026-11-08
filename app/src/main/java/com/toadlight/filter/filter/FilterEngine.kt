package com.toadlight.filter.filter

import com.toadlight.filter.core.AppGraph
import com.toadlight.filter.core.PaperDecisions
import com.toadlight.filter.core.color.ColorTemperature
import com.toadlight.filter.core.model.EngineParams
import com.toadlight.filter.core.model.ScheduleMode
import com.toadlight.filter.core.schedule.ScheduleEvaluator
import com.toadlight.filter.core.solar.SolarCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the UI needs to know about the filter right now. The service
 * consumes the overlay fields, screens consume the status fields.
 */
data class EngineState(
    val masterOn: Boolean = true,
    val paused: Boolean = false,
    val pauseEndsAt: Long = 0L,
    val overlayOn: Boolean = false,
    val fading: Boolean = false,
    val tintArgb: Int = 0,
    val tintAlpha: Float = 0f,
    val dimAlpha: Float = 0f,
    val paperOn: Boolean = false,
    val paperAlpha: Float = 0f,
    val paperIntensityPercent: Int = 0,
    /** Duration of the transition currently in progress, if any. */
    val fadeMs: Long = 0L,
    /** True on the recompute right after a profile/mode switch (view cross-fades). */
    val colorFading: Boolean = false,
    val scheduleOpen: Boolean = false,
    val previewing: Boolean = false,
    val previewEndsAt: Long = 0L,
    val nextChangeAt: Long? = null,
    val sunsetAt: Long? = null,
    val sunriseAt: Long? = null,
    val profileName: String = "",
    val colorTempK: Int = 3200,
    val intensityPercent: Int = 0,
    val dimPercent: Int = 0,
    val scheduleMode: ScheduleMode = ScheduleMode.SUN,
    val usingFallbackSchedule: Boolean = false,
    val perAppSuppressing: Boolean = false,
    /** True while the foreground app is on Paper Mode (reading finish). */
    val paperAppActive: Boolean = false,
)

/**
 * The heart of the app: turns (profile + settings + clock + foreground app)
 * into a concrete overlay color/dimness, including the gradual sunrise and
 * sunset fades.
 *
 * [recompute] is called whenever inputs change, and periodically by the
 * service ticker. All state lives in [state]; the class is thread-safe.
 */
class FilterEngine(private val graph: AppGraph) {

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Current foreground app package (fed by the accessibility watcher). */
    val foregroundPackage = MutableStateFlow<String?>(null)

    // Transition bookkeeping.
    private var targetOn: Boolean? = null
    private var transitionStartMs = 0L
    private var blendAtFlip = 0f
    private var durationMs = 0L

    /** Last blend the engine emitted; used so interrupted fades resume smoothly. */
    private var lastBlend = 0f

    // Flip-cause tracking: manual causes get the quick fade, astronomical
    // flips keep the long sunset/sunrise fade.
    private var lastProfileId: String? = null
    private var lastMaster: Boolean? = null
    private var lastPaused: Boolean? = null
    private var lastPreviewing: Boolean? = null
    private var lastSuppress: Boolean? = null
    private var lastPaperApp: Boolean? = null

    fun observe(scope: CoroutineScope) {
        scope.launch {
            combine(
                graph.settingsState,
                graph.profilesState,
                graph.activeProfileState,
            ) { _, _, _ -> Unit }.collect {
                recompute()
            }
        }
        scope.launch {
            @OptIn(FlowPreview::class)
            foregroundPackage.drop(1).debounce(400).collect {
                recompute()
            }
        }
    }

    fun setForegroundPackage(pkg: String?) {
        if (foregroundPackage.value != pkg) {
            foregroundPackage.value = pkg
        }
    }

    @Synchronized
    fun recompute() {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val settings = graph.settingsState.value
        val profile = graph.activeProfileState.value

        if (profile == null) {
            _state.value = EngineState(
                masterOn = settings.masterEnabled,
                paused = settings.pausedUntil > now,
                pauseEndsAt = settings.pausedUntil,
            )
            targetOn = null
            return
        }

        val paused = settings.pausedUntil > now
        val fg = foregroundPackage.value
        val override = if (settings.perAppEnabled) settings.appOverrides[fg] else null
        val suppress = PaperDecisions.isSuppressing(override)
        val paperApp = PaperDecisions.isPaperApp(override)

        val horizon =
            if (settings.civilTwilight) SolarCalculator.HORIZON_CIVIL_DEG
            else SolarCalculator.HORIZON_OFFICIAL_DEG

        val sunProvider: (LocalDate) -> SolarCalculator.SunEvents = { date ->
            SolarCalculator.eventsFor(
                date,
                settings.latitude ?: 0.0,
                settings.longitude ?: 0.0,
                horizon,
            )
        }

        val window = ScheduleEvaluator.evaluate(profile.schedule, settings, now, zone, sunProvider)
        val previewing = settings.previewUntil > now
        val effectiveOpen = window.open || previewing
        val newTarget = PaperDecisions.targetOn(
            masterEnabled = settings.masterEnabled,
            paused = paused,
            suppress = suppress,
            scheduleOpen = window.open,
            previewing = previewing,
            paperApp = paperApp,
        )

        // A flip is "manual" when a user action caused it (master toggle,
        // pause/resume, preview start/expiry, per-app suppression, entering
        // or leaving a Paper Mode app) — including the moment such a flag
        // returns to neutral. Those transitions use the quick fade; pure
        // schedule flips (sunset/sunrise) use the long fade.
        val manualCause = paused || !settings.masterEnabled || suppress || previewing ||
            lastMaster != settings.masterEnabled ||
            lastPaused != paused ||
            lastPreviewing != previewing ||
            lastSuppress != suppress ||
            lastPaperApp != paperApp
        val profileSwitched = lastProfileId != null && profile.id != lastProfileId

        lastProfileId = profile.id
        lastMaster = settings.masterEnabled
        lastPaused = paused
        lastPreviewing = previewing
        lastSuppress = suppress
        lastPaperApp = paperApp

        if (newTarget != targetOn) {
            blendAtFlip = lastBlend
            transitionStartMs = now
            targetOn = newTarget
            durationMs = if (manualCause) settings.quickTransitionMs.toLong()
                         else settings.transitionMinutes * 60_000L
        }

        val blend = blendValue(newTarget, now)
        lastBlend = blend

        val params: EngineParams = PaperDecisions.params(override, profile, effectiveOpen, paused)

        val tintArgb = ColorTemperature.argb(params.colorTempK)
        val paperIntensity = PaperDecisions.paperIntensity(override, profile)
        val paperOn = paperIntensity > 0

        val nextChange = listOfNotNull(
            window.nextChangeAt,
            if (paused && settings.pausedUntil > now) settings.pausedUntil else null,
            if (previewing) settings.previewUntil else null,
        ).minOrNull()

        _state.value = EngineState(
            masterOn = settings.masterEnabled,
            paused = paused,
            pauseEndsAt = settings.pausedUntil,
            overlayOn = settings.masterEnabled && blend > 0.004f,
            fading = durationMs > 0 && blend > 0.004f && blend < 0.996f,
            tintArgb = tintArgb,
            tintAlpha = (params.intensityPercent / 100f) * blend,
            dimAlpha = (params.dimPercent / 100f) * 0.95f * blend,
            paperOn = paperOn,
            paperAlpha = if (paperOn) (paperIntensity / 100f) * blend else 0f,
            paperIntensityPercent = if (paperOn) paperIntensity else 0,
            fadeMs = durationMs,
            colorFading = profileSwitched && settings.quickTransitionMs > 0,
            scheduleOpen = window.open,
            previewing = previewing,
            previewEndsAt = if (previewing) settings.previewUntil else 0L,
            nextChangeAt = nextChange,
            sunsetAt = window.sunsetAt,
            sunriseAt = window.sunriseAt,
            profileName = profile.name,
            colorTempK = params.colorTempK,
            intensityPercent = params.intensityPercent,
            dimPercent = params.dimPercent,
            scheduleMode = profile.schedule.mode,
            usingFallbackSchedule = window.usingFallback,
            perAppSuppressing = suppress,
            paperAppActive = paperApp,
        )
    }

    /** How long the service ticker should wait before the next recompute. */
    fun nextTickDelayMs(): Long {
        val s = _state.value
        val now = System.currentTimeMillis()
        var delay = 60_000L
        if (s.fading) {
            // Quick fades tick fast for smoothness; the multi-minute
            // astronomical fades keep the relaxed 1 s rhythm.
            delay = if (s.fadeMs in 1..3500L) 120L else 1_000L
        }
        s.nextChangeAt?.let { event ->
            if (event > now) delay = minOf(delay, event - now + 250L)
        }
        if (s.paused && s.pauseEndsAt > now) {
            delay = minOf(delay, s.pauseEndsAt - now + 250L)
        }
        return delay.coerceIn(120L, 60_000L)
    }

    /**
     * Blend factor 0..1 of the tint/dim strength. On a flip, the engine
     * remembers the blend at the moment of the flip so an interrupted fade
     * continues smoothly in the other direction.
     */
    private fun blendValue(forTargetOn: Boolean, now: Long): Float {
        if (durationMs <= 0L) return if (forTargetOn) 1f else 0f
        val progress = ((now - transitionStartMs).toFloat() / durationMs).coerceIn(0f, 1f)
        return if (forTargetOn) {
            blendAtFlip + (1f - blendAtFlip) * progress
        } else {
            blendAtFlip * (1f - progress)
        }
    }
}
