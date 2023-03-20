package no.nav.su.se.bakover.web.routes.vedtak

import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagVilkår
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørtRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
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
    val periode: PeriodeJson?,
    val type: String,
    val dokumenttilstand: String,
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
    ;

    override fun toString(): String {
        return beskrivelse
    }
}

internal fun Vedtak.toJson(): VedtakJson {
    return when (this) {
        is VedtakAvslagBeregning -> this.toJson()
        is VedtakAvslagVilkår -> this.toJson()
        is VedtakEndringIYtelse -> this.toJson()
        is Klagevedtak.Avvist -> this.toJson()
    }
}

internal fun Avslagsvedtak.toJson(): VedtakJson = VedtakJson(
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
    type = VedtakTypeJson.AVSLAG.toString(),
    dokumenttilstand = this.dokumenttilstand.toJson(),
)

internal fun VedtakAvslagBeregning.toJson(): VedtakJson = VedtakJson(
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
    type = VedtakTypeJson.AVSLAG.toString(),
    dokumenttilstand = this.dokumenttilstand.toJson(),
)

internal fun VedtakEndringIYtelse.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    beregning = when (this) {
        is VedtakGjenopptakAvYtelse -> null
        is VedtakInnvilgetRevurdering -> this.beregning.toJson()
        is VedtakInnvilgetSøknadsbehandling -> this.beregning.toJson()
        is VedtakOpphørtRevurdering -> this.beregning.toJson()
        is VedtakStansAvYtelse -> null
        is VedtakInnvilgetRegulering -> this.beregning.toJson()
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
        is VedtakGjenopptakAvYtelse -> VedtakTypeJson.GJENOPPTAK_AV_YTELSE.toString()
        is VedtakInnvilgetRevurdering -> VedtakTypeJson.ENDRING.toString()
        is VedtakInnvilgetSøknadsbehandling -> VedtakTypeJson.SØKNAD.toString()
        is VedtakOpphørtRevurdering -> VedtakTypeJson.OPPHØR.toString()
        is VedtakStansAvYtelse -> VedtakTypeJson.STANS_AV_YTELSE.toString()
        is VedtakInnvilgetRegulering -> VedtakTypeJson.REGULERING.toString()
    },
    dokumenttilstand = this.dokumenttilstand.toJson(),
)

internal fun Klagevedtak.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    beregning = null,
    simulering = null,
    attestant = attestant.navIdent,
    saksbehandler = saksbehandler.navIdent,
    utbetalingId = null,
    behandlingId = klage.id,
    sakId = klage.sakId,
    saksnummer = klage.saksnummer.toString(),
    fnr = klage.fnr.toString(),
    periode = null,
    type = VedtakTypeJson.AVVIST_KLAGE.toString(),
    dokumenttilstand = dokumenttilstand.toJson(),
)

internal fun VedtakSomKanRevurderes.toJson(): VedtakJson = when (this) {
    is VedtakEndringIYtelse -> this.toJson()
}

internal fun VedtakSomKanRevurderes.toVedtakTypeJson(): VedtakTypeJson = when (this) {
    is VedtakGjenopptakAvYtelse -> VedtakTypeJson.GJENOPPTAK_AV_YTELSE
    is VedtakInnvilgetRegulering -> VedtakTypeJson.REGULERING
    is VedtakInnvilgetRevurdering -> VedtakTypeJson.ENDRING
    is VedtakInnvilgetSøknadsbehandling -> VedtakTypeJson.SØKNAD
    is VedtakOpphørtRevurdering -> VedtakTypeJson.OPPHØR
    is VedtakStansAvYtelse -> VedtakTypeJson.STANS_AV_YTELSE
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
