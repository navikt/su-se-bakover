package økonomi.domain.simulering

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.KlasseType
import java.lang.Integer.max
import java.time.LocalDate

internal data class TolketSimulering(
    private val simulering: Simulering,
) {
    private val tolketMåneder: NonEmptyList<TolketMåned> = simulering.måneder.map { it.tolk() }.also {
        require(it.isNotEmpty()) { "Skal alltid være minst 1 periode" }
    }.toNonEmptyList()

    private fun hentMånederMedUtbetalinger(): List<TolketMånedMedUtbetalinger> {
        return tolketMåneder.filterIsInstance<TolketMånedMedUtbetalinger>()
    }

    val periode = tolketMåneder.map { it.måned }.minAndMaxOf()

    val månederMedSimuleringsresultat = hentMånederMedUtbetalinger().map { it.måned }

    fun harFeilutbetalinger() = hentFeilutbetalteBeløp().sum() > 0

    fun harFeilutbetalinger(periode: Periode): Boolean {
        return hentFeilutbetalteBeløp().filter { periode.overlapper(it.periode) }.sumOf { it.beløp.sum() } > 0
    }

    /**
     * Sjekk for spesialtilfelle hvor vi har mottatt tom respons (indikerer ingen posteringer/utbetalinger).
     * Her vil [no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringResponseMapper] lage en "fiktiv" periode
     * satt til simuleringsperioden some er brukt, men denne vil ikke inneholde noen utbetalinger.
     * OBS - i motsetning til perioder med utbetalinger vil denne perioden kunne være lenger enn 1 mnd.
     */
    fun erAlleMånederUtenUtbetaling(): Boolean {
        return tolketMåneder.all { it is TolketMånedUtenUtbetalinger }
    }

    fun kontooppstilling(): Map<Periode, Kontooppstilling> {
        return if (erAlleMånederUtenUtbetaling()) {
            mapOf(periode to Kontooppstilling.EMPTY)
        } else {
            hentMånederMedUtbetalinger().associate { it.måned to it.kontooppstilling() }
        }
    }

    fun hentUtbetalteBeløp(): Månedsbeløp {
        return if (erAlleMånederUtenUtbetaling()) {
            Månedsbeløp(emptyList())
        } else {
            return Månedsbeløp(
                hentMånederMedUtbetalinger()
                    .map { it.hentUtbetaltBeløp() }
                    .filter { it.sum() > 0 },
            )
        }
    }

    fun hentFeilutbetalteBeløp(): Månedsbeløp {
        return if (erAlleMånederUtenUtbetaling()) {
            Månedsbeløp(emptyList())
        } else {
            Månedsbeløp(
                hentMånederMedUtbetalinger()
                    .map { it.hentFeilutbetalteBeløp() }
                    .filter { it.sum() > 0 },
            )
        }
    }

    fun hentTilUtbetaling(): Månedsbeløp {
        return if (erAlleMånederUtenUtbetaling()) {
            Månedsbeløp(emptyList())
        } else {
            Månedsbeløp(
                hentMånederMedUtbetalinger()
                    .map { it.hentTilUtbetaling() }
                    .filter { it.sum() > 0 },
            )
        }
    }

    fun hentTotalUtbetaling(): Månedsbeløp {
        if (erAlleMånederUtenUtbetaling()) return Månedsbeløp(emptyList())

        return Månedsbeløp(
            tolketMåneder.map { tolketMåned ->
                MånedBeløp(
                    periode = tolketMåned.måned,
                    beløp = when (tolketMåned) {
                        is TolketMånedMedUtbetalinger -> {
                            tolketMåned.utbetaling.ytelse.filter { it.erUtbetalingSomSimuleres }.let {
                                when (it.size) {
                                    0 -> Beløp.zero() // TODO jah: Hvorfor må dette være null?
                                    1 -> Beløp(it.first().beløp.beløp)
                                    else -> throw IllegalStateException("Fant flere YTEL uten tilbakeføring som var større enn 0, hadde typeSats MND, antallSats 1 og sats er lik beløp. Det skal ikke være tilfelle.")
                                }
                            }
                        }

                        is TolketMånedUtenUtbetalinger -> Beløp.zero()
                    },
                )
            },
        )
    }

    fun totalOppsummering(): PeriodeOppsummering {
        return periodeOppsummering().reduce { acc, periodeOppsummering ->
            acc.copy(
                periode = periode,
                sumTilUtbetaling = acc.sumTilUtbetaling + periodeOppsummering.sumTilUtbetaling,
                sumEtterbetaling = acc.sumEtterbetaling + periodeOppsummering.sumEtterbetaling,
                sumFramtidigUtbetaling = acc.sumFramtidigUtbetaling + periodeOppsummering.sumFramtidigUtbetaling,
                sumTotalUtbetaling = acc.sumTotalUtbetaling + periodeOppsummering.sumTotalUtbetaling,
                sumTidligereUtbetalt = acc.sumTidligereUtbetalt + periodeOppsummering.sumTidligereUtbetalt,
                sumFeilutbetaling = acc.sumFeilutbetaling + periodeOppsummering.sumFeilutbetaling,
                sumReduksjonFeilkonto = acc.sumReduksjonFeilkonto + periodeOppsummering.sumReduksjonFeilkonto,
            )
        }
    }

    fun periodeOppsummering(): List<PeriodeOppsummering> {
        return if (erAlleMånederUtenUtbetaling()) {
            listOf(
                PeriodeOppsummering(
                    periode = periode,
                    sumTilUtbetaling = 0,
                    sumEtterbetaling = 0,
                    sumFramtidigUtbetaling = 0,
                    sumTotalUtbetaling = 0,
                    sumTidligereUtbetalt = 0,
                    sumFeilutbetaling = 0,
                    sumReduksjonFeilkonto = 0,
                ),
            )
        } else {
            hentMånederMedUtbetalinger().map { it.oppsummering() }
        }
    }
}

internal sealed interface TolketMåned {
    val måned: Måned
    val utbetaling: TolketUtbetaling?
    fun kontooppstilling(): Kontooppstilling
}

internal data class TolketMånedUtenUtbetalinger(
    override val måned: Måned,
) : TolketMåned {
    override val utbetaling: TolketUtbetaling? = null
    override fun kontooppstilling(): Kontooppstilling = Kontooppstilling.EMPTY
}

internal data class TolketMånedMedUtbetalinger(
    override val måned: Måned,
    override val utbetaling: TolketUtbetaling,
) : TolketMåned {
    override fun kontooppstilling(): Kontooppstilling = utbetaling.kontoppstilling

    fun harFeilutbetalinger() = hentFeilutbetalteBeløp().sum() > 0
    fun hentUtbetaltBeløp(): MånedBeløp {
        return MånedBeløp(måned, Beløp(utbetaling.kontoppstilling.kreditYtelse.sum()))
    }

    fun hentFeilutbetalteBeløp(): MånedBeløp {
        return MånedBeløp(måned, Beløp(utbetaling.kontoppstilling.debetFeilkonto.sum()))
    }

    private fun hentReduksjonFeilkonto(): MånedBeløp {
        return MånedBeløp(måned, Beløp(utbetaling.kontoppstilling.kreditFeilkonto.sum()))
    }

    fun hentTilUtbetaling(): MånedBeløp {
        return MånedBeløp(måned, Beløp(max(utbetaling.kontoppstilling.sumUtbetaling.sum(), 0)))
    }

    private fun hentTotalUtbetaling(): MånedBeløp {
        return MånedBeløp(
            måned,
            Beløp(utbetaling.kontoppstilling.debetYtelse.sum() - utbetaling.kontoppstilling.debetFeilkonto.sum()),
        )
    }

    private fun hentEtterbetaling(): MånedBeløp {
        return if (måned.erForfalt()) {
            hentTilUtbetaling()
        } else {
            MånedBeløp(måned, Beløp.zero())
        }
    }

    private fun hentFramtidigUtbetaling(): MånedBeløp {
        return if (!måned.erForfalt()) {
            hentTilUtbetaling()
        } else {
            MånedBeløp(måned, Beløp.zero())
        }
    }

    fun oppsummering(): PeriodeOppsummering {
        return PeriodeOppsummering(
            periode = måned,
            sumTilUtbetaling = Månedsbeløp(månedbeløp = listOf(hentTilUtbetaling())).sum(),
            sumEtterbetaling = Månedsbeløp(månedbeløp = listOf(hentEtterbetaling())).sum(),
            sumFramtidigUtbetaling = Månedsbeløp(månedbeløp = listOf(hentFramtidigUtbetaling())).sum(),
            sumTotalUtbetaling = Månedsbeløp(månedbeløp = listOf(hentTotalUtbetaling())).sum(),
            sumTidligereUtbetalt = Månedsbeløp(månedbeløp = listOf(hentUtbetaltBeløp())).sum(),
            sumFeilutbetaling = Månedsbeløp(månedbeløp = listOf(hentFeilutbetalteBeløp())).sum(),
            sumReduksjonFeilkonto = Månedsbeløp(månedbeløp = listOf(hentReduksjonFeilkonto())).sum(),
        )
    }

    private fun Måned.erForfalt(): Boolean {
        return tilOgMed < utbetaling.forfall
    }
}

internal data class TolketUtbetaling(
    private val detaljer: List<TolketDetalj>,
    val forfall: LocalDate,
) {
    private val feilkonto: List<TolketDetalj.Feilkonto> = detaljer.filterIsInstance<TolketDetalj.Feilkonto>()
    private val motpostFeilkonto: List<TolketDetalj.MotpostFeilkonto> =
        detaljer.filterIsInstance<TolketDetalj.MotpostFeilkonto>()
    val ytelse: List<TolketDetalj.Ytelse> = detaljer.filterIsInstance<TolketDetalj.Ytelse>()

    val kontoppstilling = Kontooppstilling(
        debetYtelse = hentDebetYtelse(),
        kreditYtelse = hentKreditYtelse(),
        debetFeilkonto = hentDebetFeilkonto(),
        kreditFeilkonto = hentKreditFeilkonto(),
        debetMotpostFeilkonto = hentDebetMotpostFeilkonto(),
        kreditMotpostFeilkonto = hentKreditMotpostFeilkonto(),
    )

    private fun hentDebetYtelse(): Kontobeløp.Debet {
        return Kontobeløp.Debet(ytelse.filter { it.erDebet() }.sumOf { it.sum() })
    }

    private fun hentKreditYtelse(): Kontobeløp.Kredit {
        return Kontobeløp.Kredit(ytelse.filter { it.erKredit() }.sumOf { it.sum() })
    }

    @Suppress("unused")
    private fun hentUtbetalingSomSimuleres(): Kontobeløp.Debet {
        return Kontobeløp.Debet(ytelse.filter { it.erUtbetalingSomSimuleres && it.erDebet() }.sumOf { it.sum() })
    }

    private fun hentDebetFeilkonto(): Kontobeløp.Debet {
        return Kontobeløp.Debet(feilkonto.filter { it.erDebet() }.sumOf { it.sum() })
    }

    private fun hentKreditFeilkonto(): Kontobeløp.Kredit {
        return Kontobeløp.Kredit(feilkonto.filter { it.erKredit() }.sumOf { it.sum() })
    }

    private fun hentDebetMotpostFeilkonto(): Kontobeløp.Debet {
        return Kontobeløp.Debet(motpostFeilkonto.filter { it.erDebet() }.sumOf { it.sum() })
    }

    private fun hentKreditMotpostFeilkonto(): Kontobeløp.Kredit {
        return Kontobeløp.Kredit(motpostFeilkonto.filter { it.erKredit() }.sumOf { it.sum() })
    }
}

sealed interface TolketDetalj {
    val beløp: Kontobeløp
    val tilbakeføring: Boolean

    fun erDebet() = beløp is Kontobeløp.Debet
    fun erKredit() = beløp is Kontobeløp.Kredit
    fun sum() = beløp.sum()

    @Suppress("unused")
    fun erYtelse() = this is Ytelse

    companion object {
        private fun SimulertDetaljer.erUtbetalingSomSimuleres(): Boolean {
            return erYtelse() &&
                sats >= 0 &&
                belop == sats &&
                typeSats == "MND" &&
                antallSats == 1 &&
                !tilbakeforing
        }

        fun from(
            simulertDetaljer: SimulertDetaljer,
            log: Logger = LoggerFactory.getLogger(this::class.java),
        ): TolketDetalj? = when {
            simulertDetaljer.erFeilkonto() -> {
                Feilkonto(
                    beløp = Kontobeløp(simulertDetaljer.belop),
                    tilbakeføring = simulertDetaljer.tilbakeforing,
                )
            }

            simulertDetaljer.erYtelse() -> {
                Ytelse(
                    beløp = Kontobeløp(simulertDetaljer.belop),
                    erUtbetalingSomSimuleres = simulertDetaljer.erUtbetalingSomSimuleres(),
                    tilbakeføring = simulertDetaljer.tilbakeforing,
                )
            }

            simulertDetaljer.erMotpostFeilkonto() -> {
                MotpostFeilkonto(
                    beløp = Kontobeløp(simulertDetaljer.belop),
                    tilbakeføring = simulertDetaljer.tilbakeforing,
                )
            }

            simulertDetaljer.erSkatt() -> {
                Skatt(beløp = Kontobeløp(simulertDetaljer.belop), tilbakeføring = simulertDetaljer.tilbakeforing)
            }

            else -> {
                log.error("Ukjent detalj: $simulertDetaljer")
                null
            }
        }

        private fun SimulertDetaljer.erFeilkonto() = klasseType == KlasseType.FEIL

        private fun SimulertDetaljer.erYtelse() = klasseType == KlasseType.YTEL
        private fun SimulertDetaljer.erMotpostFeilkonto() = klasseType == KlasseType.MOTP
        private fun SimulertDetaljer.erSkatt() = klasseType == KlasseType.SKAT
    }

    data class Feilkonto(
        override val beløp: Kontobeløp,
        override val tilbakeføring: Boolean,
    ) : TolketDetalj

    data class Ytelse(
        override val beløp: Kontobeløp,
        override val tilbakeføring: Boolean,
        val erUtbetalingSomSimuleres: Boolean,
    ) : TolketDetalj

    data class MotpostFeilkonto(
        override val beløp: Kontobeløp,
        override val tilbakeføring: Boolean,
    ) : TolketDetalj

    data class Skatt(
        override val beløp: Kontobeløp,
        override val tilbakeføring: Boolean,
    ) : TolketDetalj
}
