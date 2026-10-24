package app.work.server.activity

import kotlinx.datetime.LocalDate

import app.work.domain.Activity
import app.work.domain.ActivityKind
import app.work.domain.ActivityStore
import app.work.server.BadDate
import app.work.server.DayEntry
import app.work.server.DayResponse

interface Days {
    suspend fun load(rawDate: String): DayResponse
}

class StoredDays(private val store: ActivityStore) : Days {
    override suspend fun load(rawDate: String): DayResponse {
        val date = parseDay(rawDate)
        return DayResponse(
            date = date.toString(),
            log = store.find(ActivityKind.Log, date).map { it.toEntry() },
            schedule = store.find(ActivityKind.Schedule, date).map { it.toEntry() },
        )
    }
}

private fun parseDay(raw: String): LocalDate = try {
    LocalDate.parse(raw)
} catch (_: IllegalArgumentException) {
    throw BadDate()
}

private fun Activity.toEntry() = DayEntry(
    id = id,
    summary = summary,
    jiraKey = jiraKey,
    createdAt = createdAt.toString(),
)
