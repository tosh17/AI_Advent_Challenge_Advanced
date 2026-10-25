package app.work.server.activity

import app.work.domain.Activity
import app.work.domain.ActivityKind
import app.work.domain.ActivityStore
import app.work.server.BacklogEntry
import app.work.server.BacklogResponse

interface Backlog {
    suspend fun load(): BacklogResponse
}

class StoredBacklog(private val store: ActivityStore) : Backlog {
    override suspend fun load(): BacklogResponse {
        return BacklogResponse(
            items = store.list(ActivityKind.Backlog).map { it.toEntry() },
        )
    }
}

private fun Activity.toEntry() = BacklogEntry(
    id = id,
    summary = summary,
    issueKey = issueKey,
    link = link,
    createdAt = createdAt.toString(),
)
