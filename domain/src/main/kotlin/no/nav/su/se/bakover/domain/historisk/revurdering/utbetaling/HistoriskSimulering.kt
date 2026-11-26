package no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregning
import økonomi.domain.simulering.SimuleringFeilet
import java.math.BigDecimal
import java.time.LocalDate

fun interface HistoriskSimuleringClient {
    fun simulerHistorisk(
        forespørsel: HistoriskSimuleringsforespørsel,
    ): Either<KunneIkkeSimulereHistoriskUtbetaling, HistoriskSimuleringsrespons>
}

data class HistoriskSimuleringsforespørsel(
    val grunnlag: HistoriskInfotrygdOppdragsgrunnlag,
    val saksnummer: Saksnummer,
    val fnr: Fnr,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val harEksisterendeSuOppdrag: Boolean,
)

sealed interface KunneIkkeSimulereHistoriskUtbetaling {
    data class Klientfeil(val feil: SimuleringFeilet) : KunneIkkeSimulereHistoriskUtbetaling
    data object UgyldigMeldingskandidat : KunneIkkeSimulereHistoriskUtbetaling
}

data class HistoriskSimuleringsrespons(
    val beregning: Beregning?,
    val infomelding: String?,
    val råRespons: String,
) {
    fun erGyldigFor(fnr: Fnr): Boolean =
        beregning?.let { it.gjelderId == fnr && it.perioder.isNotEmpty() } == true

    data class Beregning(
        val gjelderId: Fnr,
        val gjelderNavn: String,
        val datoBeregnet: LocalDate,
        val faggruppe: String,
        // Oppdrags samlede beløp er ikke en beregnet SU-differanse.
        val beløp: BigDecimal,
        val perioder: List<Beregningsperiode>,
    )

    data class Beregningsperiode(
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
        val utbetalinger: List<Utbetaling>,
    ) {
        init {
            require(!tilOgMed.isBefore(fraOgMed)) { "Ugyldig simuleringsperiode i respons" }
        }
    }

    data class Utbetaling(
        val fagområde: String,
        val stoppnivåId: String,
        val behandlendeEnhet: String,
        val oppdragsId: String,
        val fagsystemId: String,
        val kid: String?,
        val utbetalesTilId: String,
        val utbetalesTilNavn: String,
        val bilagstype: String,
        val forfall: LocalDate,
        val feilkonto: Boolean,
        val detaljer: List<Detalj>,
    )

    data class Detalj(
        val faktiskFraOgMed: LocalDate,
        val faktiskTilOgMed: LocalDate,
        val konto: String,
        val behandlingskode: String,
        val beløp: BigDecimal,
        val trekkVedtakId: String,
        val stønadId: String?,
        val korrigering: String?,
        val tilbakeføring: Boolean,
        val linjeId: String,
        val sats: BigDecimal,
        val typeSats: String,
        val antallSats: BigDecimal,
        val saksbehandlerId: String,
        val uføregrad: BigDecimal,
        val kravhaverId: String?,
        val delytelseId: String?,
        val bostedsenhet: String,
        val skyldnerId: String?,
        val klassekode: String,
        val klassekodeBeskrivelse: String,
        val klasseType: String,
        val klasseTypeBeskrivelse: String,
        val refunderesOrgNr: String?,
    ) {
        init {
            require(!faktiskTilOgMed.isBefore(faktiskFraOgMed)) { "Ugyldig detaljperiode i simuleringsrespons" }
        }
    }
}

data class HistoriskInfotrygdSimulering(
    val grunnlag: HistoriskInfotrygdBeregning,
    val simuleringsperiode: Periode,
    val simulert: Tidspunkt,
    val harEksisterendeSuOppdrag: Boolean,
    val respons: HistoriskSimuleringsrespons,
) {
    init {
        require(
            grunnlag.månedsresultater.keys.all {
                it.fraOgMed >= simuleringsperiode.fraOgMed && it.tilOgMed <= simuleringsperiode.tilOgMed
            },
        ) {
            "Simuleringsperioden må dekke beregningen"
        }
        require(respons.beregning != null && respons.beregning.perioder.isNotEmpty()) {
            "Historisk simulering må ha et beregningsresultat"
        }
    }
}
