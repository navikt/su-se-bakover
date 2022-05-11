package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vedtak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId
import no.nav.su.se.bakover.web.withVedtakId

/**
 * Mulighet for å hente grunnlagene som stod til grunn for revurderingen (før). Dvs, ikke nye grunnlag som er lagt til
 * som en del av revurderingen.
 * //TODO vurder om dette endepunktet burde tilhøre sak
 * // TODO ai: Byt denne route til å bruke endepunktet som finnes på sak.
 */
internal fun Route.hentGrunnlagRevurderingRoutes(
    revurderingService: RevurderingService,
    vedtakService: VedtakService, // TODO ai: Flytte denne til "VedtakRoutes" når vi får något sånt
    satsFactory: SatsFactory,
) {
    get("$revurderingPath/{revurderingId}/grunnlagsdataOgVilkårsvurderinger") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->

                call.svar(
                    revurderingService.hentGjeldendeGrunnlagsdataOgVilkårsvurderinger(revurderingId)
                        .mapLeft {
                            when (it) {
                                KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger.FantIkkeBehandling -> Revurderingsfeilresponser.fantIkkeRevurdering
                                KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger.FantIkkeSak -> Revurderingsfeilresponser.fantIkkeSak
                                KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger.FantIngentingSomKanRevurderes -> Revurderingsfeilresponser.fantIngenVedtakSomKanRevurderes
                                is KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger.UgyldigPeriode -> Revurderingsfeilresponser.ugyldigPeriode(
                                    it.subError,
                                )
                            }
                        }.map {
                            Resultat.json(
                                HttpStatusCode.OK,
                                serialize(
                                    GrunnlagsdataOgVilkårsvurderingerJson.create(
                                        grunnlagsdata = it.grunnlagsdata,
                                        vilkårsvurderinger = it.vilkårsvurderinger,
                                        satsFactory = satsFactory,
                                    ),
                                ),
                            )
                        }.getOrHandle {
                            it
                        },
                )
            }
        }
    }

    get("$revurderingPath/historisk/vedtak/{vedtakId}/grunnlagsdataOgVilkårsvurderinger") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withVedtakId { vedtakId ->
                    vedtakService.historiskGrunnlagForVedtaksperiode(sakId, vedtakId).fold(
                        ifLeft = {
                            call.svar(
                                when (it) {
                                    KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.FantIkkeVedtak -> HttpStatusCode.NotFound.errorJson(
                                        "Fant ikke vedtak",
                                        "fant_ikke_vedtak",
                                    )
                                    KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.IngenTidligereVedtak -> HttpStatusCode.NotFound.errorJson(
                                        "Fant ikke grunnlagsdata for tidligere vedtak",
                                        "fant_ikke_tidligere_grunnlagsdata",
                                    )
                                },
                            )
                        },
                        ifRight = {
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.OK,
                                    serialize(
                                        GrunnlagsdataOgVilkårsvurderingerJson.create(
                                            it.grunnlagsdata,
                                            it.vilkårsvurderinger,
                                            satsFactory = satsFactory,
                                        ),
                                    ),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}
