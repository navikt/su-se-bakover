package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import økonomi.domain.simulering.Simulering
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.util.UUID

/**
 * TODO dobbeltimplementasjon i prod: [no.nav.su.se.bakover.web.services.tilbakekreving.LokalMottaKravgrunnlagJob]
 * @see [no.nav.su.se.bakover.web.services.tilbakekreving.genererKravgrunnlagFraSimulering]
 */
fun genererKravgrunnlagFraSimulering(
    saksnummer: Saksnummer,
    simulering: Simulering,
    utbetalingId: UUID30,
    clock: Clock,
    eksternKravgrunnlagId: String = "123456",
    eksternVedtakId: String = "654321",
    behandler: String = "K231B433",
    eksternTidspunkt: Tidspunkt = Tidspunkt.now(clock),
    status: Kravgrunnlag.KravgrunnlagStatus = Kravgrunnlag.KravgrunnlagStatus.Nytt,
    skatteprosent: BigDecimal = BigDecimal("50"),
): Kravgrunnlag {
    return Kravgrunnlag(
        saksnummer = saksnummer,
        eksternKravgrunnlagId = eksternKravgrunnlagId,
        eksternVedtakId = eksternVedtakId,
        eksternKontrollfelt = eksternTidspunkt.toOppdragTimestamp(),
        status = status,
        behandler = behandler,
        utbetalingId = utbetalingId,
        eksternTidspunkt = eksternTidspunkt,
        grunnlagsmåneder = simulering.hentFeilutbetalteBeløp()
            .map { (måned, feilutbetaling) ->
                val beløpTidligereUtbetaling = simulering.hentUtbetalteBeløp(måned)!!.sum()
                val beløpNyUtbetaling = simulering.hentTotalUtbetaling(måned)!!.sum()
                val beløpSkalTilbakekreves = feilutbetaling.sum()
                require(beløpTidligereUtbetaling - beløpNyUtbetaling == beløpSkalTilbakekreves) {
                    "Forventet at beløpTidligereUtbetaling ($beløpTidligereUtbetaling) - beløpNyUtbetaling($beløpNyUtbetaling) == beløpSkalTilbakekreves($beløpSkalTilbakekreves)."
                }
                Kravgrunnlag.Grunnlagsmåned(
                    måned = måned,
                    betaltSkattForYtelsesgruppen = skatteprosent.times(BigDecimal(beløpSkalTilbakekreves)).divide(BigDecimal(100.0000)).setScale(0, RoundingMode.UP),
                    ytelse = Kravgrunnlag.Grunnlagsmåned.Ytelse(
                        beløpTidligereUtbetaling = beløpTidligereUtbetaling,
                        beløpNyUtbetaling = beløpNyUtbetaling,
                        beløpSkalTilbakekreves = beløpSkalTilbakekreves,
                        beløpSkalIkkeTilbakekreves = 0,
                        skatteProsent = skatteprosent,
                    ),
                    feilutbetaling = Kravgrunnlag.Grunnlagsmåned.Feilutbetaling(
                        beløpTidligereUtbetaling = 0,
                        beløpNyUtbetaling = simulering.hentFeilutbetalteBeløp(måned)!!.sum(),
                        beløpSkalTilbakekreves = 0,
                        beløpSkalIkkeTilbakekreves = 0,
                    ),
                )
            },
    )
}

fun nyOpprettetTilbakekrevingsbehandlingHendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = Hendelsesversjon(2),
    meta: DefaultHendelseMetadata = DefaultHendelseMetadata.tom(),
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    opprettetAv: NavIdentBruker.Saksbehandler = saksbehandler,
    kravgrunnlagsId: String = "123",
): OpprettetTilbakekrevingsbehandlingHendelse = OpprettetTilbakekrevingsbehandlingHendelse(
    hendelseId = hendelseId,
    sakId = sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    meta = meta,
    id = behandlingId,
    opprettetAv = opprettetAv,
    kravgrunnlagsId = kravgrunnlagsId,
)
