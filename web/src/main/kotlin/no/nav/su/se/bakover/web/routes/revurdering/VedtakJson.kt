package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.domain.vedtak.IVedtakSomGirUtbetaling
import no.nav.su.se.bakover.web.routes.behandling.BehandlingsinformasjonJson
import no.nav.su.se.bakover.web.routes.behandling.BehandlingsinformasjonJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.behandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.toJson
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class VedtakJson(
    val id: String,
    val opprettet: String,
    val behandlingsinformasjon: BehandlingsinformasjonJson,
    val beregning: BeregningJson,
    val simulering: SimuleringJson,
    val attestant: String,
    val saksbehandler: String,
    val utbetalingId: String,
    val behandlingId: UUID,
    val sakId: UUID,
    val saksnummer: String,
    val fnr: String,
    val periode: PeriodeJson,
)

internal fun IVedtakSomGirUtbetaling.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    behandlingsinformasjon = behandlingsinformasjon.toJson(),
    beregning = beregning.toJson(),
    simulering = simulering.toJson(),
    attestant = attestant.navIdent,
    saksbehandler = saksbehandler.navIdent,
    utbetalingId = utbetalingId.toString(),
    behandlingId = behandling.id,
    sakId = behandling.sakId,
    saksnummer = behandling.saksnummer.toString(),
    fnr = behandling.fnr.toString(),
    periode = periode.toJson(),
)
