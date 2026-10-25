package app.work.domain

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

enum class ActivityKind {
    Log,
    Schedule,
    Backlog,
    Sprint,
}

data class Activity(
    val id: String,
    val summary: String,
    val kind: ActivityKind,
    val issueKey: String?,
    val link: String?,
    val onDate: LocalDate?,
    val createdAt: Instant,
)

data class NewActivity(
    val summary: String,
    val kind: ActivityKind,
    val issueKey: String?,
    val onDate: LocalDate?,
    val link: String? = null,
)

interface ActivityStore {
    suspend fun add(draft: NewActivity): Activity

    suspend fun find(kind: ActivityKind, onDate: LocalDate): List<Activity>

    suspend fun findBetween(kind: ActivityKind, from: LocalDate, to: LocalDate): List<Activity>

    suspend fun list(kind: ActivityKind): List<Activity>

    suspend fun search(query: String): List<Activity>
}
