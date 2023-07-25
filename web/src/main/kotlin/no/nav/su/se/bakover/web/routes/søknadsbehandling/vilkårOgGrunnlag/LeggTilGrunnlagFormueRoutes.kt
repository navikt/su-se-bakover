package no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.depositumErHøyereEnnInnskudd
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageFormueVerdier
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.web.routes.grunnlag.FormuegrunnlagJson
import no.nav.su.se.bakover.web.routes.grunnlag.tilResultat
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.søknadsbehandlingPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.FormueBody.Companion.toServiceRequest
import java.time.Clock
import java.util.UUID

private data class FormueBody(
    val periode: PeriodeJson,
    val epsFormue: FormuegrunnlagJson.VerdierJson?,
    val søkersFormue: FormuegrunnlagJson.VerdierJson,
    val begrunnelse: String?,
    val måInnhenteMerInformasjon: Boolean,
) {

    companion object {
        private fun lagFormuegrunnlag(json: FormuegrunnlagJson.VerdierJson) =
            Formuegrunnlag.Verdier.tryCreate(
                verdiIkkePrimærbolig = json.verdiIkkePrimærbolig,
                verdiEiendommer = json.verdiEiendommer,
                verdiKjøretøy = json.verdiKjøretøy,
                innskudd = json.innskudd,
                verdipapir = json.verdipapir,
                pengerSkyldt = json.pengerSkyldt,
                kontanter = json.kontanter,
                depositumskonto = json.depositumskonto,
            )

        fun List<FormueBody>.toServiceRequest(
            behandlingId: UUID,
            saksbehandler: NavIdentBruker.Saksbehandler,
            clock: Clock,
        ): Either<Resultat, LeggTilFormuevilkårRequest> {
            if (this.isEmpty()) {
                return HttpStatusCode.BadRequest.errorJson(
                    "Formueliste kan ikke være tom",
                    "formueliste_kan_ikke_være_tom",
                ).left()
            }

            return LeggTilFormuevilkårRequest(
                behandlingId = behandlingId,
                formuegrunnlag = this.map { formueBody ->
                    LeggTilFormuevilkårRequest.Grunnlag.Søknadsbehandling(
                        periode = formueBody.periode.toPeriodeOrResultat()
                            .getOrElse { return it.left() },
                        epsFormue = formueBody.epsFormue?.let {
                            lagFormuegrunnlag(formueBody.epsFormue).getOrElse {
                                return it.tilResultat().left()
                            }
                        },
                        søkersFormue = lagFormuegrunnlag(formueBody.søkersFormue).getOrElse {
                            return it.tilResultat().left()
                        },
                        begrunnelse = formueBody.begrunnelse,
                        måInnhenteMerInformasjon = formueBody.måInnhenteMerInformasjon,
                    )
                }.toNonEmptyList(),
                saksbehandler = saksbehandler,
                tidspunkt = Tidspunkt.now(clock),
            ).right()
        }
    }
}

internal fun Route.leggTilFormueForSøknadsbehandlingRoute(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
    clock: Clock,
) {
    post("$søknadsbehandlingPath/{behandlingId}/formuegrunnlag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBehandlingId { behandlingId ->
                    call.withBody<List<FormueBody>> { body ->
                        body.toServiceRequest(behandlingId, call.suUserContext.saksbehandler, clock).mapLeft {
                            return@authorize call.svar(it)
                        }.map { request ->
                            søknadsbehandlingService.leggTilFormuevilkår(request)
                                .map {
                                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                    call.sikkerlogg("Lagret formue for revudering $behandlingId på $sakId")
                                    return@authorize call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(satsFactory))))
                                }.mapLeft { kunneIkkeLeggeTilFormuegrunnlag ->
                                    return@authorize call.svar(kunneIkkeLeggeTilFormuegrunnlag.tilResultat())
                                }
                        }
                    }
                }
            }
        }
    }
}

private fun KunneIkkeLageFormueVerdier.tilResultat() = when (this) {
    KunneIkkeLageFormueVerdier.DepositumErStørreEnnInnskudd -> depositumErHøyereEnnInnskudd
    KunneIkkeLageFormueVerdier.VerdierKanIkkeVæreNegativ -> HttpStatusCode.BadRequest.errorJson(
        "Verdier kan ikke være negativ",
        "verdier_kan_ikke_være_negativ",
    )
}

internal fun KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår.tilResultat() = when (this) {
    is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår.KunneIkkeMappeTilDomenet -> this.feil.tilResultat()
    is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
        fra = this.fra,
        til = this.til,
    )
}
