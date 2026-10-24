package app.work.domain

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

enum class ActivityKind {
    Log,
    Schedule,
    Backlog,
}

data class Activity(
    val id: String,
    val summary: String,
    val kind: ActivityKind,
    val jiraKey: String?,
    val onDate: LocalDate?,
    val createdAt: Instant,
)

data class NewActivity(
    val summary: String,
    val kind: ActivityKind,
    val jiraKey: String?,
    val onDate: LocalDate?,
)

interface ActivityStore {
    suspend fun add(draft: NewActivity): Activity

    suspend fun find(kind: ActivityKind, onDate: LocalDate): List<Activity>
}
