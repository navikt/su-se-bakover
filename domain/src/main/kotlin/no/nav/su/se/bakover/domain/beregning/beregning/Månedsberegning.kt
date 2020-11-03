package no.nav.su.se.bakover.domain.beregning.beregning

import no.nav.su.se.bakover.common.limitedUpwardsTo
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.common.positiveOrZero
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.IFradrag

interface IMånedsberegning : PeriodisertInformasjon {
    fun sum(): Double
    fun fradrag(): Double
    fun grunnbeløp(): Int
    fun sats(): Sats
    fun getSatsbeløp(): Double
}

internal data class Månedsberegning(
    private val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<IFradrag>
) : IMånedsberegning {
    init {
        require(fradrag.all { it.periode() == periode }) { "Fradrag må være gjeldende for aktuell måned" }
        require(periode.antallMåneder() == 1) { "Månedsberegning kan kun utføres for en enkelt måned" }
    }

    override fun sum() = (getSatsbeløp() - fradrag())
        .positiveOrZero()

    override fun fradrag() = fradrag
        .sumByDouble { it.månedsbeløp() }
        .limitedUpwardsTo(getSatsbeløp())

    override fun grunnbeløp(): Int = Grunnbeløp.`1G`.fraDato(periode.fraOgMed()).toInt()
    override fun sats(): Sats = sats
    override fun getSatsbeløp(): Double = sats.periodiser(periode).getValue(periode)

    override fun periode(): Periode = periode
}
