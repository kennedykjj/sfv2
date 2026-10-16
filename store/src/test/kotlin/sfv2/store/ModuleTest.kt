package sfv2.store

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sfv2.core.TextHash

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
    fun `AC-1 creates a new database at schema version 2 in WAL mode`() {
        val file = dir.resolve("fresh.db")

        Database.open(file).close()

        assertThat(Files.exists(file)).isTrue()
        assertThat(Database.SCHEMA_VERSION).isEqualTo(2)
        assertThat(scalar(file, "PRAGMA user_version")).isEqualTo("2")
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
        assertThat(scalar(file, "PRAGMA user_version")).isEqualTo("2")
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

    @Test
    fun `AC-1 spec write puts the file at the card path and records path and hash`() {
        val specsDir = dir.resolve("specs")
        val db = dir.resolve("spec1.db")
        val text = "# Spec\n\nconteúdo ✓\n"

        Database.open(db).use { SpecStore(it, specsDir).write("card-1", text) }

        val path = specsDir.resolve("card-1.md")
        assertThat(Files.readString(path)).isEqualTo(text)
        assertThat(scalar(db, "SELECT path FROM spec WHERE card_id = 'card-1'")).isEqualTo(path.toString())
        assertThat(scalar(db, "SELECT hash FROM spec WHERE card_id = 'card-1'")).isEqualTo(TextHash.of(text).value)
    }

    @Test
    fun `AC-2 spec read reports divergence when the file is altered or removed`() {
        val specsDir = dir.resolve("specs")
        Database.open(dir.resolve("spec2.db")).use { database ->
            val store = SpecStore(database, specsDir)
            val text = "approved text"
            store.write("card-2", text)
            assertThat(store.read("card-2")).isEqualTo(SpecRead.Valid(text, TextHash.of(text)))
            val path = specsDir.resolve("card-2.md")

            Files.writeString(path, "edited outside")
            assertThat(store.read("card-2"))
                .isEqualTo(SpecRead.Diverged(path, TextHash.of(text), TextHash.of("edited outside")))

            Files.delete(path)
            assertThat(store.read("card-2")).isEqualTo(SpecRead.Diverged(path, TextHash.of(text), null))
        }
    }

    @Test
    fun `AC-3 spec read of a card without spec says not registered`() {
        Database.open(dir.resolve("spec3.db")).use { database ->
            assertThat(SpecStore(database, dir.resolve("specs")).read("card-3"))
                .isEqualTo(SpecRead.NotRegistered("card-3"))
        }
    }
}
