package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.KopierbarForSnitt

interface Fradrag : PeriodisertInformasjon, KopierbarForSnitt<Fradrag?> {
    val fradragskategoriWrapper: FradragskategoriWrapper
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
}

fun List<Fradrag>.utenSosialstønad(): List<Fradrag> =
    filterNot { it.fradragskategoriWrapper.kategori === Fradragskategori.Sosialstønad }

fun List<Fradrag>.utenAvkorting(): List<Fradrag> =
    filterNot { it.fradragskategoriWrapper.kategori === Fradragskategori.AvkortingUtenlandsopphold }

fun List<Fradrag>.sum(type: FradragskategoriWrapper): Double {
    return filter { it.fradragskategoriWrapper == type }.sumOf { it.månedsbeløp }
}

fun List<Fradrag>.sumEksklusiv(type: FradragskategoriWrapper): Double {
    return filterNot { it.fradragskategoriWrapper == type }.sumOf { it.månedsbeløp }
}
