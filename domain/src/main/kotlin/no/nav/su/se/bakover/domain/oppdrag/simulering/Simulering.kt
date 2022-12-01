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

    fun hentUtbetalteBeløp(): Månedsbeløp {
        return tolkning.hentUtbetalteBeløp()
    }

    fun hentUtbetalteBeløp(måned: Måned): MånedBeløp? {
        return hentUtbetalteBeløp().singleOrNull { it.periode == måned }
    }

    fun hentTilUtbetaling(): Månedsbeløp {
        return tolkning.hentTilUtbetaling()
    }

    fun hentFeilutbetalteBeløp(): Månedsbeløp {
        return tolkning.hentFeilutbetalteBeløp()
    }

    fun hentUtbetalingSomSimuleres(): Månedsbeløp {
        return tolkning.hentUtbetalingSomSimuleres()
    }

    fun hentUtbetalingSomSimuleres(måned: Måned): MånedBeløp? {
        return tolkning.hentUtbetalingSomSimuleres().singleOrNull { it.periode == måned }
    }

    fun hentDebetYtelse(): Månedsbeløp {
        return tolkning.hentDebetYtelse()
    }

    fun erSimuleringUtenUtbetalinger(): Boolean {
        return tolkning.erSimuleringUtenUtbetalinger()
    }

    fun periode(): Periode {
        return tolkning.periode
    }

    fun kontooppstilling(): Map<Periode, Kontooppstilling> {
        return tolkning.kontooppstilling()
    }
}

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
        return TolketUtbetaling(detaljer.mapNotNull { it.tolk(forfall = forfall) })
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
    fun tolk(forfall: LocalDate): TolketDetalj? {
        return TolketDetalj.from(simulertDetaljer = this, forfall = forfall)
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
