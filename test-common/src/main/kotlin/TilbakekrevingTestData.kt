package no.nav.su.se.bakover.test

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.NonBlankString
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlag
import tilbakekreving.domain.AvbruttHendelse
import tilbakekreving.domain.BrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.ForhåndsvarsletTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.NotatTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.OppdatertKravgrunnlagPåTilbakekrevingHendelse
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilAttesteringHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.UnderkjentHendelse
import tilbakekreving.domain.VurdertTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus
import tilbakekreving.domain.underkjennelse.UnderkjennAttesteringsgrunnTilbakekreving
import tilbakekreving.domain.vurdering.Vurdering
import tilbakekreving.domain.vurdering.Vurderinger
import tilbakekreving.domain.vurdering.VurderingerMedKrav
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
    status: Kravgrunnlagstatus = Kravgrunnlagstatus.Nytt,
    skatteprosent: BigDecimal = BigDecimal("50"),
    kravgrunnlagPåSakHendelseId: HendelseId,
): Kravgrunnlag {
    return Kravgrunnlag(
        hendelseId = kravgrunnlagPåSakHendelseId,
        saksnummer = saksnummer,
        eksternKravgrunnlagId = eksternKravgrunnlagId,
        eksternVedtakId = eksternVedtakId,
        eksternKontrollfelt = eksternTidspunkt.toOppdragTimestamp(),
        status = status,
        behandler = behandler,
        utbetalingId = utbetalingId,
        eksternTidspunkt = eksternTidspunkt,
        grunnlagsperioder = simulering.hentFeilutbetalteBeløp()
            .map { (måned, feilutbetaling) ->
                val beløpTidligereUtbetaling = simulering.hentUtbetalteBeløp(måned)!!.sum()
                val beløpNyUtbetaling = simulering.hentTotalUtbetaling(måned)!!.sum()
                val beløpSkalTilbakekreves = feilutbetaling.sum()
                require(beløpTidligereUtbetaling - beløpNyUtbetaling == beløpSkalTilbakekreves) {
                    "Forventet at beløpTidligereUtbetaling ($beløpTidligereUtbetaling) - beløpNyUtbetaling($beløpNyUtbetaling) == beløpSkalTilbakekreves($beløpSkalTilbakekreves)."
                }
                Kravgrunnlag.Grunnlagsperiode(
                    periode = måned,
                    betaltSkattForYtelsesgruppen = skatteprosent.times(BigDecimal(beløpSkalTilbakekreves))
                        .divide(BigDecimal(100.0000)).setScale(0, RoundingMode.UP).intValueExact(),
                    bruttoTidligereUtbetalt = beløpTidligereUtbetaling,
                    bruttoNyUtbetaling = beløpNyUtbetaling,
                    bruttoFeilutbetaling = beløpSkalTilbakekreves,
                    skatteProsent = skatteprosent,
                )
            },
    )
}

fun nyOpprettetTilbakekrevingsbehandlingHendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = Hendelsesversjon(2),
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    opprettetAv: NavIdentBruker.Saksbehandler = saksbehandler,
    kravgrunnlagPåSakHendelseId: HendelseId,
): OpprettetTilbakekrevingsbehandlingHendelse = OpprettetTilbakekrevingsbehandlingHendelse(
    hendelseId = hendelseId,
    sakId = sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    id = behandlingId,
    opprettetAv = opprettetAv,
    kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
)

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param kravgrunnlagPåSakHendelseId Ignoreres desom [forrigeHendelse] sendes inn.
 */
fun nyForhåndsvarsletTilbakekrevingsbehandlingHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    kravgrunnlagPåSakHendelseId: HendelseId,
    utførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyOpprettetTilbakekrevingsbehandlingHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        opprettetAv = utførtAv,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
    fritekst: String = "",
    dokumentId: UUID = UUID.randomUUID(),
): ForhåndsvarsletTilbakekrevingsbehandlingHendelse = ForhåndsvarsletTilbakekrevingsbehandlingHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    id = forrigeHendelse.id,
    utførtAv = utførtAv,
    tidligereHendelseId = forrigeHendelse.hendelseId,
    fritekst = fritekst,
    dokumentId = dokumentId,
)

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param kravgrunnlagPåSakHendelseId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param dokumentId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param vurderingerMedKrav Ignoreres desom [forrigeHendelse] sendes inn.
 */
fun nyVurdertTilbakekrevingsbehandlingHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    kravgrunnlagPåSakHendelseId: HendelseId,
    dokumentId: UUID = UUID.randomUUID(),
    utførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyForhåndsvarsletTilbakekrevingsbehandlingHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        dokumentId = dokumentId,
        utførtAv = utførtAv,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
    kravgrunnlag: Kravgrunnlag = kravgrunnlag(
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        behandler = utførtAv.toString(),
    ),
    vurderinger: Vurderinger = nyVurderinger(),
    vurderingerMedKrav: VurderingerMedKrav = VurderingerMedKrav.utledFra(
        vurderinger,
        kravgrunnlag,
    ).getOrFail(),
): VurdertTilbakekrevingsbehandlingHendelse = VurdertTilbakekrevingsbehandlingHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    id = forrigeHendelse.id,
    utførtAv = utførtAv,
    tidligereHendelseId = forrigeHendelse.hendelseId,
    vurderingerMedKrav = vurderingerMedKrav,
)

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param kravgrunnlagPåSakHendelseId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param dokumentId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param vurderingerMedKrav Ignoreres desom [forrigeHendelse] sendes inn.
 */
fun nyOppdaterVedtaksbrevTilbakekrevingsbehandlingHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    kravgrunnlagPåSakHendelseId: HendelseId,
    dokumentId: UUID = UUID.randomUUID(),
    utførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    kravgrunnlag: Kravgrunnlag = kravgrunnlag(
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        behandler = utførtAv.toString(),
    ),
    vurderinger: Vurderinger = nyVurderinger(),
    vurderingerMedKrav: VurderingerMedKrav = VurderingerMedKrav.utledFra(
        vurderinger,
        kravgrunnlag,
    ).getOrFail(),
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyVurdertTilbakekrevingsbehandlingHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        dokumentId = dokumentId,
        vurderingerMedKrav = vurderingerMedKrav,
        utførtAv = utførtAv,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
    brevvalg: Brevvalg.SaksbehandlersValg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(
        fritekst = "fritekst",
    ),
): BrevTilbakekrevingsbehandlingHendelse = BrevTilbakekrevingsbehandlingHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    id = forrigeHendelse.id,
    utførtAv = utførtAv,
    tidligereHendelseId = forrigeHendelse.hendelseId,
    brevvalg = brevvalg,
)

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param kravgrunnlagPåSakHendelseId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param dokumentId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param vurderingerMedKrav Ignoreres desom [forrigeHendelse] sendes inn.
 */
fun nyOppdatertNotatTilbakekrevingsbehandlingHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    kravgrunnlagPåSakHendelseId: HendelseId,
    dokumentId: UUID = UUID.randomUUID(),
    utførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    kravgrunnlag: Kravgrunnlag = kravgrunnlag(
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        behandler = utførtAv.toString(),
    ),
    vurderinger: Vurderinger = nyVurderinger(),
    vurderingerMedKrav: VurderingerMedKrav = VurderingerMedKrav.utledFra(
        vurderinger,
        kravgrunnlag,
    ).getOrFail(),
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyVurdertTilbakekrevingsbehandlingHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        dokumentId = dokumentId,
        vurderingerMedKrav = vurderingerMedKrav,
        utførtAv = utførtAv,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
    notat: NonBlankString? = NonBlankString.create("notat"),
): NotatTilbakekrevingsbehandlingHendelse = NotatTilbakekrevingsbehandlingHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    id = forrigeHendelse.id,
    utførtAv = utførtAv,
    tidligereHendelseId = forrigeHendelse.hendelseId,
    notat = notat,
)

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param førsteKravgrunnlagPåSakHendelseId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param dokumentId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param vurderingerMedKrav Ignoreres desom [forrigeHendelse] sendes inn.
 * @param oppdatertKravgrunnlagPåSakHendelseId Dersom denne ikke sendes inn, vil det ikke finnes et oppdatert kravgrunnlag, kun en referanseløs id.
 */
fun nyOppdatertKravgrunnlagTilbakekrevingsbehandlingHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    førsteKravgrunnlagPåSakHendelseId: HendelseId,
    dokumentId: UUID = UUID.randomUUID(),
    utførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    førsteKravgrunnlag: Kravgrunnlag = kravgrunnlag(
        kravgrunnlagPåSakHendelseId = førsteKravgrunnlagPåSakHendelseId,
        behandler = utførtAv.toString(),
    ),
    vurderinger: Vurderinger = nyVurderinger(),
    vurderingerMedKrav: VurderingerMedKrav = VurderingerMedKrav.utledFra(
        vurderinger,
        førsteKravgrunnlag,
    ).getOrFail(),
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyVurdertTilbakekrevingsbehandlingHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagPåSakHendelseId = førsteKravgrunnlagPåSakHendelseId,
        dokumentId = dokumentId,
        vurderingerMedKrav = vurderingerMedKrav,
        utførtAv = utførtAv,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
    oppdatertKravgrunnlagPåSakHendelseId: HendelseId = HendelseId.generer(),
): OppdatertKravgrunnlagPåTilbakekrevingHendelse = OppdatertKravgrunnlagPåTilbakekrevingHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    id = forrigeHendelse.id,
    utførtAv = utførtAv,
    tidligereHendelseId = forrigeHendelse.hendelseId,
    kravgrunnlagPåSakHendelseId = oppdatertKravgrunnlagPåSakHendelseId,
)

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param kravgrunnlagPåSakHendelseId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param dokumentId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param vurderingerMedKrav Ignoreres desom [forrigeHendelse] sendes inn.
 * @param brevvalg Ignoreres desom [forrigeHendelse] sendes inn.
 */
fun nyTilbakekrevingsbehandlingTilAttesteringHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    kravgrunnlagPåSakHendelseId: HendelseId,
    dokumentId: UUID = UUID.randomUUID(),
    utførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    kravgrunnlag: Kravgrunnlag = kravgrunnlag(
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        behandler = utførtAv.toString(),
    ),
    vurderinger: Vurderinger = nyVurderinger(),
    vurderingerMedKrav: VurderingerMedKrav = VurderingerMedKrav.utledFra(
        vurderinger,
        kravgrunnlag,
    ).getOrFail(),
    brevvalg: Brevvalg.SaksbehandlersValg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(
        fritekst = "fritekst",
    ),
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyOppdaterVedtaksbrevTilbakekrevingsbehandlingHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        dokumentId = dokumentId,
        vurderingerMedKrav = vurderingerMedKrav,
        utførtAv = utførtAv,
        brevvalg = brevvalg,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
): TilAttesteringHendelse = TilAttesteringHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    id = forrigeHendelse.id,
    utførtAv = utførtAv,
    tidligereHendelseId = forrigeHendelse.hendelseId,
)

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param kravgrunnlagPåSakHendelseId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param dokumentId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param vurderingerMedKrav Ignoreres desom [forrigeHendelse] sendes inn.
 * @param brevvalg Ignoreres desom [forrigeHendelse] sendes inn.
 */
fun nyUnderkjentTilbakekrevingsbehandlingHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    kravgrunnlagPåSakHendelseId: HendelseId,
    dokumentId: UUID = UUID.randomUUID(),
    utførtAv: NavIdentBruker.Attestant = attestant,
    kravgrunnlag: Kravgrunnlag = kravgrunnlag(
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        behandler = utførtAv.toString(),
    ),
    vurderinger: Vurderinger = nyVurderinger(),
    vurderingerMedKrav: VurderingerMedKrav = VurderingerMedKrav.utledFra(
        vurderinger,
        kravgrunnlag,
    ).getOrFail(),
    brevvalg: Brevvalg.SaksbehandlersValg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(
        fritekst = "fritekst",
    ),
    sendtTilAttesteringUtførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyTilbakekrevingsbehandlingTilAttesteringHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        dokumentId = dokumentId,
        vurderingerMedKrav = vurderingerMedKrav,
        utførtAv = sendtTilAttesteringUtførtAv,
        brevvalg = brevvalg,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
    underkjentGrunn: UnderkjennAttesteringsgrunnTilbakekreving = UnderkjennAttesteringsgrunnTilbakekreving.ANDRE_FORHOLD,
    underkjentBegrunnelse: String = "underkjentBegrunnelse",
): UnderkjentHendelse = UnderkjentHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    id = forrigeHendelse.id,
    tidligereHendelseId = forrigeHendelse.hendelseId,
    utførtAv = utførtAv,
    grunn = underkjentGrunn,
    begrunnelse = underkjentBegrunnelse,
)

/**
 * @param sakId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param behandlingId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param kravgrunnlagPåSakHendelseId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param dokumentId Ignoreres desom [forrigeHendelse] sendes inn.
 * @param vurderingerMedKrav Ignoreres desom [forrigeHendelse] sendes inn.
 * @param brevvalg Ignoreres desom [forrigeHendelse] sendes inn.
 */
fun nyIverksattTilbakekrevingsbehandlingHendelse(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    behandlingId: TilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId.generer(),
    kravgrunnlagPåSakHendelseId: HendelseId,
    dokumentId: UUID = UUID.randomUUID(),
    utførtAv: NavIdentBruker.Attestant = attestant,
    kravgrunnlag: Kravgrunnlag = kravgrunnlag(
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        behandler = utførtAv.toString(),
    ),
    vurderinger: Vurderinger = nyVurderinger(),
    vurderingerMedKrav: VurderingerMedKrav = VurderingerMedKrav.utledFra(
        vurderinger,
        kravgrunnlag,
    ).getOrFail(),
    brevvalg: Brevvalg.SaksbehandlersValg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(
        fritekst = "fritekst",
    ),
    sendtTilAttesteringUtførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyTilbakekrevingsbehandlingTilAttesteringHendelse(
        sakId = sakId,
        behandlingId = behandlingId,
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        dokumentId = dokumentId,
        vurderingerMedKrav = vurderingerMedKrav,
        utførtAv = sendtTilAttesteringUtførtAv,
        brevvalg = brevvalg,
    ),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
    vedtakId: UUID = UUID.randomUUID(),
): IverksattHendelse = IverksattHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    id = forrigeHendelse.id,
    utførtAv = utførtAv,
    tidligereHendelseId = forrigeHendelse.hendelseId,
    vedtakId = vedtakId,
)

fun nyAvbruttTilbakekrevingsbehandlingHendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    utførtAv: NavIdentBruker.Saksbehandler = saksbehandler,
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    kravgrunnlagPåSakHendelseId: HendelseId,
    forrigeHendelse: TilbakekrevingsbehandlingHendelse = nyOpprettetTilbakekrevingsbehandlingHendelse(
        kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
    ),
    versjon: Hendelsesversjon = forrigeHendelse.versjon.inc(),
): AvbruttHendelse = AvbruttHendelse(
    hendelseId = hendelseId,
    sakId = forrigeHendelse.sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    id = forrigeHendelse.id,
    tidligereHendelseId = forrigeHendelse.hendelseId,
    utførtAv = utførtAv,
    begrunnelse = "Avbrutt av TilbakekrevingTestData.kt",
)

fun nyVurderinger(
    perioderVurderinger: Nel<Vurderinger.Periodevurdering> = nonEmptyListOf(
        Vurderinger.Periodevurdering(
            periode = januar(2021),
            vurdering = Vurdering.SkalTilbakekreve,
        ),
    ),
): Vurderinger = Vurderinger(perioderVurderinger)

/**
 * @param perioderVurderinger ignoreres dersom [vurderinger] sendes inn.
 */
fun vurderingerMedKrav(
    perioderVurderinger: Nel<Vurderinger.Periodevurdering> = nonEmptyListOf(
        Vurderinger.Periodevurdering(
            periode = januar(2021),
            vurdering = Vurdering.SkalTilbakekreve,
        ),
    ),
    vurderinger: Vurderinger = nyVurderinger(perioderVurderinger = perioderVurderinger),
    kravgrunnlag: Kravgrunnlag = kravgrunnlag(
        kravgrunnlagPåSakHendelseId = HendelseId.generer(),
        behandler = saksbehandler.toString(),
    ),
): VurderingerMedKrav {
    return VurderingerMedKrav.utledFra(
        vurderinger = vurderinger,
        kravgrunnlag = kravgrunnlag,
    ).getOrFail()
}
