package tilbakekreving.presentation.api.hent

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import tilbakekreving.application.service.HentÅpentKravgrunnlagService
import tilbakekreving.domain.hent.KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag
import tilbakekreving.presentation.api.common.KravgrunnlagJson.Companion.toStringifiedJson
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak
import tilbakekreving.presentation.api.common.ingenÅpneKravgrunnlag
import tilbakekreving.presentation.consumer.TilbakekrevingsmeldingMapper

internal const val tilbakekrevingPath = "saker/{sakId}/tilbakekreving"

internal fun Route.hentKravgrunnlagRoute(
    hentÅpentKravgrunnlagService: HentÅpentKravgrunnlagService,
) {
    get("$tilbakekrevingPath/sisteKravgrunnlag") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId {
                hentÅpentKravgrunnlagService.hentÅpentKravgrunnlag(
                    sakId = it,
                    kravgrunnlagMapper = TilbakekrevingsmeldingMapper::toKravgrunnlag,
                ).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = { call.svar(Resultat.json(HttpStatusCode.Created, it.toStringifiedJson())) },
                )
            }
        }
    }
}

private fun KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.tilResultat(): Resultat = when (this) {
    KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.FeilVedMappingAvKravgrunnalget -> mappingFeil
    KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.FinnesIngenFerdigBehandledeKravgrunnlag -> ingenÅpneKravgrunnlag
    is KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.IkkeTilgang -> ikkeTilgangTilSak
}

private val mappingFeil = HttpStatusCode.InternalServerError.errorJson(
    "Teknisk feil ved mapping av innholdet",
    "teknisk_feil_ved_mapping_av_kravgrunnlag",
)
