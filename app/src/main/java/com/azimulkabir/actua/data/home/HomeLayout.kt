package com.azimulkabir.actua.data.home

import androidx.annotation.StringRes
import com.azimulkabir.actua.R
import org.json.JSONArray
import org.json.JSONObject

/** Stable section keys and the agreed V1 display order for the Home dashboard. */
enum class HomeSection(@StringRes val titleRes: Int) {
    READY_TO_BUDGET(R.string.home_ready_to_budget),
    FAVORITE_CATEGORIES(R.string.home_favorite_categories),
    FAVORITE_ACCOUNTS(R.string.home_favorite_accounts),
    UPCOMING(R.string.home_upcoming),
    THIS_MONTH(R.string.home_this_month),
    REPORTS(R.string.home_reports),
    RECENT_ACTIVITY(R.string.home_recent_activity),
}

/**
 * A user's customized Home layout: section order plus the optional sections they hid. This is
 * device-local UI preference, not budget financial data, so it never touches the Actual schema.
 */
data class HomeLayout(val order: List<HomeSection>, val hidden: Set<HomeSection>) {
    /** Sections to render, in display order, with hidden ones already filtered out. */
    val visibleSections: List<HomeSection> get() = order.filterNot { it in hidden }

    companion object {
        fun default(): HomeLayout = HomeLayout(HomeSection.entries.toList(), emptySet())
    }
}

/**
 * Pure reordering/visibility logic for [HomeLayout], mirroring [com.azimulkabir.actua.data.budget.CategoryReorderPlanner]:
 * no I/O here, so drag gestures can mutate an in-memory copy on every step and persist only once, on drop.
 *
 * Ready to Budget is pinned first and cannot be hidden: hiding Home's primary purpose would undermine
 * the screen, so it is treated as fixed/required rather than an optional section like the rest.
 */
object HomeLayoutPlanner {
    private val PINNED = HomeSection.READY_TO_BUDGET

    /**
     * Repairs a layout against the current [HomeSection] entries: unknown/removed sections are
     * dropped, newly introduced sections are appended in their declared (default) order rather than
     * corrupting the user's saved arrangement, and [PINNED] is always first and never hidden.
     */
    fun sanitize(layout: HomeLayout): HomeLayout {
        val known = HomeSection.entries
        val existing = layout.order.filter { it in known }.distinct()
        val missing = known.filterNot { it in existing }
        val reorderable = (existing + missing).filterNot { it == PINNED }
        val order = listOf(PINNED) + reorderable
        val hidden = layout.hidden.filterNot { it == PINNED }.toSet()
        return HomeLayout(order, hidden)
    }

    /** Moves [sectionId] to just before [targetId] (end of the reorderable sections when null). */
    fun moveSection(order: List<HomeSection>, sectionId: HomeSection, targetId: HomeSection?): List<HomeSection>? {
        if (sectionId == PINNED || targetId == PINNED) return null
        if (sectionId == targetId) return null
        if (sectionId !in order) return null
        val rest = order.filterNot { it == sectionId }
        val insertAt = targetId?.let { id -> rest.indexOf(id) }?.takeIf { it >= 0 } ?: rest.size
        return rest.toMutableList().apply { add(insertAt, sectionId) }
    }

    /** Moves [sectionId] one place up its reorderable order (Ready to Budget excluded). */
    fun moveSectionUp(order: List<HomeSection>, sectionId: HomeSection): List<HomeSection>? {
        val reorderable = order.filterNot { it == PINNED }
        val index = reorderable.indexOf(sectionId)
        if (index <= 0) return null
        return moveSection(order, sectionId, reorderable[index - 1])
    }

    /** Moves [sectionId] one place down its reorderable order (Ready to Budget excluded). */
    fun moveSectionDown(order: List<HomeSection>, sectionId: HomeSection): List<HomeSection>? {
        val reorderable = order.filterNot { it == PINNED }
        val index = reorderable.indexOf(sectionId)
        if (index < 0 || index >= reorderable.size - 1) return null
        return moveSection(order, sectionId, reorderable.getOrNull(index + 2))
    }

    /** Whether [sectionId] sits at a different index in [current] than in [original]. */
    fun hasMoved(original: List<HomeSection>, current: List<HomeSection>, sectionId: HomeSection): Boolean =
        original.indexOf(sectionId) != current.indexOf(sectionId)

    fun setHidden(layout: HomeLayout, sectionId: HomeSection, hidden: Boolean): HomeLayout {
        if (sectionId == PINNED) return layout
        val updated = layout.hidden.toMutableSet().apply { if (hidden) add(sectionId) else remove(sectionId) }
        return layout.copy(hidden = updated)
    }
}

/**
 * JSON encoding for [HomeLayout], stored as a single SharedPreferences string. [SCHEMA_VERSION] is
 * carried in the payload so a future schema change can migrate explicitly instead of guessing from
 * shape; today every payload is read through [HomeLayoutPlanner.sanitize], which already handles the
 * one migration this feature needs (section definitions changing) without a version bump.
 */
object HomeLayoutCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(layout: HomeLayout): String {
        val sanitized = HomeLayoutPlanner.sanitize(layout)
        return JSONObject()
            .put("version", SCHEMA_VERSION)
            .put("order", JSONArray(sanitized.order.map { it.name }))
            .put("hidden", JSONArray(sanitized.hidden.map { it.name }))
            .toString()
    }

    fun decode(raw: String?): HomeLayout {
        if (raw == null) return HomeLayout.default()
        val decoded = runCatching {
            val json = JSONObject(raw)
            val order = json.getJSONArray("order").toSectionList()
            val hidden = json.optJSONArray("hidden")?.toSectionList()?.toSet() ?: emptySet()
            HomeLayout(order, hidden)
        }.getOrDefault(HomeLayout.default())
        return HomeLayoutPlanner.sanitize(decoded)
    }

    private fun JSONArray.toSectionList(): List<HomeSection> =
        (0 until length()).mapNotNull { index -> runCatching { HomeSection.valueOf(getString(index)) }.getOrNull() }
}
