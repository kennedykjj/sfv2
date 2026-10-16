package sfv2.daemon

import sfv2.core.Card
import sfv2.core.Column

/** The same card in another column: the title and every external reference stay. */
fun Card.movedTo(column: Column): Card =
    references.fold(Card.create(title, column)) { acc, ref -> acc.withReference(ref) }
