package com.toadlight.filter.core

import com.toadlight.filter.core.model.AppOverride
import com.toadlight.filter.core.model.OverrideMode

/**
 * Curated package candidates for one-tap Paper Mode setup.
 *
 * These are well-known PDF readers, e-book and reading apps. The list is
 * deliberately generous: [applicable] keeps only packages that are actually
 * installed on the device and that the user has not already customized, so a
 * stale or wrong package name in the list is harmless — it simply never
 * matches an installed app.
 */
object PaperPresets {

    val CANDIDATES: List<String> = listOf(
        "com.adobe.reader",                     // Adobe Acrobat Reader
        "com.google.android.apps.pdfviewer",    // Google PDF Viewer
        "com.xodo.pdf.reader",                  // Xodo PDF Reader & Editor
        "com.amazon.kindle",                    // Amazon Kindle
        "cn.wps.moffice_eng",                   // WPS Office (PDF viewing)
        "com.foobnix.pdf.reader",               // EBook Reader & PDF Reader
        "org.geometerplus.zlibrary.ui.android", // FBReader
        "com.flyersoft.moonreaderp",            // Moon+ Reader
        "com.duokan.reader",                    // 多看阅读
        "com.qq.reader",                        // QQ阅读
        "com.tencent.weread",                   // 微信读书
        "com.qidian.QDReader",                  // 起点读书
    )

    /**
     * Which candidates should receive a Paper Mode override right now:
     * installed on this device, and not already given a custom rule
     * (PAPER / CUSTOM / NO_FILTER) by the user — one-tap setup must never
     * silently clobber deliberate choices.
     *
     * @param installed package names visible on the device (launcher or
     *   PackageManager list; the Apps screen already has this).
     * @param existing the user's current per-app overrides.
     */
    fun applicable(
        installed: Set<String>,
        existing: Map<String, AppOverride>,
    ): List<String> = CANDIDATES.filter { pkg ->
        pkg in installed &&
            (existing[pkg]?.mode ?: OverrideMode.DEFAULT) == OverrideMode.DEFAULT
    }

    /** The user's packages currently on Paper Mode (for "remove from all"). */
    fun paperPackages(existing: Map<String, AppOverride>): List<String> =
        existing.values.filter { it.mode == OverrideMode.PAPER }.map { it.packageName }
}
