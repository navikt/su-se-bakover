package no.nav.su.se.bakover.web.routes.vedtak

import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
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
)

internal enum class VedtakTypeJson(private val beskrivelse: String) {
    SØKNAD("SØKNAD"),
    AVSLAG("AVSLAG"),
    ENDRING("ENDRING"),
    REGULERING("REGULERING"),
    INGEN_ENDRING("INGEN_ENDRING"),
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
        is Avslagsvedtak.AvslagBeregning -> this.toJson()
        is Avslagsvedtak.AvslagVilkår -> this.toJson()
        is VedtakSomKanRevurderes.EndringIYtelse -> this.toJson()
        is VedtakSomKanRevurderes.IngenEndringIYtelse -> this.toJson()
        is Klagevedtak.Avvist -> this.toJson()
    }
}

internal fun Avslagsvedtak.AvslagVilkår.toJson(): VedtakJson = VedtakJson(
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
)

internal fun Avslagsvedtak.AvslagBeregning.toJson(): VedtakJson = VedtakJson(
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
)

internal fun VedtakSomKanRevurderes.EndringIYtelse.toJson(): VedtakJson = VedtakJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    beregning = when (this) {
        is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> null
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> this.beregning.toJson()
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> this.beregning.toJson()
        is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> this.beregning.toJson()
        is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> null
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> this.beregning.toJson()
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
        is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> VedtakTypeJson.GJENOPPTAK_AV_YTELSE.toString()
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> VedtakTypeJson.ENDRING.toString()
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> VedtakTypeJson.SØKNAD.toString()
        is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> VedtakTypeJson.OPPHØR.toString()
        is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> VedtakTypeJson.STANS_AV_YTELSE.toString()
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> VedtakTypeJson.REGULERING.toString()
    },
)

internal fun VedtakSomKanRevurderes.IngenEndringIYtelse.toJson(): VedtakJson = VedtakJson(
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
    type = VedtakTypeJson.INGEN_ENDRING.toString(),
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
)

internal fun VedtakSomKanRevurderes.toJson(): VedtakJson = when (this) {
    is VedtakSomKanRevurderes.EndringIYtelse -> this.toJson()
    is VedtakSomKanRevurderes.IngenEndringIYtelse -> this.toJson()
}

internal fun VedtakSomKanRevurderes.toVedtakTypeJson(): VedtakTypeJson = when (this) {
    is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> VedtakTypeJson.GJENOPPTAK_AV_YTELSE
    is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> VedtakTypeJson.REGULERING
    is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> VedtakTypeJson.ENDRING
    is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> VedtakTypeJson.SØKNAD
    is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> VedtakTypeJson.OPPHØR
    is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> VedtakTypeJson.STANS_AV_YTELSE
    is VedtakSomKanRevurderes.IngenEndringIYtelse -> VedtakTypeJson.INGEN_ENDRING
}
