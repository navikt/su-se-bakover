package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakType
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
    val beregning: BeregningJson?,
    val simulering: SimuleringJson?,
    val attestant: String,
    val saksbehandler: String,
    val utbetalingId: String?,
    val behandlingId: UUID,
    val sakId: UUID,
    val saksnummer: String,
    val fnr: String,
    val periode: PeriodeJson?,
    val type: VedtakType,
)

internal fun Vedtak.toJson(): VedtakJson {
    return when (this) {
        is Vedtak.Avslag.AvslagBeregning -> this.toJson()
        is Vedtak.Avslag.AvslagVilkår -> this.toJson()
        is Vedtak.EndringIYtelse -> this.toJson()
        is Vedtak.IngenEndringIYtelse -> this.toJson()
    }
}

internal fun Vedtak.Avslag.AvslagVilkår.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    behandlingsinformasjon = behandlingsinformasjon.toJson(),
    beregning = null,
    simulering = null,
    attestant = attestant.navIdent,
    saksbehandler = saksbehandler.navIdent,
    utbetalingId = null,
    behandlingId = behandling.id,
    sakId = behandling.sakId,
    saksnummer = behandling.saksnummer.toString(),
    fnr = behandling.fnr.toString(),
    // TODO jah: Sett denne til perioden hvis vi legger på dette senere
    periode = null,
    type = vedtakType,
)

internal fun Vedtak.Avslag.AvslagBeregning.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    behandlingsinformasjon = behandlingsinformasjon.toJson(),
    beregning = beregning.toJson(),
    simulering = null,
    attestant = attestant.navIdent,
    saksbehandler = saksbehandler.navIdent,
    utbetalingId = null,
    behandlingId = behandling.id,
    sakId = behandling.sakId,
    saksnummer = behandling.saksnummer.toString(),
    fnr = behandling.fnr.toString(),
    periode = beregning.getPeriode().toJson(),
    type = vedtakType,
)

internal fun Vedtak.EndringIYtelse.toJson(): VedtakJson = VedtakJson(
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
    type = vedtakType,
)

internal fun Vedtak.IngenEndringIYtelse.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    behandlingsinformasjon = behandlingsinformasjon.toJson(),
    beregning = beregning.toJson(),
    simulering = null,
    attestant = attestant.navIdent,
    saksbehandler = saksbehandler.navIdent,
    utbetalingId = null,
    behandlingId = behandling.id,
    sakId = behandling.sakId,
    saksnummer = behandling.saksnummer.toString(),
    fnr = behandling.fnr.toString(),
    periode = periode.toJson(),
    type = vedtakType,
)
