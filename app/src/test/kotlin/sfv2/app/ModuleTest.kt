package sfv2.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ModuleTest {
    @Test
    fun `module is named after its Gradle project`() {
        assertThat(MODULE_NAME).isEqualTo("app")
    }

    private class RecordingSkin(override val id: String) : AssistantSkin {
        val shown = java.util.concurrent.CopyOnWriteArrayList<List<String>>()

        override fun show(history: List<String>) {
            shown.add(history.toList())
        }
    }

    @Test
    fun `AC-1 window shows only the last lines, oldest first, long phrases cut`() {
        val skin = TerminalSkin(maxLines = 3, maxColumns = 10)

        val window = skin.window(listOf("one", "two", "three", "four", "a very long phrase indeed"))

        assertThat(window.lines).containsExactly("> three", "> four", "> a very …")
        assertThat(window.lines.all { it.codePointCount(0, it.length) <= 10 }).isTrue()
        assertThat(skin.window(emptyList()).lines).containsExactly("> aguardando…")
    }

    @Test
    fun `AC-1 constructor rejects invalid sizes`() {
        assertThrows<IllegalArgumentException> { TerminalSkin(maxLines = 0, maxColumns = 40) }
        assertThrows<IllegalArgumentException> { TerminalSkin(maxLines = 5, maxColumns = 3) }
    }

    @Test
    fun `AC-2 activating another skin switches without restart and hands over the history`() {
        val terminal = RecordingSkin("terminal")
        val other = RecordingSkin("other")
        val registry = SkinRegistry(listOf(terminal, other), initial = "terminal")
        registry.publish("first")
        registry.publish("second")

        registry.activate("other")

        assertThat(registry.active).isSameAs(other)
        assertThat(other.shown.last()).containsExactly("first", "second")
        val error = assertThrows<UnknownSkinException> { registry.activate("nope") }
        assertThat(error.message).contains("nope")
        assertThat(registry.active).isSameAs(other)
    }

    @Test
    fun `AC-3 only the active skin receives a new phrase as the most recent`() {
        val terminal = TerminalSkin(maxLines = 2, maxColumns = 40)
        val other = RecordingSkin("other")
        val registry = SkinRegistry(listOf(terminal, other), initial = terminal.id)

        registry.publish("old")
        registry.publish("fresh")

        assertThat(other.shown).isEmpty()
        assertThat(terminal.current.lines).containsExactly("> old", "> fresh")
        assertThat(registry.history).containsExactly("old", "fresh")
    }

    @Test
    fun `AC-4 controls become spaces and the cut never splits a surrogate pair`() {
        val skin = TerminalSkin(maxLines = 5, maxColumns = 6)
        val registry = SkinRegistry(listOf(skin), initial = skin.id)

        registry.publish("a\nb\tc")
        registry.publish("ab😀😀😀😀")

        assertThat(registry.history).containsExactly("a b c", "ab😀😀😀😀")
        assertThat(skin.current.lines).containsExactly("> a b…", "> ab😀…")
        val cut = skin.window(listOf("ab😀😀😀😀")).lines.single()
        assertThat(cut.codePoints().toArray().none { it == 0xFFFD }).isTrue()
        assertThat(cut.length).isEqualTo(cut.toCharArray().size)
        assertThat(cut.indices.none { i ->
            Character.isHighSurrogate(cut[i]) != (i + 1 < cut.length && Character.isLowSurrogate(cut[i + 1])) &&
                Character.isHighSurrogate(cut[i])
        }).isTrue()
    }

    @Test
    fun `AC-5 concurrent publish and activate lose no phrase and show whole histories`() {
        val a = RecordingSkin("a")
        val b = RecordingSkin("b")
        val registry = SkinRegistry(listOf(a, b), initial = "a")
        val start = java.util.concurrent.CountDownLatch(1)
        val publishers = (0 until 4).map { t ->
            Thread {
                start.await()
                repeat(20) { registry.publish("t$t-$it") }
            }
        }
        val switcher = Thread {
            start.await()
            repeat(50) { registry.activate(if (it % 2 == 0) "b" else "a") }
        }
        val all = publishers + switcher
        all.forEach { it.start() }
        start.countDown()
        all.forEach { it.join() }

        assertThat(registry.history).hasSize(80).doesNotHaveDuplicates()
        val shown = a.shown + b.shown
        assertThat(shown).isNotEmpty()
        shown.forEach { snapshot -> assertThat(snapshot).doesNotHaveDuplicates() }
        assertThat(registry.active.id).isIn("a", "b")
    }

    @Test
    fun `AC-5 history keeps at most the last 100 phrases and returns a copy`() {
        val skin = RecordingSkin("a")
        val registry = SkinRegistry(listOf(skin), initial = "a")
        repeat(120) { registry.publish("p$it") }

        val copy = registry.history

        assertThat(copy).hasSize(100)
        assertThat(copy.first()).isEqualTo("p20")
        assertThat(copy.last()).isEqualTo("p119")
        registry.publish("more")
        assertThat(copy).hasSize(100).doesNotContain("more")
    }
}
