package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vilkår.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import java.util.UUID

private data class UtlandsoppholdBody(
    val utenlandsopphold: List<UtenlandsoppholdJson>,
) {
    fun toRequest(revurderingId: UUID): Either<Resultat, LeggTilFlereUtenlandsoppholdRequest> {
        return LeggTilFlereUtenlandsoppholdRequest(
            behandlingId = revurderingId,
            request = Nel.fromListUnsafe(
                utenlandsopphold.map {
                    it.toRequest(revurderingId).getOrHandle { feil -> return feil.left() }
                },
            ),
        ).right()
    }
}

private data class UtenlandsoppholdJson(
    val periode: PeriodeJson,
    val status: String,
    val begrunnelse: String?,
) {
    fun toRequest(revurderingId: UUID): Either<Resultat, LeggTilUtenlandsoppholdRequest> {
        return LeggTilUtenlandsoppholdRequest(
            behandlingId = revurderingId,
            periode = periode.toPeriodeOrResultat().getOrHandle { return it.left() },
            status = UtenlandsoppholdStatus.valueOf(status),
            begrunnelse = begrunnelse,
        ).right()
    }
}

internal fun Route.leggTilUtlandsoppholdRoute(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    post("$revurderingPath/{revurderingId}/utenlandsopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<UtlandsoppholdBody> { body ->
                    val req = body.toRequest(revurderingId)
                        .getOrHandle { return@withRevurderingId call.svar(it) }
                    call.svar(
                        revurderingService.leggTilUtenlandsopphold(req).mapLeft {
                            it.tilResultat()
                        }.map {
                            Resultat.json(HttpStatusCode.OK, serialize(it.toJson(satsFactory)))
                        }.getOrHandle { it },
                    )
                }
            }
        }
    }
}

internal fun KunneIkkeLeggeTilUtenlandsopphold.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling -> {
            HttpStatusCode.NotFound.errorJson("Fant ikke revurdering", "fant_ikke_revurdering")
        }
        KunneIkkeLeggeTilUtenlandsopphold.OverlappendeVurderingsperioder -> {
            Feilresponser.overlappendeVurderingsperioder
        }
        KunneIkkeLeggeTilUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig -> {
            Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
        }
        is KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(this.fra, this.til)
        }
        KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode -> {
            Feilresponser.utenforBehandlingsperioden
        }
        KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat -> {
            Feilresponser.alleResultaterMåVæreLike
        }
        KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden -> {
            Feilresponser.måVurdereHelePerioden
        }
    }
}
