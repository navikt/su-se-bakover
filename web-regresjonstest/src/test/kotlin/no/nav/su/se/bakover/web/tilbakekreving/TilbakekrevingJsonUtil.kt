package no.nav.su.se.bakover.web.tilbakekreving

import common.presentation.attestering.AttesteringJson
import common.presentation.attestering.UnderkjennelseJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.jwt.DEFAULT_IDENT
import tilbakekreving.presentation.api.common.GrunnlagsperiodeJson
import tilbakekreving.presentation.api.common.KravgrunnlagJson
import tilbakekreving.presentation.api.common.KravgrunnlagStatusJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingStatus
import tilbakekreving.presentation.api.common.VurderingMedKravForPeriodeJson
import tilbakekreving.presentation.api.common.VurderingerMedKravJson

fun lagOpprettTilbakekrevingRespons(
    sakId: String,
    opprettet: Tidspunkt,
    expectedVersjon: Long,
    status: TilbakekrevingsbehandlingStatus = TilbakekrevingsbehandlingStatus.OPPRETTET,
    fritekst: String? = null,
    notat: String? = null,
    sendtTilAttesteringAv: String? = null,
): TilbakekrevingsbehandlingJson = TilbakekrevingsbehandlingJson(
    id = "ignoreres-siden-denne-opprettes-av-tjenesten",
    sakId = sakId,
    opprettet = opprettet,
    opprettetAv = DEFAULT_IDENT,
    kravgrunnlag = null,
    status = status,
    vurderinger = null,
    fritekst = fritekst,
    forhåndsvarselsInfo = emptyList(),
    versjon = expectedVersjon,
    sendtTilAttesteringAv = sendtTilAttesteringAv,
    attesteringer = emptyList(),
    erKravgrunnlagUtdatert = false,
    avsluttetTidspunkt = null,
    notat = notat,
)

fun lagKravgrunnlagRespons(
    summertBetaltSkattForYtelsesgruppen: String = "1192",
    summertBruttoTidligereUtbetalt: Int = 10946,
    summertBruttoNyUtbetaling: Int = 8563,
    summertBruttoFeilutbetaling: Int = 2383,
    summertNettoFeilutbetaling: Int = 1191,
    summertSkattFeilutbetaling: Int = 1192,
    status: KravgrunnlagStatusJson = KravgrunnlagStatusJson.NY,
) = KravgrunnlagJson(
    hendelseId = "ignoreres-siden-denne-opprettes-av-tjenesten",
    eksternKravgrunnlagsId = "123456",
    eksternVedtakId = "654321",
    kontrollfelt = "2021-02-01-02.03.51.456789",
    status = status,
    grunnlagsperiode = listOf(
        GrunnlagsperiodeJson(
            periode = PeriodeJson(
                fraOgMed = "2021-01-01",
                tilOgMed = "2021-01-31",
            ),
            betaltSkattForYtelsesgruppen = summertBetaltSkattForYtelsesgruppen,
            bruttoTidligereUtbetalt = summertBruttoTidligereUtbetalt.toString(),
            bruttoNyUtbetaling = summertBruttoNyUtbetaling.toString(),
            bruttoFeilutbetaling = summertBruttoFeilutbetaling.toString(),
            nettoFeilutbetaling = summertNettoFeilutbetaling.toString(),
            skatteProsent = "50",
            skattFeilutbetaling = summertSkattFeilutbetaling.toString(),
        ),
    ),
    summertBetaltSkattForYtelsesgruppen = summertBetaltSkattForYtelsesgruppen,
    summertBruttoTidligereUtbetalt = summertBruttoTidligereUtbetalt,
    summertBruttoNyUtbetaling = summertBruttoNyUtbetaling,
    summertBruttoFeilutbetaling = summertBruttoFeilutbetaling,
    summertNettoFeilutbetaling = summertNettoFeilutbetaling,
    summertSkattFeilutbetaling = summertSkattFeilutbetaling,
)

fun lagUnderkjentAttesteringJson(
    attestant: String,
    underkjennelse: UnderkjennelseJson,
) = AttesteringJson(
    attestant = attestant,
    underkjennelse = underkjennelse,
    opprettet = Tidspunkt.now(fixedClock),
)

fun lagVurderingerMedKravJson(
    vurdering: String = "SkalTilbakekreve",
    bruttoSkalTilbakekreve: Int = 2383,
    nettoSkalTilbakekreve: Int = 1191,
    bruttoSkalIkkeTilbakekreve: Int = 0,
) = VurderingerMedKravJson(
    eksternKravgrunnlagId = "123456",
    eksternVedtakId = "654321",
    eksternKontrollfelt = "2021-02-01-02.03.51.456789",
    bruttoSkalTilbakekreveSummert = bruttoSkalTilbakekreve,
    nettoSkalTilbakekreveSummert = nettoSkalTilbakekreve,
    bruttoSkalIkkeTilbakekreveSummert = bruttoSkalIkkeTilbakekreve,
    betaltSkattForYtelsesgruppenSummert = 1192,
    bruttoNyUtbetalingSummert = 8563,
    bruttoTidligereUtbetaltSummert = 10946,
    perioder = listOf(
        VurderingMedKravForPeriodeJson(
            periode = PeriodeJson(
                fraOgMed = "2021-01-01",
                tilOgMed = "2021-01-31",
            ),
            vurdering = vurdering,
            betaltSkattForYtelsesgruppen = 1192,
            bruttoTidligereUtbetalt = 10946,
            bruttoNyUtbetaling = 8563,
            bruttoSkalTilbakekreve = bruttoSkalTilbakekreve,
            nettoSkalTilbakekreve = nettoSkalTilbakekreve,
            bruttoSkalIkkeTilbakekreve = bruttoSkalIkkeTilbakekreve,
            skatteProsent = "50",
        ),
    ),
)
