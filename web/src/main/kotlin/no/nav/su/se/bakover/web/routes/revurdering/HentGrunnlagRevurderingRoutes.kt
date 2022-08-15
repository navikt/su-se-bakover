package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.sak.KunneIkkeHenteGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withSakId
import no.nav.su.se.bakover.web.withVedtakId

/**
 * Mulighet for å hente grunnlagene som stod til grunn for revurderingen (før). Dvs, ikke nye grunnlag som er lagt til
 * som en del av revurderingen.
 */
internal fun Route.hentGrunnlagRevurderingRoutes(
    sakService: SakService, // TODO ai: Flytte denne til "VedtakRoutes" når vi får något sånt
    satsFactory: SatsFactory,
) {
    get("$revurderingPath/historisk/vedtak/{vedtakId}/grunnlagsdataOgVilkårsvurderinger") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withVedtakId { vedtakId ->
                    sakService.historiskGrunnlagForVedtaksperiode(
                        sakId = sakId,
                        vedtakId = vedtakId,
                    ).fold(
                        ifLeft = {
                            call.svar(
                                when (it) {
                                    KunneIkkeHenteGjeldendeVedtaksdata.FantIkkeSak -> {
                                        Feilresponser.fantIkkeSak
                                    }

                                    KunneIkkeHenteGjeldendeVedtaksdata.IngenVedtak -> {
                                        HttpStatusCode.NotFound.errorJson(
                                            "Fant ikke grunnlagsdata for tidligere vedtak",
                                            "fant_ikke_tidligere_grunnlagsdata",
                                        )
                                    }
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
