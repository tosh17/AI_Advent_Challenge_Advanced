package app.work.server.activity

import kotlin.test.Test
import kotlin.test.assertEquals

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

import app.work.domain.ActivityKind
import app.work.domain.NewActivity

class ActivitySearchTest {
    @Test
    fun findsByIssueKeyAndListsOnlySprint() = runTest {
        val store = ExposedActivityStore(
            connect(
                url = "jdbc:h2:mem:search_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                user = "sa",
                password = "",
            ),
        )
        val day = LocalDate(2026, 10, 24)
        store.add(NewActivity("ревью", ActivityKind.Sprint, "APP-9", null, "https://example.test/review"))
        store.add(NewActivity("созвон", ActivityKind.Log, "APP-2", day))

        assertEquals(listOf("ревью"), store.list(ActivityKind.Sprint).map { it.summary })
        assertEquals(listOf("ревью"), store.search("app-9").map { it.summary })
        assertEquals(listOf("https://example.test/review"), store.search("REVIEW").map { it.link })
        assertEquals(emptyList(), store.search("нет такого"))
        assertEquals(emptyList(), store.search(""))
        assertEquals(emptyList(), store.search("   "))
        assertEquals(emptyList(), store.list(ActivityKind.Backlog))
    }
}
