package tilbakekreving.presentation.api.iverksett

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.application.service.iverksett.IverksettTilbakekrevingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.iverksettelse.IverksettTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.iverksettelse.KunneIkkeIverksette
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak
import tilbakekreving.presentation.api.common.kravgrunnlagetHarEndretSeg

private data class Body(
    val versjon: Long,
)

internal fun Route.iverksettTilbakekrevingsbehandlingRoute(
    service: IverksettTilbakekrevingService,
) {
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/iverksett") {
        authorize(Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { id ->
                    call.withBody<Body> { body ->
                        service.iverksett(
                            command = IverksettTilbakekrevingsbehandlingCommand(
                                sakId = sakId,
                                tilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId(id),
                                utførtAv = call.suUserContext.attestant,
                                correlationId = call.correlationId,
                                brukerroller = call.suUserContext.roller.toNonEmptyList(),
                                klientensSisteSaksversjon = Hendelsesversjon(body.versjon),
                            ),
                        ).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = { call.svar(Resultat.json(HttpStatusCode.Created, it.toStringifiedJson())) },
                        )
                    }
                }
            }
        }
    }
}

private fun KunneIkkeIverksette.tilResultat(): Resultat = when (this) {
    is KunneIkkeIverksette.IkkeTilgang -> ikkeTilgangTilSak
    is KunneIkkeIverksette.KravgrunnlagetHarEndretSeg -> kravgrunnlagetHarEndretSeg
    KunneIkkeIverksette.UlikVersjon -> Feilresponser.utdatertVersjon
    KunneIkkeIverksette.SaksbehandlerOgAttestantKanIkkeVæreSammePerson -> Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
    KunneIkkeIverksette.KunneIkkeSendeTilbakekrevingsvedtak -> HttpStatusCode.InternalServerError.errorJson(
        "Kunne ikke sende tilbakekrevingsvedtaket til oppdrag. Utenfor OS/UR sin åpningstid? Kan kravgrunnlaget eller statusene ha endret seg? F.eks. ved at nye linjer har blitt sendt inn for denne saken. Meld fra til utviklere, da det også kan være en programmeringsfeil/tolkningsfeil.",
        "kunne_ikke_sende_tilbakekrevingsvedtaket_til_oppdrag",
    )
}
