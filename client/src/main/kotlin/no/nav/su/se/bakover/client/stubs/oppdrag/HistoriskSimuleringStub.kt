package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskSimuleringClient
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskSimuleringsforespørsel
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskSimuleringsrespons
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.KunneIkkeSimulereHistoriskUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.toFagområde
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

class HistoriskSimuleringStub(private val clock: Clock) : HistoriskSimuleringClient {
    override fun simulerHistorisk(
        forespørsel: HistoriskSimuleringsforespørsel,
    ): Either<KunneIkkeSimulereHistoriskUtbetaling, HistoriskSimuleringsrespons> {
        val datoBeregnet = LocalDate.now(clock)
        val stubmarkør = "LOKAL_STUB"
        val navn = "Lokal testperson"
        val fagområde = Sakstype.ALDER.toFagområde().toString()
        val saksnummer = forespørsel.saksnummer.toString()
        val perioder = forespørsel.grunnlag.månedsresultater.map { (måned, resultat) ->
            val beløp = when (resultat) {
                is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> resultat.beløp
                is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> BigDecimal.ZERO
            }
            HistoriskSimuleringsrespons.Beregningsperiode(
                fraOgMed = måned.fraOgMed,
                tilOgMed = måned.tilOgMed,
                utbetalinger = listOf(
                    HistoriskSimuleringsrespons.Utbetaling(
                        fagområde = fagområde,
                        stoppnivåId = "lokal-$måned",
                        behandlendeEnhet = stubmarkør,
                        oppdragsId = "lokal-$saksnummer",
                        fagsystemId = saksnummer,
                        kid = null,
                        utbetalesTilId = forespørsel.fnr.value,
                        utbetalesTilNavn = navn,
                        bilagstype = stubmarkør,
                        forfall = datoBeregnet,
                        feilkonto = false,
                        detaljer = listOf(
                            HistoriskSimuleringsrespons.Detalj(
                                faktiskFraOgMed = måned.fraOgMed,
                                faktiskTilOgMed = måned.tilOgMed,
                                konto = stubmarkør,
                                behandlingskode = stubmarkør,
                                beløp = beløp,
                                trekkVedtakId = stubmarkør,
                                stønadId = null,
                                korrigering = null,
                                tilbakeføring = false,
                                linjeId = "lokal-linje-$måned",
                                sats = beløp,
                                typeSats = "MND",
                                antallSats = BigDecimal.ONE,
                                saksbehandlerId = forespørsel.saksbehandler.navIdent,
                                uføregrad = BigDecimal.ZERO,
                                kravhaverId = null,
                                delytelseId = null,
                                bostedsenhet = stubmarkør,
                                skyldnerId = null,
                                klassekode = fagområde,
                                klassekodeBeskrivelse = "Beregnet beløp i lokal stub",
                                klasseType = stubmarkør,
                                klasseTypeBeskrivelse = "Lokalt testresultat",
                                refunderesOrgNr = null,
                            ),
                        ),
                    ),
                ),
            )
        }
        return HistoriskSimuleringsrespons(
            beregning = HistoriskSimuleringsrespons.Beregning(
                gjelderId = forespørsel.fnr,
                gjelderNavn = navn,
                datoBeregnet = datoBeregnet,
                faggruppe = stubmarkør,
                beløp = perioder.sumOf { periode -> periode.utbetalinger.sumOf { it.detaljer.sumOf { detalj -> detalj.beløp } } },
                perioder = perioder,
            ),
            infomelding = "Lokalt testresultat laget fra beregningen. Ingen forespørsel er sendt til Oppdrag. " +
                "Tidligere utbetalinger, etterbetaling og feilutbetaling er ikke simulert.",
            råRespons = "Lokal stub: ingen respons fra Oppdrag.",
        ).right()
    }
}
