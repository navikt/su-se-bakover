package no.nav.su.se.bakover.web.routes.vedtak

import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class VedtakJson(
    val id: String,
    val opprettet: String,
    val beregning: BeregningJson?,
    val simulering: SimuleringJson?,
    val attestant: String,
    val saksbehandler: String,
    val utbetalingId: String?,
    val behandlingId: UUID,
    val sakId: UUID,
    val saksnummer: String,
    val fnr: String,
    val periode: PeriodeJson,
    val type: String,
)

internal enum class VedtakTypeJson(private val beskrivelse: String) {
    SØKNAD("SØKNAD"),
    AVSLAG("AVSLAG"),
    ENDRING("ENDRING"),
    INGEN_ENDRING("INGEN_ENDRING"),
    OPPHØR("OPPHØR"),
    STANS_AV_YTELSE("STANS_AV_YTELSE"),
    GJENOPPTAK_AV_YTELSE("GJENOPPTAK_AV_YTELSE"), ;

    override fun toString(): String {
        return beskrivelse
    }
}

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
    beregning = null,
    simulering = null,
    attestant = attestant.navIdent,
    saksbehandler = saksbehandler.navIdent,
    utbetalingId = null,
    behandlingId = behandling.id,
    sakId = behandling.sakId,
    saksnummer = behandling.saksnummer.toString(),
    fnr = behandling.fnr.toString(),
    periode = periode.toJson(),
    type = VedtakTypeJson.AVSLAG.toString()
)

internal fun Vedtak.Avslag.AvslagBeregning.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
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
    type = VedtakTypeJson.AVSLAG.toString()
)

internal fun Vedtak.EndringIYtelse.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    beregning = when (this) {
        is Vedtak.EndringIYtelse.GjenopptakAvYtelse -> null
        is Vedtak.EndringIYtelse.InnvilgetRevurdering -> this.beregning.toJson()
        is Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling -> this.beregning.toJson()
        is Vedtak.EndringIYtelse.OpphørtRevurdering -> this.beregning.toJson()
        is Vedtak.EndringIYtelse.StansAvYtelse -> null
    },
    simulering = simulering.toJson(),
    attestant = attestant.navIdent,
    saksbehandler = saksbehandler.navIdent,
    utbetalingId = utbetalingId.toString(),
    behandlingId = behandling.id,
    sakId = behandling.sakId,
    saksnummer = behandling.saksnummer.toString(),
    fnr = behandling.fnr.toString(),
    periode = periode.toJson(),
    type = when (this) {
        is Vedtak.EndringIYtelse.GjenopptakAvYtelse -> VedtakTypeJson.GJENOPPTAK_AV_YTELSE.toString()
        is Vedtak.EndringIYtelse.InnvilgetRevurdering -> VedtakTypeJson.ENDRING.toString()
        is Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling -> VedtakTypeJson.SØKNAD.toString()
        is Vedtak.EndringIYtelse.OpphørtRevurdering -> VedtakTypeJson.OPPHØR.toString()
        is Vedtak.EndringIYtelse.StansAvYtelse -> VedtakTypeJson.STANS_AV_YTELSE.toString()
    }
)

internal fun Vedtak.IngenEndringIYtelse.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
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
    type = VedtakTypeJson.INGEN_ENDRING.toString()
)

internal fun VedtakSomKanRevurderes.toJson(): VedtakJson = when (this) {
    is Vedtak.EndringIYtelse -> this.toJson()
    is Vedtak.IngenEndringIYtelse -> this.toJson()
}
