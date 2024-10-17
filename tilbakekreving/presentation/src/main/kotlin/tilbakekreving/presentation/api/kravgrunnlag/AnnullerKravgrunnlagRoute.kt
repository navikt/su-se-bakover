package tilbakekreving.presentation.api.kravgrunnlag

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.lesUUID
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.application.service.kravgrunnlag.AnnullerKravgrunnlagService
import tilbakekreving.application.service.kravgrunnlag.KunneIkkeAnnullereKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.AnnullerKravgrunnlagCommand
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toJson
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak

internal fun Route.annullerKravgrunnlagRoute(
    service: AnnullerKravgrunnlagService,
) {
    data class Body(val versjon: Long)

    patch("$TILBAKEKREVING_PATH/kravgrunnlag/{kravgrunnlagHendelseId}/annuller") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.lesUUID("kravgrunnlagHendelseId").fold(
                    ifLeft = {
                        call.svar(
                            HttpStatusCode.BadRequest.errorJson(
                                it,
                                "kravgrunnlagHendelseId_mangler_eller_feil_format",
                            ),
                        )
                    },
                    ifRight = { kravgrunnlagHendelseId ->
                        call.withBody<Body> {
                            service.annuller(
                                command = AnnullerKravgrunnlagCommand(
                                    sakId = sakId,
                                    correlationId = call.correlationId,
                                    brukerroller = call.suUserContext.roller,
                                    annullertAv = call.suUserContext.saksbehandler,
                                    kravgrunnlagHendelseId = HendelseId.fromUUID(kravgrunnlagHendelseId),
                                    klientensSisteSaksversjon = Hendelsesversjon(it.versjon),
                                ),
                            ).fold(
                                ifLeft = { call.svar(it.tilResultat()) },
                                ifRight = {
                                    call.svar(
                                        Resultat.json(
                                            HttpStatusCode.OK,
                                            serialize(
                                                AnnullerResponse(tilbakekrevingsbehandling = it?.toJson()),
                                            ),
                                        ),
                                    )
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}

data class AnnullerResponse(
    val tilbakekrevingsbehandling: TilbakekrevingsbehandlingJson?,
)

internal fun KunneIkkeAnnullereKravgrunnlag.tilResultat(): Resultat = when (this) {
    is KunneIkkeAnnullereKravgrunnlag.IkkeTilgang -> ikkeTilgangTilSak
    KunneIkkeAnnullereKravgrunnlag.BehandlingenErIFeilTilstandForÅAnnullere -> HttpStatusCode.BadRequest.errorJson(
        "Behandlingen er i en tilstand som ikke tillater å annullere kravgrunnlaget",
        "feil_tilstand_for_å_annullere_kravgrunnlag",
    )

    KunneIkkeAnnullereKravgrunnlag.FantIkkeKravgrunnlag -> HttpStatusCode.BadRequest.errorJson(
        "Fant ikke kravgrunnlag",
        "fant_ikke_kravgrunnlag",
    )

    is KunneIkkeAnnullereKravgrunnlag.FeilMotTilbakekrevingskomponenten -> HttpStatusCode.InternalServerError.errorJson(
        "Teknisk feil mot tilbakekrevingskomponenten",
        "teknisk_feil_tilbakekrevingskomponent",
    )

    KunneIkkeAnnullereKravgrunnlag.InnsendtHendelseIdErIkkeDenSistePåSaken -> HttpStatusCode.BadRequest.errorJson(
        "Innsendt hendelseId for kravgrunnlaget er ikke den siste på saken",
        "hendelseId_er_ikke_siste_på_saken",
    )

    KunneIkkeAnnullereKravgrunnlag.SakenHarIkkeKravgrunnlagSomKanAnnulleres -> HttpStatusCode.BadRequest.errorJson(
        "Saken har ikke kravgrunnlag som kan annulleres",
        "saken_har_ikke_kravgrunnlag_som_kan_annulleres",
    )
}
