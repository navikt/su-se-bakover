package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import økonomi.domain.KlasseKode
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

/**
 * TODO dobbeltimplementasjon
 * @see [no.nav.su.se.bakover.web.services.tilbakekreving.matchendeKravgrunnlag]
 */
fun matchendeKravgrunnlag(
    revurdering: Revurdering,
    simulering: Simulering,
    utbetalingId: UUID30,
    clock: Clock,
): Kravgrunnlag {
    val now = Tidspunkt.now(clock)
    return simulering.let {
        Kravgrunnlag(
            saksnummer = revurdering.saksnummer,
            eksternKravgrunnlagId = "123456",
            eksternVedtakId = "654321",
            eksternKontrollfelt = now.toOppdragTimestamp(),
            status = Kravgrunnlag.KravgrunnlagStatus.Nytt,
            behandler = "K231B433",
            utbetalingId = utbetalingId,
            eksternTidspunkt = now,
            grunnlagsmåneder = it.hentFeilutbetalteBeløp()
                .map { (periode, feilutbetaling) ->
                    Kravgrunnlag.Grunnlagsmåned(
                        måned = periode.tilMåned(),
                        betaltSkattForYtelsesgruppen = BigDecimal(4395),
                        ytelse = Kravgrunnlag.Grunnlagsmåned.Ytelse(
                            klassekode = KlasseKode.SUUFORE,
                            beløpTidligereUtbetaling = it.hentUtbetalteBeløp(periode)!!.sum(),
                            beløpNyUtbetaling = it.hentTotalUtbetaling(periode)!!.sum(),
                            beløpSkalTilbakekreves = feilutbetaling.sum(),
                            beløpSkalIkkeTilbakekreves = 0,
                            skatteProsent = BigDecimal("43.9983"),
                        ),
                        feilutbetaling = Kravgrunnlag.Grunnlagsmåned.Feilutbetaling(
                            klassekode = KlasseKode.KL_KODE_FEIL_INNT,
                            beløpTidligereUtbetaling = 0,
                            beløpNyUtbetaling = feilutbetaling.sum(),
                            beløpSkalTilbakekreves = 0,
                            beløpSkalIkkeTilbakekreves = 0,
                        ),
                    )
                },
        )
    }
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
