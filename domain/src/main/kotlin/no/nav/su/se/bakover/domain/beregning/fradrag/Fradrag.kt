package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Copyable

interface Fradrag : PeriodisertInformasjon, Copyable<CopyArgs.Snitt, Fradrag?> {
    val fradragstype: Fradragstype
    val månedsbeløp: Double
    val utenlandskInntekt: UtenlandskInntekt? // TODO can we pls do something about this one?
    val tilhører: FradragTilhører

    fun tilhørerEps(): Boolean {
        return tilhører == FradragTilhører.EPS
    }
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

fun List<Fradrag>.harFradragSomTilhørerEps(): Boolean {
    return this.any { it.tilhørerEps() }
}

fun List<Fradrag>.utenSosialstønad(): List<Fradrag> = filterNot { it.fradragstype === Fradragstype.Sosialstønad }
