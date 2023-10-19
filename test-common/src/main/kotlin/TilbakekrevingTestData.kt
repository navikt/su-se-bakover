package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.BrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.ForhåndsvarsleTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.MånedsvurderingerTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilAttesteringHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vurdert.Månedsvurdering
import tilbakekreving.domain.vurdert.Vurdering
import tilbakekreving.domain.vurdert.Vurderinger
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
                    betaltSkattForYtelsesgruppen = skatteprosent.times(BigDecimal(beløpSkalTilbakekreves))
                        .divide(BigDecimal(100.0000)).setScale(0, RoundingMode.UP),
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

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param kravgrunnlagsId Ignoreres desom [forrigeHendelse] sendes inn.
 */
fun nyForhåndsvarsletTilbakekrevingsbehandlingHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    kravgrunnlagsId: String = "123",
    utførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyOpprettetTilbakekrevingsbehandlingHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagsId = kravgrunnlagsId,
        opprettetAv = utførtAv,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
    meta: DefaultHendelseMetadata = DefaultHendelseMetadata.tom(),
    fritekst: String = "",
    dokumentId: UUID = UUID.randomUUID(),
): ForhåndsvarsleTilbakekrevingsbehandlingHendelse = ForhåndsvarsleTilbakekrevingsbehandlingHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    meta = meta,
    id = forrigeHendelse.id,
    utførtAv = utførtAv,
    tidligereHendelseId = forrigeHendelse.hendelseId,
    fritekst = fritekst,
    dokumentId = dokumentId,
)

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param kravgrunnlagsId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param dokumentId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param vurderinger Ignoreres desom [forrigeHendelse] sendes inn.
 */
fun nyVurdertTilbakekrevingsbehandlingHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    kravgrunnlagsId: String = "123456",
    dokumentId: UUID = UUID.randomUUID(),
    utførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyForhåndsvarsletTilbakekrevingsbehandlingHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagsId = kravgrunnlagsId,
        dokumentId = dokumentId,
        utførtAv = utførtAv,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
    meta: DefaultHendelseMetadata = DefaultHendelseMetadata.tom(),
    vurderinger: Vurderinger = Vurderinger(
        vurderinger = nonEmptyListOf(
            Månedsvurdering(
                måned = januar(2021),
                vurdering = Vurdering.SkalTilbakekreve,
            ),
        ),
    ),
): MånedsvurderingerTilbakekrevingsbehandlingHendelse = MånedsvurderingerTilbakekrevingsbehandlingHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    meta = meta,
    id = forrigeHendelse.id,
    utførtAv = utførtAv,
    tidligereHendelseId = forrigeHendelse.hendelseId,
    vurderinger = vurderinger,
)

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param kravgrunnlagsId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param dokumentId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param vurderinger Ignoreres desom [forrigeHendelse] sendes inn.
 */
fun nyOppdaterVedtaksbrevTilbakekrevingsbehandlingHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    kravgrunnlagsId: String = "123456",
    dokumentId: UUID = UUID.randomUUID(),
    vurderinger: Vurderinger = Vurderinger(
        vurderinger = nonEmptyListOf(
            Månedsvurdering(
                måned = januar(2021),
                vurdering = Vurdering.SkalTilbakekreve,
            ),
        ),
    ),
    utførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyVurdertTilbakekrevingsbehandlingHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagsId = kravgrunnlagsId,
        dokumentId = dokumentId,
        vurderinger = vurderinger,
        utførtAv = utførtAv,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
    meta: DefaultHendelseMetadata = DefaultHendelseMetadata.tom(),
    brevvalg: Brevvalg.SaksbehandlersValg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(
        fritekst = "fritekst",
    ),
): BrevTilbakekrevingsbehandlingHendelse = BrevTilbakekrevingsbehandlingHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    meta = meta,
    id = forrigeHendelse.id,
    utførtAv = utførtAv,
    tidligereHendelseId = forrigeHendelse.hendelseId,
    brevvalg = brevvalg,
)

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param kravgrunnlagsId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param dokumentId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param vurderinger Ignoreres desom [forrigeHendelse] sendes inn.
 * @param brevvalg Ignoreres desom [forrigeHendelse] sendes inn.
 */
fun nyTilbakekrevingsbehandlingTilAttesteringHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    kravgrunnlagsId: String = "123456",
    dokumentId: UUID = UUID.randomUUID(),
    vurderinger: Vurderinger = Vurderinger(
        vurderinger = nonEmptyListOf(
            Månedsvurdering(
                måned = januar(2021),
                vurdering = Vurdering.SkalTilbakekreve,
            ),
        ),
    ),
    brevvalg: Brevvalg.SaksbehandlersValg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(
        fritekst = "fritekst",
    ),
    utførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyOppdaterVedtaksbrevTilbakekrevingsbehandlingHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagsId = kravgrunnlagsId,
        dokumentId = dokumentId,
        vurderinger = vurderinger,
        utførtAv = utførtAv,
        brevvalg = brevvalg,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
    meta: DefaultHendelseMetadata = DefaultHendelseMetadata.tom(),
): TilAttesteringHendelse = TilAttesteringHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    meta = meta,
    id = forrigeHendelse.id,
    utførtAv = utførtAv,
    tidligereHendelseId = forrigeHendelse.hendelseId,
)

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param kravgrunnlagsId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param dokumentId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param vurderinger Ignoreres desom [forrigeHendelse] sendes inn.
 * @param brevvalg Ignoreres desom [forrigeHendelse] sendes inn.
 */
fun nyIverksattTilbakekrevingsbehandlingHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    kravgrunnlagsId: String = "123456",
    dokumentId: UUID = UUID.randomUUID(),
    vurderinger: Vurderinger = Vurderinger(
        vurderinger = nonEmptyListOf(
            Månedsvurdering(
                måned = januar(2021),
                vurdering = Vurdering.SkalTilbakekreve,
            ),
        ),
    ),
    brevvalg: Brevvalg.SaksbehandlersValg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(
        fritekst = "fritekst",
    ),
    utførtAv: NavIdentBruker.Attestant = attestant,
    sendtTilAttesteringUtførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyTilbakekrevingsbehandlingTilAttesteringHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagsId = kravgrunnlagsId,
        dokumentId = dokumentId,
        vurderinger = vurderinger,
        utførtAv = sendtTilAttesteringUtførtAv,
        brevvalg = brevvalg,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
    meta: DefaultHendelseMetadata = DefaultHendelseMetadata.tom(),
): IverksattHendelse = IverksattHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    meta = meta,
    id = forrigeHendelse.id,
    utførtAv = utførtAv,
    tidligereHendelseId = forrigeHendelse.hendelseId,
)
