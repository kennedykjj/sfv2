package sfv2.store

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ModuleTest {
    @TempDir
    lateinit var dir: Path

    private fun <T> jdbc(path: Path, block: (Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:$path").use(block)

    private fun scalar(path: Path, sql: String): String =
        jdbc(path) { c -> c.createStatement().use { s -> s.executeQuery(sql).use { rs -> rs.next(); rs.getString(1) } } }

    private fun newerDatabase(version: Int): Path {
        val file = dir.resolve("newer.db")
        jdbc(file) { c ->
            c.createStatement().use { s ->
                s.execute("CREATE TABLE future_table (id INTEGER PRIMARY KEY, note TEXT)")
                s.execute("INSERT INTO future_table (note) VALUES ('from the future')")
                s.execute("PRAGMA user_version = $version")
            }
        }
        return file
    }

    @Test
    fun `module is named after its Gradle project`() {
        assertThat(MODULE_NAME).isEqualTo("store")
    }

    @Test
    fun `AC-1 creates a new database at schema version 1 in WAL mode`() {
        val file = dir.resolve("fresh.db")

        Database.open(file).close()

        assertThat(Files.exists(file)).isTrue()
        assertThat(Database.SCHEMA_VERSION).isEqualTo(1)
        assertThat(scalar(file, "PRAGMA user_version")).isEqualTo("1")
        assertThat(scalar(file, "PRAGMA journal_mode")).isEqualTo("wal")
        val tables = jdbc(file) { c ->
            c.createStatement().use { s ->
                s.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { rs ->
                    generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
                }
            }
        }
        assertThat(tables).contains("card", "event")
    }

    @Test
    fun `AC-2 reopening a current database keeps schema and data identical`() {
        val file = dir.resolve("current.db")
        Database.open(file).close()
        jdbc(file) { c ->
            c.createStatement().use { s ->
                s.execute("CREATE TABLE probe (id INTEGER PRIMARY KEY, note TEXT)")
                s.execute("INSERT INTO probe (note) VALUES ('kept')")
            }
        }
        val schemaBefore = scalar(file, "SELECT group_concat(sql, ';') FROM (SELECT sql FROM sqlite_master ORDER BY name)")

        Database.open(file).close()

        assertThat(scalar(file, "SELECT group_concat(sql, ';') FROM (SELECT sql FROM sqlite_master ORDER BY name)"))
            .isEqualTo(schemaBefore)
        assertThat(scalar(file, "SELECT note FROM probe")).isEqualTo("kept")
        assertThat(scalar(file, "PRAGMA user_version")).isEqualTo("1")
        assertThat(scalar(file, "PRAGMA journal_mode")).isEqualTo("wal")
    }

    @Test
    fun `AC-3 fails on a newer database citing found and supported versions`() {
        val found = Database.SCHEMA_VERSION + 6
        val file = newerDatabase(found)

        assertThatThrownBy { Database.open(file) }
            .isInstanceOf(DatabaseSchemaTooNewException::class.java)
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining(found.toString())
            .hasMessageContaining(Database.SCHEMA_VERSION.toString())
            .hasMessageContaining(file.toString())
    }

    @Test
    fun `AC-4 leaves a newer database byte for byte unchanged when open fails`() {
        val file = newerDatabase(Database.SCHEMA_VERSION + 1)
        val before = Files.readAllBytes(file)
        val filesBefore = Files.list(dir).use { it.map { p -> p.fileName.toString() }.sorted().toList() }

        assertThatThrownBy { Database.open(file) }
            .isInstanceOf(DatabaseSchemaTooNewException::class.java)

        assertThat(Files.readAllBytes(file)).isEqualTo(before)
        assertThat(Files.list(dir).use { it.map { p -> p.fileName.toString() }.sorted().toList() })
            .isEqualTo(filesBefore)
    }
}
