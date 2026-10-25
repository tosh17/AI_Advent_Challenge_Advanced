package app.work.server.activity

import app.work.domain.Activity
import app.work.domain.ActivityStore
import app.work.server.BlankQuery
import app.work.server.SearchHit
import app.work.server.SearchResponse

interface Search {
    suspend fun load(rawQuery: String): SearchResponse
}

class StoredSearch(private val store: ActivityStore) : Search {
    override suspend fun load(rawQuery: String): SearchResponse {
        val query = rawQuery.trim()
        if (query.isEmpty()) throw BlankQuery()
        return SearchResponse(
            items = store.search(query).map { it.toHit() },
        )
    }
}

private fun Activity.toHit() = SearchHit(
    kind = kind.name,
    summary = summary,
    issueKey = issueKey,
    link = link,
    onDate = onDate?.toString(),
)
