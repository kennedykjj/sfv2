package sfv2.store

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Path
import sfv2.store.db.Sfv2Database

/** The factory's SQLite database, versioned by `PRAGMA user_version`. */
class Database private constructor(private val driver: SqlDriver) : AutoCloseable {
    val queries: Sfv2Database = Sfv2Database(driver)

    override fun close() = driver.close()

    companion object {
        const val SCHEMA_VERSION = 1

        fun open(path: Path): Database {
            val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
            try {
                // The version is read before any write PRAGMA: WAL changes the file header.
                val found = driver.readInt("PRAGMA user_version")
                if (found > SCHEMA_VERSION) throw DatabaseSchemaTooNewException(found, SCHEMA_VERSION, path)
                if (found == 0) {
                    Sfv2Database.Schema.create(driver)
                    driver.execute(null, "PRAGMA user_version = $SCHEMA_VERSION", 0)
                } else if (found < SCHEMA_VERSION) {
                    Sfv2Database.Schema.migrate(driver, found.toLong(), SCHEMA_VERSION.toLong())
                    driver.execute(null, "PRAGMA user_version = $SCHEMA_VERSION", 0)
                }
                driver.readInt("PRAGMA journal_mode=WAL")
            } catch (e: Throwable) {
                driver.close()
                throw e
            }
            return Database(driver)
        }

        private fun SqlDriver.readInt(sql: String): Int =
            executeQuery(null, sql, { cursor: SqlCursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            }, 0).value.toInt()
    }
}
