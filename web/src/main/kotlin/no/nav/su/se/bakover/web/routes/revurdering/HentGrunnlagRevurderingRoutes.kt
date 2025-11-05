package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withVedtakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import vilkår.formue.domain.FormuegrenserFactory

/**
 * Mulighet for å hente grunnlagene som stod til grunn for revurderingen (før). Dvs, ikke nye grunnlag som er lagt til
 * som en del av revurderingen.
 */
internal fun Route.hentGrunnlagRevurderingRoutes(
    // TODO ai: Flytte denne til "VedtakRoutes" når vi får något sånt
    sakService: SakService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    get("$REVURDERING_PATH/historisk/vedtak/{vedtakId}/grunnlagsdataOgVilkårsvurderinger") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withVedtakId { vedtakId ->
                    sakService.historiskGrunnlagForVedtaketsPeriode(
                        sakId = sakId,
                        vedtakId = vedtakId,
                    ).fold(
                        ifLeft = {
                            call.svar(
                                when (it) {
                                    KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.FantIkkeSak -> {
                                        Feilresponser.fantIkkeSak
                                    }

                                    is KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.Feil -> {
                                        when (it.feil) {
                                            Sak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.FantIkkeVedtak -> {
                                                HttpStatusCode.NotFound.errorJson(
                                                    "Fant ikke vedtak",
                                                    "fant_ikke_vedtak",
                                                )
                                            }

                                            Sak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.IngenTidligereVedtak -> {
                                                HttpStatusCode.NotFound.errorJson(
                                                    "Fant ikke grunnlagsdata for tidligere vedtak",
                                                    "fant_ikke_tidligere_grunnlagsdata",
                                                )
                                            }
                                        }
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
                                            grunnlagsdata = it.grunnlagsdata,
                                            vilkårsvurderinger = it.vilkårsvurderinger,
                                            formuegrenserFactory = formuegrenserFactory,
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
