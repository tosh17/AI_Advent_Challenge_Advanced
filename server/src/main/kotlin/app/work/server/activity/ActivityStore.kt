package app.work.server.activity

import kotlin.time.Clock
import kotlin.time.Instant

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

import app.work.domain.Activity
import app.work.domain.ActivityKind
import app.work.domain.ActivityStore
import app.work.domain.NewActivity
import app.work.server.AppConfig

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
    val issueKey = varchar("jira_key", 40).nullable()
    val link = varchar("link", 2_000).nullable()
    val onDate = varchar("on_date", 10).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

class ExposedActivityStore(private val database: Database) : ActivityStore {
    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(ActivityTable)
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
            it[issueKey] = draft.issueKey
            it[link] = draft.link
            it[onDate] = draft.onDate?.toString()
            it[ActivityTable.createdAt] = createdAt.toEpochMilliseconds()
        }
        Activity(
            id = id,
            summary = draft.summary,
            kind = draft.kind,
            issueKey = draft.issueKey,
            link = draft.link,
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
            .map { it.toActivity() }
    }

    override suspend fun findBetween(kind: ActivityKind, from: LocalDate, to: LocalDate): List<Activity> = db {
        if (to < from) return@db emptyList()
        ActivityTable.selectAll()
            .where {
                (ActivityTable.kind eq kind.name) and
                    (ActivityTable.onDate greaterEq from.toString()) and
                    (ActivityTable.onDate lessEq to.toString())
            }
            .orderBy(ActivityTable.createdAt to SortOrder.ASC)
            .map { it.toActivity() }
    }

    override suspend fun list(kind: ActivityKind): List<Activity> = db {
        ActivityTable.selectAll()
            .where { ActivityTable.kind eq kind.name }
            .orderBy(ActivityTable.createdAt to SortOrder.ASC)
            .map { it.toActivity() }
    }

    override suspend fun search(query: String): List<Activity> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return db {
            ActivityTable.selectAll()
                .orderBy(ActivityTable.createdAt to SortOrder.ASC)
                .map { it.toActivity() }
                .filter { activity ->
                    activity.summary.lowercase().contains(needle) ||
                        activity.issueKey?.lowercase()?.contains(needle) == true ||
                        activity.link?.lowercase()?.contains(needle) == true
                }
        }
    }

    private suspend fun <T> db(block: () -> T): T = withContext(Dispatchers.IO) {
        transaction(database) { block() }
    }
}

private fun ResultRow.toActivity() = Activity(
    id = this[ActivityTable.id],
    summary = this[ActivityTable.summary],
    kind = ActivityKind.valueOf(this[ActivityTable.kind]),
    issueKey = this[ActivityTable.issueKey],
    link = this[ActivityTable.link],
    onDate = this[ActivityTable.onDate]?.let(LocalDate::parse),
    createdAt = Instant.fromEpochMilliseconds(this[ActivityTable.createdAt]),
)
