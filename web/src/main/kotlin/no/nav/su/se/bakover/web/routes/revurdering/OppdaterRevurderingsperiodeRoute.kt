package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.merge
import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.Omgjøring.måHaomgjøringsgrunn
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.OppdaterRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.begrunnelseKanIkkeVæreTom
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.måVelgeInformasjonSomRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.ugyldigÅrsak
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory
import java.time.LocalDate

data class OppdaterRevurderingsperiodeBody(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val årsak: String,
    val omgjøringsgrunn: String? = null,
    val informasjonSomRevurderes: List<Revurderingsteg>,
)

internal fun Route.oppdaterRevurderingRoute(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    put("$REVURDERING_PATH/{revurderingId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withValidertBody<OppdaterRevurderingsperiodeBody>(log) { body ->
                    either {
                        val revurdering = revurderingService.oppdaterRevurdering(
                            OppdaterRevurderingCommand(
                                revurderingId = RevurderingId(revurderingId),
                                periode = Periode.create(
                                    fraOgMed = body.fraOgMed,
                                    tilOgMed = body.tilOgMed,
                                ),
                                årsak = body.årsak,
                                omgjøringsgrunn = body.omgjøringsgrunn,
                                saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                informasjonSomRevurderes = body.informasjonSomRevurderes,
                            ),
                        ).mapLeft { kunneIkkeOppdatereRevurdering -> kunneIkkeOppdatereRevurdering.tilResultat() }.bind()

                        call.sikkerlogg("Oppdaterte perioden på revurdering med id: $revurderingId")
                        call.audit(revurdering.fnr, AuditLogEvent.Action.UPDATE, revurdering.id.value)
                        Resultat.json(HttpStatusCode.OK, serialize(revurdering.toJson(formuegrenserFactory)))
                    }.merge().let { resultat -> call.svar(resultat) }
                }
            }
        }
    }
}

private fun KunneIkkeOppdatereRevurdering.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeOppdatereRevurdering.UgyldigÅrsak -> {
            ugyldigÅrsak
        }

        KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes -> {
            måVelgeInformasjonSomRevurderes
        }

        is KunneIkkeOppdatereRevurdering.GjeldendeVedtaksdataKanIkkeRevurderes -> {
            this.underliggende.tilResultat()
        }

        is KunneIkkeOppdatereRevurdering.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(this.fra, this.til)
        }

        is KunneIkkeOppdatereRevurdering.OpphørteVilkårMåRevurderes -> {
            this.underliggende.tilResultat()
        }

        is KunneIkkeOppdatereRevurdering.MåhaOmgjøringsgrunn -> {
            måHaomgjøringsgrunn
        }

        is KunneIkkeOppdatereRevurdering.UgyldigBegrunnelse -> {
            begrunnelseKanIkkeVæreTom
        }
    }
}
