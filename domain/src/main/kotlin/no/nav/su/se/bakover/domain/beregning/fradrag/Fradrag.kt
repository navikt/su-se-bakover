package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.application.KopierbarForSnitt
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon

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

    fun skalJusteresVedGEndring() = fradragstype.måJusteresManueltVedGEndring
}

enum class FradragTilhører {
    BRUKER,
    EPS,
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

fun List<Fradrag>.sum(): Double {
    return sumOf { it.månedsbeløp }
}
