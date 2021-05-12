package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje

interface Fradrag : KanPlasseresPåTidslinje<Fradrag> {
    val fradragstype: Fradragstype
    val månedsbeløp: Double
    val utenlandskInntekt: UtenlandskInntekt? // TODO can we pls do something about this one?
    val tilhører: FradragTilhører
    override val periode: Periode
    override val opprettet: Tidspunkt
}

enum class FradragTilhører {
    BRUKER,
    EPS;

    companion object {
        fun tryParse(value: String): Either<UgyldigFradragTilhører, Fradragstype> {
            return Fradragstype.values().firstOrNull { it.name == value }?.right() ?: UgyldigFradragTilhører.left()
        }
    }

    object UgyldigFradragTilhører
}
