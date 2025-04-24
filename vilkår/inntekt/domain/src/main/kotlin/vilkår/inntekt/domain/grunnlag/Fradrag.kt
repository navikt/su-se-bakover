package vilkår.inntekt.domain.grunnlag

import no.nav.su.se.bakover.common.KopierbarForSnitt
import no.nav.su.se.bakover.common.tid.periode.PeriodisertInformasjon
import java.math.BigDecimal

/**
 * TODO: Dette burde egentlig vært et beregningsfradrag, mens Fradragsgrunnlag ikke refererer til denne.
 */
sealed interface Fradrag :
    PeriodisertInformasjon,
    KopierbarForSnitt<Fradrag?> {
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

    fun oppdaterBeløp(beløp: BigDecimal): Fradrag
}

enum class FradragTilhører {
    BRUKER,
    EPS,
}

fun List<Fradrag>.utenSosialstønad(): List<Fradrag> =
    filterNot { it.fradragstype == Fradragstype.Sosialstønad }

fun List<Fradrag>.sum(type: Fradragstype): Double {
    return filter { it.fradragstype == type }.sumOf { it.månedsbeløp }
}

fun List<Fradrag>.sumEksklusiv(type: Fradragstype): Double {
    return filterNot { it.fradragstype == type }.sumOf { it.månedsbeløp }
}

fun List<Fradrag>.sum(): Double {
    return sumOf { it.månedsbeløp }
}
