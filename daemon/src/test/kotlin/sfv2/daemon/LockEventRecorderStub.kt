package sfv2.daemon

import java.time.Clock
import sfv2.core.Card
import sfv2.core.Event
import sfv2.store.CardEventStore
import sfv2.store.StoredEvent

// shipfloor:qa-stub - exists only so the red gate compiles; the implementer deletes this file
// and adds the real LockEventRecorder and RunPool overload in production code.
interface LockEventRecorder {
    fun card(id: String): Card?
    fun recordChange(id: String, card: Card, event: Event): Long
}

fun RunPool(
    capacity: Int,
    recorder: LockEventRecorder,
    clock: Clock,
    probe: ProcessProbe,
    log: DaemonLog,
    onEvent: (StoredEvent) -> Unit,
): RunPool {
    val field = recorder.javaClass.getDeclaredField("store").apply { isAccessible = true }
    return RunPool(capacity, field.get(recorder) as CardEventStore, clock, probe, log, onEvent)
}
