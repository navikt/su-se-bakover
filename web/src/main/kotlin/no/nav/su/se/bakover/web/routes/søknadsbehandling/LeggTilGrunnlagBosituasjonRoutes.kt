package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.merge
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.BosituasjonValg
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeBehandling
import no.nav.su.se.bakover.web.routes.Feilresponser.harIkkeEktefelle
import no.nav.su.se.bakover.web.routes.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.routes.grunnlag.tilResultat
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import java.util.UUID

internal fun Route.leggTilGrunnlagBosituasjonRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    data class EpsBody(
        val epsFnr: String?,
    ) {
        fun toLeggTilBosituasjonEpsgrunnlagRequest(behandlingId: UUID): Either<Resultat, LeggTilBosituasjonEpsRequest> {
            return LeggTilBosituasjonEpsRequest(
                behandlingId = behandlingId,
                epsFnr = epsFnr?.let { Fnr(epsFnr) },
            ).right()
        }
    }

    data class BosituasjonBody(
        val bosituasjon: BosituasjonValg,
        val begrunnelse: String?,
    ) {
        fun toFullførBosituasjongrunnlagRequest(behandlingId: UUID): Either<Resultat, FullførBosituasjonRequest> {
            return FullførBosituasjonRequest(
                behandlingId = behandlingId,
                bosituasjon = bosituasjon,
                begrunnelse = begrunnelse,
            ).right()
        }
    }

    post("$behandlingPath/{behandlingId}/grunnlag/bosituasjon/eps") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<EpsBody> { body ->
                    call.svar(
                        body.toLeggTilBosituasjonEpsgrunnlagRequest(behandlingId)
                            .map { leggTilBosituasjonEpsgrunnlagRequest ->
                                søknadsbehandlingService.leggTilBosituasjonEpsgrunnlag(
                                    leggTilBosituasjonEpsgrunnlagRequest,
                                ).fold(
                                    {
                                        when (it) {
                                            SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.FantIkkeBehandling -> fantIkkeBehandling
                                            SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KlarteIkkeHentePersonIPdl -> Feilresponser.fantIkkePerson
                                            is SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KunneIkkeOppdatereBosituasjon -> it.feil.tilResultat()
                                        }
                                    },
                                    {
                                        Resultat.json(
                                            HttpStatusCode.Created,
                                            serialize(it.toJson(satsFactory))
                                        )
                                    },
                                )
                            }.merge(),
                    )
                }
            }
        }
    }

    post("$behandlingPath/{behandlingId}/grunnlag/bosituasjon/eps/skjermet") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<EpsBody> { body ->
                    if (body.epsFnr.isNullOrEmpty()) {
                        return@withBehandlingId call.svar(Feilresponser.ugyldigBody)
                    }
                    søknadsbehandlingService.leggTilBosituasjonEpsgrunnlag(
                        LeggTilBosituasjonEpsRequest(
                            behandlingId = behandlingId,
                            epsFnr = Fnr(body.epsFnr),
                        ),
                    )
                        .fold(
                            {
                                when (it) {
                                    SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.FantIkkeBehandling -> fantIkkeBehandling
                                    SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KlarteIkkeHentePersonIPdl -> Feilresponser.fantIkkePerson
                                    is SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KunneIkkeOppdatereBosituasjon -> it.feil.tilResultat()
                                }
                            },
                            {
                                Resultat.okJson(HttpStatusCode.Created)
                            },
                        ).let { call.svar(it) }
                }
            }
        }
    }

    post("$behandlingPath/{behandlingId}/grunnlag/bosituasjon/fullfør") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<BosituasjonBody> { body ->
                    call.svar(
                        body.toFullførBosituasjongrunnlagRequest(behandlingId)
                            .flatMap { fullføreBosituasjongrunnlagRequest ->
                                søknadsbehandlingService.fullførBosituasjongrunnlag(
                                    fullføreBosituasjongrunnlagRequest,
                                ).mapLeft {
                                    when (it) {
                                        SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.FantIkkeBehandling -> fantIkkeBehandling
                                        SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.KlarteIkkeLagreBosituasjon -> Feilresponser.kunneIkkeLeggeTilBosituasjonsgrunnlag
                                        SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.KlarteIkkeHentePersonIPdl -> Feilresponser.fantIkkePerson
                                        is SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.KunneIkkeEndreBosituasjongrunnlag -> Feilresponser.kunneIkkeLeggeTilBosituasjonsgrunnlag
                                    }
                                }.map {
                                    Resultat.json(
                                        HttpStatusCode.Created,
                                        serialize(it.toJson(satsFactory))
                                    )
                                }
                            }.getOrHandle {
                                it
                            },
                    )
                }
            }
        }
    }
}

internal fun SøknadsbehandlingService.KunneIkkeVilkårsvurdere.tilResultat(): Resultat {
    return when (this) {
        SøknadsbehandlingService.KunneIkkeVilkårsvurdere.FantIkkeBehandling -> fantIkkeBehandling
        is SøknadsbehandlingService.KunneIkkeVilkårsvurdere.FeilVedValideringAvBehandlingsinformasjon -> this.feil.tilResultat()
        SøknadsbehandlingService.KunneIkkeVilkårsvurdere.HarIkkeEktefelle -> harIkkeEktefelle
    }
}

internal fun Søknadsbehandling.KunneIkkeOppdatereBosituasjon.tilResultat(): Resultat {
    return when (this) {
        is Søknadsbehandling.KunneIkkeOppdatereBosituasjon.UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
    }
}

internal fun KunneIkkeHentePerson.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeHentePerson.FantIkkePerson -> Feilresponser.fantIkkePerson
        KunneIkkeHentePerson.IkkeTilgangTilPerson -> Feilresponser.ikkeTilgangTilPerson
        KunneIkkeHentePerson.Ukjent -> Feilresponser.feilVedOppslagPåPerson
    }
}

internal fun KunneIkkeLageGrunnlagsdata.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLageGrunnlagsdata.FradragForEPSMenBosituasjonUtenEPS -> HttpStatusCode.BadRequest.errorJson(
            "Kan ikke legge til fradrag knyttet til EPS for en bruker som ikke har EPS.",
            "fradrag_for_eps_uten_eps",
        )
        KunneIkkeLageGrunnlagsdata.FradragManglerBosituasjon -> HttpStatusCode.BadRequest.errorJson(
            "Alle fradragsperiodene må være innenfor bosituasjonsperioden.",
            "fradragsperiode_utenfor_bosituasjonperiode",
        )
        KunneIkkeLageGrunnlagsdata.MåLeggeTilBosituasjonFørFradrag -> HttpStatusCode.BadRequest.errorJson(
            "Må ha et bosituasjon, før man legger til fradrag",
            "må_ha_bosituasjon_før_fradrag",
        )
        is KunneIkkeLageGrunnlagsdata.UgyldigFradragsgrunnlag -> this.feil.tilResultat()
        is KunneIkkeLageGrunnlagsdata.Konsistenssjekk -> this.feil.tilResultat()
    }
}

internal fun Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.tilResultat(): Resultat {
    return when (this) {
        Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag -> Behandlingsfeilresponser.ugyldigFradragstype
    }
}
