package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.opprett.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.web.routes.person.tilResultat
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.måVelgeInformasjonSomRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import vilkår.formue.domain.FormuegrenserFactory
import java.time.LocalDate

internal fun Route.opprettRevurderingRoute(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    data class Body(
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
        val årsak: String,
        val begrunnelse: String,
        val informasjonSomRevurderes: List<Revurderingsteg>,
    )
    post(REVURDERING_PATH) {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    val navIdent = call.suUserContext.navIdent

                    revurderingService.opprettRevurdering(
                        OpprettRevurderingCommand(
                            sakId = sakId,
                            periode = Periode.create(
                                fraOgMed = body.fraOgMed,
                                tilOgMed = body.tilOgMed,
                            ),
                            årsak = body.årsak,
                            begrunnelse = body.begrunnelse,
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                            informasjonSomRevurderes = body.informasjonSomRevurderes,
                        ),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Opprettet en ny revurdering på sak med id $sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.CREATE, it.id.value)
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson(formuegrenserFactory))))
                        },
                    )
                }
            }
        }
    }
}

private fun KunneIkkeOppretteRevurdering.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes -> måVelgeInformasjonSomRevurderes
        is KunneIkkeOppretteRevurdering.OpphørteVilkårMåRevurderes -> this.feil.tilResultat()
        is KunneIkkeOppretteRevurdering.UgyldigRevurderingsårsak -> this.feil.tilResultat()
        is KunneIkkeOppretteRevurdering.VedtakInnenforValgtPeriodeKanIkkeRevurderes -> this.feil.tilResultat()
        is KunneIkkeOppretteRevurdering.FantIkkeAktørId -> this.feil.tilResultat()
        is KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
    }
}
