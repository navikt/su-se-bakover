package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.NonEmptyList
import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.domain.sak.Sakstype
import java.time.LocalDate

/**
 * @param rawResponse den rå responsen slik den kom fra oppdragssystemet.
 * @param nettoBeløp Nettobeløpet fra økonomisystemet (Inkluderer alle ytelsene/stønadene i INNT (inntektsytelser)). Brutto minus skatt. Dette feltet er i utgangspunktet ubrukelig for oss i SU.
 */
data class Simulering(
    val gjelderId: Fnr,
    val gjelderNavn: String,
    val datoBeregnet: LocalDate,
    val nettoBeløp: Int,
    val måneder: List<SimulertMåned>,
    val rawResponse: String,
) {
    private val tolkning = TolketSimulering(this)

    init {
        check(måneder.isNotEmpty()) { "En simulering må inneholde minst en måned" }
        // TODO jah: Skulle gjerne sjekket at månedene var i rekkefølge, men SimuleringStub/avkortingstestene liker ikke dette.
    }

    fun harFeilutbetalinger(): Boolean {
        return tolkning.harFeilutbetalinger()
    }

    fun harFeilutbetalinger(periode: Periode): Boolean {
        return tolkning.harFeilutbetalinger(periode)
    }

    /**
     * Beløp som tidligere har vært utbetalt for denne perioden.
     */
    fun hentUtbetalteBeløp(): Månedsbeløp {
        return tolkning.hentUtbetalteBeløp()
    }

    fun hentUtbetalteBeløp(måned: Måned): MånedBeløp? {
        return hentUtbetalteBeløp().singleOrNull { it.periode == måned }
    }

    /**
     * Beløpet som vil gå til utbetaling ved iverksettelse av behandling.
     */
    fun hentTilUtbetaling(): Månedsbeløp {
        return tolkning.hentTilUtbetaling()
    }

    /**
     * Økning av feilkonto ved iverksettelse av behandling.
     */
    fun hentFeilutbetalteBeløp(): Månedsbeløp {
        return tolkning.hentFeilutbetalteBeløp()
    }

    /**
     * Beløpet som til slutt vil/burde være utbetalt. Beløpet reduseres tilsvarende [hentFeilutbetalteBeløp] ved iverksettelse av behandling.
     */
    fun hentTotalUtbetaling(): Månedsbeløp {
        return tolkning.hentTotalUtbetaling()
    }

    fun hentTotalUtbetaling(måned: Måned): MånedBeløp? {
        return tolkning.hentTotalUtbetaling().singleOrNull { it.periode == måned }
    }

    fun erAlleMånederUtenUtbetaling(): Boolean {
        return tolkning.erAlleMånederUtenUtbetaling()
    }

    /**
     * Periode tilsvarende tidligste fraOgMed-seneste tilOgMed for simuleringen.
     *
     * Dersom simuleringen vi mottar fra OS ikke inneholder et resultat (ingen posteringer)
     * settes perioden til å være lik perioden som ble brukt ved simulering, se bruk av [SimulerUtbetalingRequest.simuleringsperiode] i [SimuleringClient.simulerUtbetaling].
     *
     * Dersom simuleringen vi mottar fra OS inneholder et resultat, settes perioden fil tidligste fraOgMed-seneste tilOgMed for månedeen med data.
     * Merk at det ikke er noen garanti for at alle månedene i denne perioden inneholder resultat. For å være garantert resultat, se [månederMedSimuleringsresultat]
     *
     *
     */
    fun periode(): Periode {
        return tolkning.periode
    }

    /**
     * Måneder hvor det foreligger et simuleringsresultat (postering mot en konto).
     */
    fun månederMedSimuleringsresultat(): List<Måned> {
        return tolkning.månederMedSimuleringsresultat
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

data class SimulertMåned(
    val måned: Måned,
    val utbetaling: SimulertUtbetaling?,
) {
    /** Ikke alle måneder vi simulerer har simuleringsdata. Typisk hvis måneden aldri har vært utbetalt og/eller ikke skal utbetales. Typisk ved hull i stønadsperiode og/eller opphør. */
    constructor(måned: Måned) : this(måned, null)

    companion object {
        fun create(periode: Periode): List<SimulertMåned> {
            return periode.måneder().map { SimulertMåned(it) }
        }

        fun create(måneder: NonEmptyList<Måned>): List<SimulertMåned> {
            require(måneder.erSammenhengendeSortertOgUtenDuplikater())
            return måneder.map { SimulertMåned(it) }
        }
    }

    internal fun tolk(): TolketMåned {
        return if (utbetaling == null) {
            TolketMånedUtenUtbetalinger(måned = måned)
        } else {
            /**
             * I Teorien kan det være flere utbetalnger per periode, f.eks hvis bruker mottar andre ytelser.
             * Vi bryr oss kun om SU og forventer derfor bare 1.
             */
            TolketMånedMedUtbetalinger(måned = måned, utbetaling = utbetaling.tolk())
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
        fun skalIkkeFiltreres(): List<String> {
            return setOf(YTEL, FEIL, MOTP).map { it.name }
        }

        fun contains(value: String): Boolean {
            return values().map { it.name }.contains(value)
        }
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
        fun skalIkkeFiltreres(): List<String> {
            return setOf(SUUFORE, KL_KODE_FEIL_INNT, SUALDER, KL_KODE_FEIL, TBMOTOBS).map { it.name }
        }

        fun contains(value: String): Boolean {
            return values().map { it.name }.contains(value)
        }
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
