package app.work.server.activity

import app.work.domain.ActivityKind
import app.work.domain.NewActivity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityStoreTest {
    @Test
    fun keepsLogPlanAndBacklogApart() = runTest {
        val store = ExposedActivityStore(
            Database.connect(
                url = "jdbc:h2:mem:work_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                user = "sa",
                password = "",
            ),
        )
        val day = LocalDate(2026, 10, 23)
        store.add(NewActivity("ревью", ActivityKind.Log, "APP-2", day))
        store.add(NewActivity("созвон", ActivityKind.Schedule, null, day))
        store.add(NewActivity("идея", ActivityKind.Backlog, null, null, "https://example.test/mock"))

        assertEquals(listOf("ревью"), store.find(ActivityKind.Log, day).map { it.summary })
        assertEquals(listOf("созвон"), store.find(ActivityKind.Schedule, day).map { it.summary })
        assertEquals(emptyList(), store.find(ActivityKind.Log, LocalDate(2026, 10, 24)))
        assertEquals(listOf("https://example.test/mock"), store.search("mock").map { it.link })
        assertEquals(listOf(null), store.search("mock").map { it.issueKey })
    }

    @Test
    fun journalRangeKeepsThePlanOut() = runTest {
        val store = ExposedActivityStore(
            Database.connect(
                url = "jdbc:h2:mem:range_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                user = "sa",
                password = "",
            ),
        )
        store.add(NewActivity("вчера", ActivityKind.Log, null, LocalDate(2026, 10, 22)))
        store.add(NewActivity("ревью", ActivityKind.Log, null, LocalDate(2026, 10, 23)))
        store.add(NewActivity("созвон", ActivityKind.Schedule, null, LocalDate(2026, 10, 23)))
        val result = store.findBetween(ActivityKind.Log, LocalDate(2026, 10, 22), LocalDate(2026, 10, 24))
        assertEquals(listOf("вчера", "ревью"), result.map { it.summary })
    }

    @Test
    fun sameSprintIssueKeyIsStoredOnce() = runTest {
        val store = ExposedActivityStore(
            Database.connect(
                url = "jdbc:h2:mem:sprint_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                user = "sa",
                password = "",
            ),
        )
        val first = store.add(NewActivity("ревью", ActivityKind.Sprint, "TESS-01", null))
        val second = store.add(NewActivity("Выполнено вчера", ActivityKind.Sprint, "TESS-01", null))
        store.add(NewActivity("без номера", ActivityKind.Sprint, null, null))
        assertEquals(first.id, second.id)
        assertEquals(listOf("ревью", "без номера"), store.list(ActivityKind.Sprint).map { it.summary })
    }
}
