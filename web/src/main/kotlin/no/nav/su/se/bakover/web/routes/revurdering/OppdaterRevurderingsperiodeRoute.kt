package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.oppdater.OppdaterRevurderingRequest
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.begrunnelseKanIkkeVæreTom
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.formueSomFørerTilOpphørMåRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.heleRevurderingsperiodenInneholderIkkeVedtak
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.måVelgeInformasjonSomRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.ugyldigÅrsak
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.utenlandsoppholdSomFørerTilOpphørMåRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.uteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeSak
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIngenVedtakSomKanRevurderes
import java.time.LocalDate

internal fun Route.oppdaterRevurderingRoute(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    data class Body(
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
        val årsak: String,
        val begrunnelse: String,
        val informasjonSomRevurderes: List<Revurderingsteg>,
    )

    put("$revurderingPath/{revurderingId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    revurderingService.oppdaterRevurdering(
                        OppdaterRevurderingRequest(
                            revurderingId = revurderingId,
                            periode = Periode.create(
                                fraOgMed = body.fraOgMed,
                                tilOgMed = body.tilOgMed,
                            ),
                            årsak = body.årsak,
                            begrunnelse = body.begrunnelse,
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                            informasjonSomRevurderes = body.informasjonSomRevurderes,
                        ),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Oppdaterte perioden på revurdering med id: $revurderingId")
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(satsFactory))))
                        },
                    )
                }
            }
        }
    }
}

private fun KunneIkkeOppdatereRevurdering.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeOppdatereRevurdering.UgyldigBegrunnelse -> {
            begrunnelseKanIkkeVæreTom
        }

        KunneIkkeOppdatereRevurdering.UgyldigÅrsak -> {
            ugyldigÅrsak
        }

        KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes -> {
            måVelgeInformasjonSomRevurderes
        }

        is KunneIkkeOppdatereRevurdering.FeilVedOppdateringAvRevurdering -> {
            when (val inner = this.feil) {
                Sak.KunneIkkeOppdatereRevurdering.FantIkkeRevurdering -> {
                    fantIkkeRevurdering
                }

                Sak.KunneIkkeOppdatereRevurdering.FantIkkeSak -> {
                    fantIkkeSak
                }

                is Sak.KunneIkkeOppdatereRevurdering.GjeldendeVedtaksdataKanIkkeRevurderes -> {
                    inner.feil.tilResultat()
                }

                is Sak.KunneIkkeOppdatereRevurdering.KunneIkkeOppdatere -> {
                    when (val nested = inner.feil) {
                        Revurdering.KunneIkkeOppdatereRevurdering.KanIkkeEndreÅrsakTilReguleringVedForhåndsvarsletRevurdering -> {
                            HttpStatusCode.BadRequest.errorJson(
                                "Kan ikke oppdatere revurdering med årsak `REGULER_GRUNNBELØP` som er forhåndsvarslet",
                                "kan_ikke_oppdatere_revurdering_med_årsak_reguler_grunnbeløp_som_er_forhåndsvarslet",
                            )
                        }

                        is Revurdering.KunneIkkeOppdatereRevurdering.UgyldigTilstand -> {
                            Feilresponser.ugyldigTilstand(nested.fra, nested.til)
                        }
                    }
                }

                is Sak.KunneIkkeOppdatereRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode -> {
                    uteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(inner.periode)
                }

                is Sak.KunneIkkeOppdatereRevurdering.OpphørteVilkårMåRevurderes -> {
                    inner.feil.tilResultat()
                }
            }
        }
    }
}

internal fun Sak.OpphørtVilkårMåRevurderes.tilResultat(): Resultat {
    return when (this) {
        Sak.OpphørtVilkårMåRevurderes.FormueSomFørerTilOpphørMåRevurderes -> {
            formueSomFørerTilOpphørMåRevurderes
        }

        Sak.OpphørtVilkårMåRevurderes.UtenlandsoppholdSomFørerTilOpphørMåRevurderes -> {
            utenlandsoppholdSomFørerTilOpphørMåRevurderes
        }
    }
}

internal fun Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.tilResultat(): Resultat {
    return when (this) {
        Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.FantIngenVedtakSomKanRevurderes -> {
            fantIngenVedtakSomKanRevurderes
        }

        Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.HeleRevurderingsperiodenInneholderIkkeVedtak -> {
            heleRevurderingsperiodenInneholderIkkeVedtak
        }
    }
}
