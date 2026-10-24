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
        store.add(NewActivity("идея", ActivityKind.Backlog, null, null))

        assertEquals(listOf("ревью"), store.find(ActivityKind.Log, day).map { it.summary })
        assertEquals(listOf("созвон"), store.find(ActivityKind.Schedule, day).map { it.summary })
        assertEquals(emptyList(), store.find(ActivityKind.Log, LocalDate(2026, 10, 24)))
    }
}
