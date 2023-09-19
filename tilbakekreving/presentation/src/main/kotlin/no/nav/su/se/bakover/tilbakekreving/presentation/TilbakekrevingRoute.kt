package no.nav.su.se.bakover.tilbakekreving.presentation

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.tilbakekreving.domain.KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag
import no.nav.su.se.bakover.tilbakekreving.domain.KunneIkkeOppretteTilbakekrevingsbehandling
import no.nav.su.se.bakover.tilbakekreving.domain.ManuellTilbakekrevingService
import no.nav.su.se.bakover.tilbakekreving.presentation.KravgrunnlagJson.Companion.toStringifiedJson
import no.nav.su.se.bakover.tilbakekreving.presentation.TilbakekrevingsbehandlingJson.Companion.toJson

internal const val tilbakekrevingPath = "saker/{sakId}/tilbakekreving"

fun Route.tilbakekrevingRoute(tilbakekrevingService: ManuellTilbakekrevingService) {
    get("$tilbakekrevingPath/sisteKravgrunnlag") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId {
                tilbakekrevingService.hentAktivKravgrunnlag(
                    sakId = it,
                    kravgrunnlagMapper = TilbakekrevingsmeldingMapper::toKravgrunnlg,
                ).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = { call.svar(Resultat.json(HttpStatusCode.Created, it.toStringifiedJson())) },
                )
            }
        }
    }

    post("$tilbakekrevingPath/ny") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId {
                tilbakekrevingService.ny(
                    sakId = it,
                    kravgrunnlagMapper = TilbakekrevingsmeldingMapper::toKravgrunnlg,
                ).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = { call.svar(Resultat.json(HttpStatusCode.Created, it.toJson())) },
                )
            }
        }
    }
}

val mappingFeil = HttpStatusCode.InternalServerError.errorJson(
    "Teknisk feil ved mapping av innholdet",
    "teknisk_feil_ved_mapping_av_kravgrunnlag",
)

val finnesIkkeFeil = HttpStatusCode.NotFound.errorJson(
    "Ingen ferdig behandlede kravgrunnlag",
    "ingen_ferdig_behandlede_kravgrunnlag",
)

internal fun KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.tilResultat(): Resultat = when (this) {
    KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.FeilVedMappingAvKravgrunnalget -> mappingFeil
    KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.FinnesIngenFerdigBehandledeKravgrunnlag -> finnesIkkeFeil
}

internal fun KunneIkkeOppretteTilbakekrevingsbehandling.tilResultat(): Resultat = when (this) {
    KunneIkkeOppretteTilbakekrevingsbehandling.FeilVedMappingAvKravgrunnalget -> mappingFeil
    KunneIkkeOppretteTilbakekrevingsbehandling.FinnesIngenFerdigBehandledeKravgrunnlag -> finnesIkkeFeil
}
