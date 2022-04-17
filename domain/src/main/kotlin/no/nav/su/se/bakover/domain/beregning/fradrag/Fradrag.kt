package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.KopierbarForSnitt
import no.nav.su.se.bakover.domain.KopierbarForTidslinje
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.tidslinje.KanPeriodiseresInternt

interface Fradrag : PeriodisertInformasjon, KopierbarForSnitt<Fradrag?> {
    val fradragstype: Fradragstype
    val månedsbeløp: Double
    val utenlandskInntekt: UtenlandskInntekt? // TODO can we pls do something about this one?
    val tilhører: FradragTilhører

    fun tilhørerBruker(): Boolean {
        return tilhører == FradragTilhører.BRUKER
    }

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

fun List<Fradrag>.utenSosialstønad(): List<Fradrag> =
    filterNot { it.fradragstype === Fradragstype.Sosialstønad }

fun List<Fradrag>.utenAvkorting(): List<Fradrag> =
    filterNot { it.fradragstype === Fradragstype.AvkortingUtenlandsopphold }

fun List<Fradrag>.sum(type: Fradragstype): Double {
    return filter { it.fradragstype == type }.sumOf { it.månedsbeløp }
}

fun List<Fradrag>.sumEksklusiv(type: Fradragstype): Double {
    return filterNot { it.fradragstype == type }.sumOf { it.månedsbeløp }
}
