package no.nav.su.se.bakover.domain.oppdrag.simulering

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.application.MånedBeløp
import no.nav.su.se.bakover.common.application.Månedsbeløp
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.sak.Sakstype
import java.time.LocalDate

data class Simulering(
    val gjelderId: Fnr,
    val gjelderNavn: String,
    val datoBeregnet: LocalDate,
    val nettoBeløp: Int,
    val periodeList: List<SimulertPeriode>,
) {
    private val tolkning = TolketSimulering(this)

    fun harFeilutbetalinger(): Boolean {
        return tolkning.harFeilutbetalinger()
    }

    /**
     * Kredit for ytelseskonto. Representerer tidligere utbetalte beløp ved endringer.
     * Se sammenheng mellom hva som til slutt utbetales i [hentDebetYtelse]
     */
    fun hentUtbetalteBeløp(): Månedsbeløp {
        return tolkning.hentUtbetalteBeløp()
    }

    fun hentUtbetalteBeløp(måned: Måned): MånedBeløp? {
        return hentUtbetalteBeløp().singleOrNull { it.periode == måned }
    }

    /**
     * Beløp som vil bli utbetalt som en konsenvens av denne simuleringen.
     * Typisk differansen mellom [hentUtbetalingSomSimuleres] - [hentUtbetalteBeløp] dersom denne er positiv.
     * 0 dersom differansen er negativ.
     */
    fun hentTilUtbetaling(): Månedsbeløp {
        return tolkning.hentTilUtbetaling()
    }

    /**
     * Perioder hvor det vil forekomme debitering (økning) av feilkonto som følge av denne simuleringen.
     */
    fun hentFeilutbetalteBeløp(): Månedsbeløp {
        return tolkning.hentFeilutbetalteBeløp()
    }

    /**
     * Beløpet vi har simulert utbetaling for i denne simuleringen.
     */
    fun hentUtbetalingSomSimuleres(): Månedsbeløp {
        return tolkning.hentUtbetalingSomSimuleres()
    }

    fun hentUtbetalingSomSimuleres(måned: Måned): MånedBeløp? {
        return tolkning.hentUtbetalingSomSimuleres().singleOrNull { it.periode == måned }
    }

    /**
     * Debit for ytelseskonto. Representerer i sin enkelste form brutto-beløpet som skal betales ut.
     * Dersom ytelse tidligere har blitt betalt ut for en periode vil ny utbetaling ([hentTilUtbetaling]) tilsvare differansen mellom
     * [hentDebetYtelse] og [hentUtbetalteBeløp]
     */
    fun hentDebetYtelse(): Månedsbeløp {
        return tolkning.hentDebetYtelse()
    }

    fun erSimuleringUtenUtbetalinger(): Boolean {
        return tolkning.erSimuleringUtenUtbetalinger()
    }

    fun periode(): Periode {
        return tolkning.periode
    }

    /**
     * Debet/kredit oppstilling av de ulike konti.
     */
    fun kontooppstilling(): Map<Periode, Kontooppstilling> {
        return tolkning.kontooppstilling()
    }
    fun oppsummering(): SimuleringsOppsummering {
        return SimuleringsOppsummering(
            totalOppsummering = tolkning.totalOppsummering(),
            periodeOppsummering = tolkning.periodeOppsummering(),
        )
    }
}

data class SimuleringsOppsummering(
    val totalOppsummering: PeriodeOppsummering,
    val periodeOppsummering: List<PeriodeOppsummering>,
)

data class PeriodeOppsummering(
    val periode: Periode,
    val sumTilUtbetaling: Int,
    val sumEtterbetaling: Int,
    val sumFramtidigUtbetaling: Int,
    val sumTotalUtbetaling: Int,
    val sumTidligereUtbetalt: Int,
    val sumFeilutbetaling: Int,
    val sumReduksjonFeilkonto: Int,
)

data class SimulertPeriode(
    @JsonAlias("fraOgMed", "fom")
    val fraOgMed: LocalDate,
    @JsonAlias("tilOgMed", "tom")
    val tilOgMed: LocalDate,
    val utbetaling: List<SimulertUtbetaling>,
) {
    internal fun tolk(): TolketPeriode {
        return if (utbetaling.isEmpty()) {
            TolketPeriodeUtenUtbetalinger(periode = Periode.create(fraOgMed, tilOgMed))
        } else {
            /**
             * I Teorien kan det være flere utbetalnger per periode, f.eks hvis bruker mottar andre ytelser.
             * Vi bryr oss kun om SU og forventer derfor bare 1.
             */
            TolketPeriodeMedUtbetalinger(
                måned = Måned.fra(fraOgMed, tilOgMed),
                utbetaling = utbetaling.single().tolk(),
            )
        }
    }
}

data class SimulertUtbetaling(
    val fagSystemId: String,
    val utbetalesTilId: Fnr,
    val utbetalesTilNavn: String,
    val forfall: LocalDate,
    val feilkonto: Boolean,
    val detaljer: List<SimulertDetaljer>,
) {

    internal fun tolk(): TolketUtbetaling {
        return TolketUtbetaling(detaljer = detaljer.mapNotNull { it.tolk() }, forfall = forfall)
    }
}

data class SimulertDetaljer(
    @JsonAlias("faktiskFraOgMed", "faktiskFom")
    val faktiskFraOgMed: LocalDate,
    @JsonAlias("faktiskTilOgMed", "faktiskTom")
    val faktiskTilOgMed: LocalDate,
    val konto: String,
    val belop: Int,
    val tilbakeforing: Boolean,
    val sats: Int,
    val typeSats: String,
    val antallSats: Int,
    val uforegrad: Int,
    val klassekode: KlasseKode,
    val klassekodeBeskrivelse: String,
    val klasseType: KlasseType,
) {
    fun tolk(): TolketDetalj? {
        return TolketDetalj.from(simulertDetaljer = this)
    }
}

enum class KlasseType {
    YTEL,
    SKAT,
    FEIL,
    MOTP,
    ;

    companion object {
        fun skalIkkeFiltreres() = setOf(YTEL, FEIL, MOTP)
    }
}

enum class KlasseKode {
    SUUFORE,
    KL_KODE_FEIL_INNT,
    TBMOTOBS,
    FSKTSKAT,

    /** Filtreres vekk av klient, bakoverkompatabilitet */
    UFOREUT,

    SUALDER,
    KL_KODE_FEIL,
    ;

    companion object {
        fun skalIkkeFiltreres() = setOf(SUUFORE, KL_KODE_FEIL_INNT, SUALDER, KL_KODE_FEIL, TBMOTOBS)
    }
}

fun Sakstype.toYtelsekode(): KlasseKode {
    return when (this) {
        Sakstype.ALDER -> {
            KlasseKode.SUALDER
        }
        Sakstype.UFØRE -> {
            KlasseKode.SUUFORE
        }
    }
}

fun Sakstype.toFeilkode(): KlasseKode {
    return when (this) {
        Sakstype.ALDER -> {
            KlasseKode.KL_KODE_FEIL
        }
        Sakstype.UFØRE -> {
            KlasseKode.KL_KODE_FEIL_INNT
        }
    }
}
