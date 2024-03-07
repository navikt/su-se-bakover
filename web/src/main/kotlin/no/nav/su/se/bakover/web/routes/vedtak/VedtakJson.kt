package no.nav.su.se.bakover.web.routes.vedtak

import dokument.domain.Dokumenttilstand
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.Opphørsvedtak
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagVilkår
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.vedtak.domain.Vedtak
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import tilbakekreving.domain.vedtak.VedtakTilbakekrevingsbehandling
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
    val periode: PeriodeJson?,
    val type: String,
    val dokumenttilstand: String,
    val kanStarteNyBehandling: Boolean,
    val skalSendeBrev: Boolean,
)

internal enum class VedtakTypeJson(private val beskrivelse: String) {
    SØKNAD("SØKNAD"),
    AVSLAG("AVSLAG"),
    ENDRING("ENDRING"),
    REGULERING("REGULERING"),
    OPPHØR("OPPHØR"),
    STANS_AV_YTELSE("STANS_AV_YTELSE"),
    GJENOPPTAK_AV_YTELSE("GJENOPPTAK_AV_YTELSE"),
    AVVIST_KLAGE("AVVIST_KLAGE"),
    TILBAKEKREVING("TILBAKEKREVING"),
    ;

    override fun toString(): String {
        return beskrivelse
    }
}

internal fun Vedtak.toJson(): VedtakJson {
    return when (this) {
        is VedtakAvslagBeregning -> this.toJson()
        is VedtakAvslagVilkår -> this.toJson()
        is Stønadsvedtak -> this.toJson()
        is Klagevedtak.Avvist -> this.toJson()
        is VedtakTilbakekrevingsbehandling -> this.toJson()
        else -> throw IllegalStateException("Vedtak er av ukjent type - ${this::class.simpleName}")
    }
}

internal fun VedtakTilbakekrevingsbehandling.toJson(): VedtakJson = VedtakJson(
    id = this.id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    beregning = null,
    simulering = null,
    attestant = this.attestant.navIdent,
    saksbehandler = this.saksbehandler.navIdent,
    utbetalingId = null,
    behandlingId = this.behandling.id.value,
    periode = null,
    type = VedtakTypeJson.TILBAKEKREVING.toString(),
    dokumenttilstand = this.dokumenttilstand.toJson(),
    kanStarteNyBehandling = false,
    skalSendeBrev = skalSendeBrev,
)

internal fun Avslagsvedtak.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    beregning = null,
    simulering = null,
    attestant = attestant.navIdent,
    saksbehandler = saksbehandler.navIdent,
    utbetalingId = null,
    behandlingId = behandling.id.value,
    periode = periode.toJson(),
    type = VedtakTypeJson.AVSLAG.toString(),
    dokumenttilstand = this.dokumenttilstand.toJson(),
    // avslagsvedtak er per tidspunkt det eneste vedtaket som kan starte en ny form for behandling
    kanStarteNyBehandling = kanStarteNyBehandling,
    skalSendeBrev = skalSendeBrev,
)

internal fun VedtakAvslagBeregning.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    beregning = beregning.toJson(),
    simulering = null,
    attestant = attestant.navIdent,
    saksbehandler = saksbehandler.navIdent,
    utbetalingId = null,
    behandlingId = behandling.id.value,
    periode = periode.toJson(),
    type = VedtakTypeJson.AVSLAG.toString(),
    dokumenttilstand = this.dokumenttilstand.toJson(),
    // avslagsvedtak er per tidspunkt det eneste vedtaket som kan starte en ny form for behandling
    kanStarteNyBehandling = kanStarteNyBehandling,
    skalSendeBrev = skalSendeBrev,
)

internal fun Stønadsvedtak.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    beregning = this.beregning?.toJson(),
    simulering = simulering?.toJson(),
    attestant = attestant.navIdent,
    saksbehandler = saksbehandler.navIdent,
    utbetalingId = utbetalingId?.toString(),
    behandlingId = behandling.id.value,
    periode = periode.toJson(),
    type = this.toVedtakTypeJson().toString(),
    dokumenttilstand = this.dokumenttilstand.toJson(),
    kanStarteNyBehandling = false,
    skalSendeBrev = skalSendeBrev,
)

internal fun Klagevedtak.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    beregning = null,
    simulering = null,
    attestant = attestant.navIdent,
    saksbehandler = saksbehandler.navIdent,
    utbetalingId = null,
    behandlingId = behandling.id.value,
    periode = null,
    type = VedtakTypeJson.AVVIST_KLAGE.toString(),
    dokumenttilstand = dokumenttilstand.toJson(),
    kanStarteNyBehandling = false,
    skalSendeBrev = skalSendeBrev,
)

internal fun Stønadsvedtak.toVedtakTypeJson(): VedtakTypeJson = when (this) {
    is VedtakGjenopptakAvYtelse -> VedtakTypeJson.GJENOPPTAK_AV_YTELSE
    is VedtakInnvilgetRegulering -> VedtakTypeJson.REGULERING
    is VedtakInnvilgetRevurdering -> VedtakTypeJson.ENDRING
    is VedtakInnvilgetSøknadsbehandling -> VedtakTypeJson.SØKNAD
    is Opphørsvedtak -> VedtakTypeJson.OPPHØR
    is VedtakStansAvYtelse -> VedtakTypeJson.STANS_AV_YTELSE
    is Avslagsvedtak -> VedtakTypeJson.AVSLAG
    else -> throw IllegalStateException("Vedtak er av ukjent type - ${this::class.simpleName}")
}

private fun Dokumenttilstand.toJson(): String {
    return when (this) {
        Dokumenttilstand.SKAL_IKKE_GENERERE -> "SKAL_IKKE_GENERERE"
        Dokumenttilstand.IKKE_GENERERT_ENDA -> "IKKE_GENERERT_ENDA"
        Dokumenttilstand.GENERERT -> "GENERERT"
        Dokumenttilstand.JOURNALFØRT -> "JOURNALFØRT"
        Dokumenttilstand.SENDT -> "SENDT"
    }
}
