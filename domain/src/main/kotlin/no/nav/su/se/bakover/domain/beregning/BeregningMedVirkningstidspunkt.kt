package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.PeriodisertFradrag
import org.jetbrains.kotlin.backend.common.push
import java.util.Stack
import java.util.UUID

data class BeregningMedVirkningstidspunkt(
    private val id: UUID = UUID.randomUUID(),
    private val opprettet: Tidspunkt = Tidspunkt.now(),
    override val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>,
    private val fradragStrategy: FradragStrategy,
    private val begrunnelse: String? = null,
    private val utgangspunkt: Månedsberegning?,
) : Beregning {
    private val beregning = beregn()

    init {
        require(fradrag.all { periode inneholder it.periode })
        require(
            if (utgangspunkt != null)
                utgangspunkt.periode == periode.månedenFør()
            else true,
        )
    }

    override fun getId(): UUID = id

    override fun getOpprettet(): Tidspunkt = opprettet

    override fun getSumYtelse() = beregning.values
        .sumOf { it.getSumYtelse() }

    override fun getSumFradrag() = beregning.values
        .sumOf { it.getSumFradrag() }

    override fun getFradragStrategyName(): FradragStrategyName = fradragStrategy.getName()

    private fun beregn(): Map<Periode, Månedsberegning> {
        val beregnetPeriodisertFradrag = fradragStrategy.beregn(fradrag, periode)

        val gjeldendeBeregninger = Stack<PeriodisertBeregning>()

        if (utgangspunkt != null) {
            val periodisert = PeriodisertBeregning(
                periode = utgangspunkt.periode,
                sats = utgangspunkt.getSats(),
                fradrag = utgangspunkt.getFradrag().map {
                    PeriodisertFradrag(
                        type = it.fradragstype,
                        månedsbeløp = it.månedsbeløp,
                        periode = it.periode,
                        utenlandskInntekt = it.utenlandskInntekt,
                        tilhører = it.tilhører,
                    )
                },
                fribeløpForEps = utgangspunkt.getFribeløpForEps(),
            )

            gjeldendeBeregninger.push(periodisert)
        }

        val resultat = mutableListOf<Månedsberegning>()

        periode.tilMånedsperioder().forEach { next ->

            val inneværendeMåned = PeriodisertBeregning(
                periode = next,
                sats = sats,
                fradrag = beregnetPeriodisertFradrag[next] ?: emptyList(),
                fribeløpForEps = fradragStrategy.getEpsFribeløp(next),
            ).let { månedsberegning ->
                when (månedsberegning.ytelseStørreEnn0MenMindreEnnToProsentAvHøySats()) {
                    true -> PeriodisertBeregning(
                        periode = månedsberegning.periode,
                        sats = sats,
                        fradrag = månedsberegning.getFradrag()
                            .plus(månedsberegning.lagFradragForBeløpUnderMinstegrense()),
                    )
                    false -> månedsberegning
                }
            }

            if (gjeldendeBeregninger.isEmpty()) {
                inneværendeMåned.let {
                    it.getMerknader().leggTil(
                        Merknad.NyYtelse.from(
                            inneværendeMåned,
                        ),
                    )
                    gjeldendeBeregninger.push(it)
                    resultat.push(it)
                }
            } else {
                val gjeldendeBeregning = gjeldendeBeregninger.pop()

                when {
                    inneværendeMåned.getSumYtelse() prosentEndringFra gjeldendeBeregning.getSumYtelse() <= -10.0 -> {
                        /**
                         * Gjeldende beregning videreføres for inneværendene måned. Siden gjeldende beregning er gjort
                         * for forrige måned, må denne forskyves en måned fram.
                         *
                         * Ved forskyvning vil gjeldende beregning utføres på nytt med grunnbeløp for måneden etter,
                         * noe som fører til at g-regulering implisitt er fritatt for 10% sjekk.
                         */
                        gjeldendeBeregning.forskyv(1, fradragStrategy).let { gjeldendeForskøvet ->
                            gjeldendeForskøvet.getMerknader().leggTil(
                                Merknad.RedusertYtelse.from(
                                    benyttetBeregning = gjeldendeForskøvet,
                                    forkastetBeregning = inneværendeMåned,
                                ),
                            )
                            resultat.push(gjeldendeForskøvet)
                            /**
                             * Den ubenyttede beregningen for inneværende måned skal ikke tre i kraft før tidligst påfølgende
                             * måned og settes til ny gjeldende beregning. Påfølgende måned vil sammenlignes mot denne.
                             */
                            gjeldendeBeregninger.push(inneværendeMåned)
                        }
                    }
                    inneværendeMåned.getSumYtelse() prosentEndringFra gjeldendeBeregning.getSumYtelse() >= 10.0 -> {
                        /**
                         * Beregningen for inneværende måned skal tre i kraft umiddelbart og legges til sluttresultat
                         * og settes som ny gjeldende beregning.
                         *
                         * Inneværende måned vil være beregnet med korrekt grunnbeløp, og dermed trengs det ikke noen
                         * spesiell håndtering av g-regulering.
                         */
                        gjeldendeBeregning.forskyv(1, fradragStrategy).let { gjeldendeForskøvet ->
                            inneværendeMåned.let { inneværende ->
                                inneværende.getMerknader().leggTil(
                                    Merknad.ØktYtelse.from(
                                        benyttetBeregning = inneværende,
                                        forkastetBeregning = gjeldendeForskøvet,
                                    ),
                                )
                                resultat.push(inneværende)
                                gjeldendeBeregninger.push(inneværende)
                            }
                        }
                    }
                    else -> {
                        /**
                         * I tilfeller hvor endring i ytelse er mindre enn 10%, videreføres gjeldende beregning.
                         * Den videreførte beregningen settes også til ny gjeldende beregning. Siden gjeldende beregning
                         * er gjort for forrige måned, må denne forskyves en måned fram.
                         *
                         * Ved forskyvning vil gjeldende beregning utføres på nytt med grunnbeløp for måneden etter,
                         * noe som fører til at g-regulering implisitt er fritatt for 10% sjekk.
                         */
                        gjeldendeBeregning.forskyv(1, fradragStrategy).let { gjeldendeForskøvet ->
                            if (gjeldendeForskøvet != inneværendeMåned) { // Ikke lag merknad hvis helt like
                                gjeldendeForskøvet.getMerknader().leggTil(
                                    Merknad.EndringUnderTiProsent.from(
                                        benyttetBeregning = gjeldendeForskøvet,
                                        forkastetBeregning = inneværendeMåned,
                                    ),
                                )
                            }
                            resultat.push(gjeldendeForskøvet)
                            gjeldendeBeregninger.push(gjeldendeForskøvet)
                        }
                    }
                }
            }
        }

        return resultat.associateBy { it.periode }
    }

    private fun Månedsberegning.lagFradragForBeløpUnderMinstegrense() = PeriodisertFradrag(
        type = Fradragstype.UnderMinstenivå,
        månedsbeløp = getSumYtelse().toDouble(),
        periode = periode,
        utenlandskInntekt = null,
        tilhører = FradragTilhører.BRUKER,
    )

    private fun Månedsberegning.ytelseStørreEnn0MenMindreEnnToProsentAvHøySats() =
        getSumYtelse() > 0 && getSumYtelse() < Sats.toProsentAvHøy(periode)

    override fun getSats(): Sats = sats
    override fun getMånedsberegninger(): List<Månedsberegning> = beregning.values.toList()
    override fun getFradrag(): List<Fradrag> = fradrag
    override fun getBegrunnelse(): String? = begrunnelse
    override fun equals(other: Any?) = (other as? Beregning)?.let { this.equals(other) } ?: false
}

/**
 * Bestem endring i prosent fra [other] til [this].
 * Implementasjonen understøtter behovet [BeregningMedVirkningstidspunkt] for å avgjøre om noe har endret seg mer eller
 * mindre enn 10%, og har følgelig tatt seg noen friheter når det kommer til matematikkens lover.
 *
 * Merk spesielt følgende friheter:
 * [this] = 0 og [other] = 0 -> 0% endring (ingen endring i beløpet)
 * [this] = 0 og [other] != 0 -> -100% endring (sørger for at beregningen håndterer dette som en reduksjon på mer enn 10%)
 * [this] != 0 og [other] = 0 -> 100% endring (sørger for at beregningen håndterer dette som en økning på mer enn 10%)
 */
internal infix fun Int.prosentEndringFra(other: Int): Double {
    return when {
        this == 0 && other == 0 -> 0.0
        this == 0 && other != 0 -> -100.0
        other == 0 && this != 0 -> 100.0
        else -> (this - other) / (1.0 * other) * 100
    }
}
