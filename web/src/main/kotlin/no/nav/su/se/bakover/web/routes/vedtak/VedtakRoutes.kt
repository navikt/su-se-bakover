package no.nav.su.se.bakover.web.routes.vedtak

import behandling.søknadsbehandling.presentation.tilResultat
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withVedtakId
import no.nav.su.se.bakover.vedtak.application.NySøknadCommandOmgjøring
import no.nav.su.se.bakover.vedtak.application.VedtakService
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.jsonBody
import vedtak.domain.KunneIkkeStarteNySøknadsbehandling
import vilkår.formue.domain.FormuegrenserFactory

internal const val VEDTAK_PATH = "$SAK_PATH/{sakId}/vedtak"

fun Route.vedtakRoutes(
    vedtakService: VedtakService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    data class Body(
        val omgjøringsårsak: String? = null,
        val omgjøringsgrunn: String? = null,
        val klageId: String? = null,
    )
    post("$VEDTAK_PATH/{vedtakId}/nySoknadsbehandling") {
        call.withSakId { sakId ->
            call.withVedtakId { vedtakId ->
                call.withBody<Body> { body ->
                    vedtakService.startNySøknadsbehandlingForAvslag(
                        sakId,
                        vedtakId,
                        call.suUserContext.saksbehandler,
                        NySøknadCommandOmgjøring(body.omgjøringsårsak, body.omgjøringsgrunn, body.klageId),
                    )
                        .fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = { call.svar(HttpStatusCode.Created.jsonBody(it, formuegrenserFactory)) },
                        )
                }
            }
        }
    }
}

internal fun KunneIkkeStarteNySøknadsbehandling.tilResultat(): Resultat = when (this) {
    KunneIkkeStarteNySøknadsbehandling.FantIkkeVedtak -> Feilresponser.fantIkkeVedtak
    KunneIkkeStarteNySøknadsbehandling.FantIkkeSak -> Feilresponser.fantIkkeSak
    is KunneIkkeStarteNySøknadsbehandling.FeilVedOpprettelseAvSøknadsbehandling -> this.feil.tilResultat()
    KunneIkkeStarteNySøknadsbehandling.VedtakErIkkeAvslag -> BadRequest.errorJson(
        "Kan ikke starte ny søknadsbehandling på et vedtak som ikke er avslag",
        "vedtak_er_ikke_avslag",
    )

    is KunneIkkeStarteNySøknadsbehandling.FeilVedHentingAvPersonForOpprettelseAvOppgave -> Feilresponser.kunneIkkeOppretteOppgave
    KunneIkkeStarteNySøknadsbehandling.FeilVedOpprettelseAvOppgave -> Feilresponser.kunneIkkeOppretteOppgave
    KunneIkkeStarteNySøknadsbehandling.ÅpenBehandlingFinnes -> BadRequest.errorJson(
        "Kan ikke starte ny søknadsbehandling når det allerede finnes en åpen søknadsbehandling",
        "åpen_behandling_finnes",
    )

    KunneIkkeStarteNySøknadsbehandling.MåHaGyldingOmgjøringsgrunn -> Feilresponser.Omgjøring.måHaomgjøringsgrunn
    KunneIkkeStarteNySøknadsbehandling.UgyldigRevurderingsÅrsak -> BadRequest.errorJson(
        "Ugyldig revurderingsårsak",
        "ugyldig_revurderingsårsak",
    )
    KunneIkkeStarteNySøknadsbehandling.KlageErAlleredeKnyttetTilBehandling -> BadRequest.errorJson(
        "Klage er allerede knyttet til en behandling",
        "klage_er_allerede_knyttet_til_behandling",
    )
    KunneIkkeStarteNySøknadsbehandling.KlageErIkkeFerdigstilt -> BadRequest.errorJson(
        "Klagen er ikke ferdigstilt",
        "klage_er_ikke_ferdigstilt",
    )
    KunneIkkeStarteNySøknadsbehandling.KlageMåFinnesForKnytning -> BadRequest.errorJson(
        "Klagen finnes ikke",
        "klagen_finnes_ikke",
    )
    KunneIkkeStarteNySøknadsbehandling.UlikOmgjøringsgrunn -> BadRequest.errorJson(
        "Må ha lik omgjøringsgrunn",
        "ulik_omgjøringsgrunn",
    )

    KunneIkkeStarteNySøknadsbehandling.KlageUgyldigUUID -> BadRequest.errorJson(
        "Ugyldig klage id",
        "klage_ugyldig_uuid",
    )

    KunneIkkeStarteNySøknadsbehandling.IngenAvsluttedeKlageHendelserFraKA ->
        BadRequest.errorJson(
            "Ingen hendelser fra Kabal er registrert av typen avsluttet",
            "ingen_avsluttet_klagehendelser_fra_kabal",
        )

    KunneIkkeStarteNySøknadsbehandling.IngenKlageHendelserFraKA ->
        BadRequest.errorJson(
            "Ingen hendelser fra Kabal er registrert av noen type",
            "ingen_klagehendelser_fra_kabal_finnes",
        )

    KunneIkkeStarteNySøknadsbehandling.IngenTrygderettenAvsluttetHendelser ->
        BadRequest.errorJson(
            "Ingen hendelser fra Kabal er registrert av typen avsluttetTrygderetten, vi trenger denne for å vite at KABAL har ferdigbehandlet hendelsen. Mener du dette er feil så si i fra.",
            "ingen_klagehendelser_fra_kabal_avsluttetTrygderetten",
        )

    KunneIkkeStarteNySøknadsbehandling.KlageErIkkeFerdigstiltOmgjortKlage ->
        BadRequest.errorJson(
            "Klagen er ikke ferdigstilt for omgjøring i vedtaksenhet",
            "klagen_er_ikke_ferdigstilt_for_omgjort_klage",
        )

    KunneIkkeStarteNySøknadsbehandling.KlageErIkkeOversendt ->
        BadRequest.errorJson(
            "Klagen er ikke i oversendt tilstand, oversending kan ta litt tid.",
            "klagen_er_ikke_oversendt",
        )

    KunneIkkeStarteNySøknadsbehandling.MåhaOmgjøringsgrunn ->
        BadRequest.errorJson(
            "Må ha omgjøringsgrunn",
            "må_ha_omgjøringsgrunn",
        )

    KunneIkkeStarteNySøknadsbehandling.UgyldigBegrunnelseRevurderingsÅrsak ->
        BadRequest.errorJson(
            "Ugyldig begrunnelse for revurderingsårsak",
            "ugyldig_begrunnelse_revurderingsårsak",
        )

    KunneIkkeStarteNySøknadsbehandling.UgyldigÅrsakRevurderingsÅrsak ->
        BadRequest.errorJson(
            "Ugyldig årsak for revurdering",
            "ugyldig_årsak_revurderingsårsak",
        )
}
