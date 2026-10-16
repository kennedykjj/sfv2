package sfv2.store

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
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

    private fun anEvent(id: String, type: EventType = EventType.CARD_CREATED, at: String = "2026-10-15T10:00:00Z") =
        Event(EventId(id), Instant.parse(at), Actor.Owner, type, mapOf("k" to "v"))

    private fun withStore(block: (CardEventStore, Path) -> Unit) {
        val file = dir.resolve("events.db")
        Database.open(file).use { db -> block(CardEventStore(db), file) }
    }

    @Test
    fun `AC-1 records card and event together`() = withStore { store, _ ->
        val card = Card.create("Write docs", Column.INBOX)
        val event = anEvent("e1")

        store.recordChange("c1", card, event)

        assertThat(store.card("c1")).isEqualTo(card)
        assertThat(store.events("c1")).containsExactly(event)
    }

    @Test
    fun `AC-1 later change replaces the card and appends an event`() = withStore { store, _ ->
        val first = anEvent("e1")
        val second = anEvent("e2", EventType.CARD_MOVED, "2026-10-15T11:00:00Z")
        val moved = Card.create("Write docs", Column.READY_FOR_REFINEMENT)
        store.recordChange("c1", Card.create("Write docs", Column.INBOX), first)

        store.recordChange("c1", moved, second)

        assertThat(store.card("c1")).isEqualTo(moved)
        assertThat(store.events("c1")).containsExactly(first, second)
    }

    @Test
    fun `AC-2 failed event write leaves card as it was and no new event`() = withStore { store, _ ->
        val original = Card.create("Write docs", Column.INBOX)
        val first = anEvent("e1")
        store.recordChange("c1", original, first)

        assertThatThrownBy {
            store.recordChange("c1", Card.create("Write docs", Column.CODING), anEvent("e1", EventType.CARD_MOVED))
        }.isInstanceOf(Exception::class.java)

        assertThat(store.card("c1")).isEqualTo(original)
        assertThat(store.events("c1")).containsExactly(first)
    }

    @Test
    fun `AC-3 failed card write leaves card as it was and no new event`() = withStore { store, file ->
        val original = Card.create("Write docs", Column.INBOX)
        val first = anEvent("e1")
        store.recordChange("c1", original, first)
        jdbc(file) { c ->
            c.createStatement().use { s ->
                s.execute("CREATE TRIGGER fail_card_insert BEFORE INSERT ON card BEGIN SELECT RAISE(ABORT, 'card write failed'); END")
                s.execute("CREATE TRIGGER fail_card_update BEFORE UPDATE ON card BEGIN SELECT RAISE(ABORT, 'card write failed'); END")
            }
        }

        assertThatThrownBy {
            store.recordChange("c1", Card.create("Write docs", Column.CODING), anEvent("e2", EventType.CARD_MOVED))
        }.isInstanceOf(Exception::class.java)

        assertThat(store.card("c1")).isEqualTo(original)
        assertThat(store.events("c1")).containsExactly(first)
    }

    @Test
    fun `AC-4 recorded event stays identical after later changes and failed attempts`() = withStore { store, _ ->
        val first = anEvent("e1")
        store.recordChange("c1", Card.create("Write docs", Column.INBOX), first)

        store.recordChange("c1", Card.create("Write docs", Column.CODING), anEvent("e2", EventType.CARD_MOVED, "2026-10-15T11:00:00Z"))
        assertThatThrownBy {
            store.recordChange("c1", Card.create("Other", Column.INBOX), anEvent("e1", EventType.OWNER_APPROVED))
        }.isInstanceOf(Exception::class.java)

        assertThat(store.events("c1").first { it.id == first.id }).isEqualTo(first)
        assertThat(store.events("c1")).hasSize(2)
    }

    @Test
    fun `AC-5 public API has no way to alter or delete an event`() {
        val names = CardEventStore::class.java.methods
            .filter { it.declaringClass == CardEventStore::class.java }
            .map { it.name }

        assertThat(names).contains("recordChange", "events", "card")
        assertThat(names).noneMatch { n ->
            listOf("update", "delete", "alter", "remove", "set", "edit", "replace").any { n.lowercase().startsWith(it) }
        }
    }
}
