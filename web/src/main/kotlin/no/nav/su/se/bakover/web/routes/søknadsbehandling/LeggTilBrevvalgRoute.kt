package no.nav.su.se.bakover.web.routes.søknadsbehandling

import KunneIkkeLeggeTilVedtaksbrevvalgSøknad
import arrow.core.merge
import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.brev.LeggTilBrevvalgRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory

data class LeggTilBrevvalgSøknadsbehandlingBody(
    val valg: LeggTilBrevvalgRequest.Valg,
)

internal fun Route.leggTilBrevvalgSøknadsbehandlingRoute(
    søknadsbehandlingService: SøknadsbehandlingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/brevvalg") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withValidertBody<LeggTilBrevvalgSøknadsbehandlingBody>(log) { body ->
                    either {
                        val søknadsbehandling = søknadsbehandlingService.leggTilBrevvalg(
                            LeggTilBrevvalgRequest(
                                behandlingsId = SøknadsbehandlingId(behandlingId),
                                valg = body.valg,
                                saksbehandler = call.suUserContext.saksbehandler,
                            ),
                        ).mapLeft { kunneIkkeLeggeTilVedtaksbrevvalgSøknad -> kunneIkkeLeggeTilVedtaksbrevvalgSøknad.tilResultat() }.bind()

                        call.sikkerlogg("Oppdaterte brevvalg for søknadsbehandling:$behandlingId")
                        call.audit(søknadsbehandling.fnr, AuditLogEvent.Action.UPDATE, behandlingId)
                        Resultat.json(HttpStatusCode.Created, serialize(søknadsbehandling.toJson(formuegrenserFactory)))
                    }.merge().let { resultat -> call.svar(resultat) }
                }
            }
        }
    }
}

internal fun KunneIkkeLeggeTilVedtaksbrevvalgSøknad.tilResultat(): Resultat {
    return when (val f = this) {
        is KunneIkkeLeggeTilVedtaksbrevvalgSøknad.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(f.tilstand, f.tilstand)
        }
    }
}
