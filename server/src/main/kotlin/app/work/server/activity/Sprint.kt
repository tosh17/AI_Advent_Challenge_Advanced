package app.work.server.activity

import app.work.domain.Activity
import app.work.domain.ActivityKind
import app.work.domain.ActivityStore
import app.work.server.SprintEntry
import app.work.server.SprintResponse

interface Sprint {
    suspend fun load(): SprintResponse
}

class StoredSprint(private val store: ActivityStore) : Sprint {
    override suspend fun load(): SprintResponse {
        return SprintResponse(
            items = store.list(ActivityKind.Sprint).map { it.toEntry() },
        )
    }
}

private fun Activity.toEntry() = SprintEntry(
    id = id,
    summary = summary,
    issueKey = issueKey,
    link = link,
    createdAt = createdAt.toString(),
)
