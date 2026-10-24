package app.work.server.activity

import app.work.domain.Activity
import app.work.domain.ActivityKind
import app.work.domain.ActivityStore
import app.work.domain.NewActivity
import app.work.server.AppConfig
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

fun openDatabase(config: AppConfig): Database = connect(
    url = config.databaseUrl,
    user = config.databaseUser,
    password = config.databasePassword,
)

fun connect(url: String, user: String, password: String): Database {
    val driver = if (url.startsWith("jdbc:postgresql:")) "org.postgresql.Driver" else "org.h2.Driver"
    return Database.connect(url = url, driver = driver, user = user, password = password)
}

object ActivityTable : Table("activity") {
    val id = varchar("id", 36)
    val summary = varchar("summary", 2_000)
    val kind = varchar("kind", 16)
    val jiraKey = varchar("jira_key", 40).nullable()
    val onDate = varchar("on_date", 10).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

class ExposedActivityStore(private val database: Database) : ActivityStore {
    init {
        transaction(database) {
            SchemaUtils.create(ActivityTable)
        }
    }

    override suspend fun add(draft: NewActivity): Activity = db {
        require(draft.summary.isNotBlank())
        require(draft.summary.length <= 2_000)
        val id = UUID.randomUUID().toString()
        val createdAt = Clock.System.now()
        ActivityTable.insert {
            it[ActivityTable.id] = id
            it[summary] = draft.summary
            it[kind] = draft.kind.name
            it[jiraKey] = draft.jiraKey
            it[onDate] = draft.onDate?.toString()
            it[ActivityTable.createdAt] = createdAt.toEpochMilliseconds()
        }
        Activity(
            id = id,
            summary = draft.summary,
            kind = draft.kind,
            jiraKey = draft.jiraKey,
            onDate = draft.onDate,
            createdAt = createdAt,
        )
    }

    override suspend fun find(kind: ActivityKind, onDate: LocalDate): List<Activity> = db {
        ActivityTable.selectAll()
            .where {
                (ActivityTable.kind eq kind.name) and (ActivityTable.onDate eq onDate.toString())
            }
            .orderBy(ActivityTable.createdAt to SortOrder.ASC)
            .map { row ->
                Activity(
                    id = row[ActivityTable.id],
                    summary = row[ActivityTable.summary],
                    kind = ActivityKind.valueOf(row[ActivityTable.kind]),
                    jiraKey = row[ActivityTable.jiraKey],
                    onDate = row[ActivityTable.onDate]?.let(LocalDate::parse),
                    createdAt = Instant.fromEpochMilliseconds(row[ActivityTable.createdAt]),
                )
            }
    }

    private suspend fun <T> db(block: () -> T): T = withContext(Dispatchers.IO) {
        transaction(database) { block() }
    }
}
