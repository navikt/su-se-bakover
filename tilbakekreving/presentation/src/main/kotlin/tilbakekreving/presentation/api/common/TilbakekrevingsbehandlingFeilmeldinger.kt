package tilbakekreving.presentation.api.common

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.errorJson

internal val ingenUteståendeKravgrunnlag = HttpStatusCode.BadRequest.errorJson(
    "Ingen utestående kravgrunnlag",
    "ingen_utestående_kravgrunnlag",
)

internal val manglerBrukkerroller = HttpStatusCode.InternalServerError.errorJson(
    message = "teknisk feil: Brukeren mangler brukerroller",
    code = "mangler_brukerroller",
)

internal val ikkeTilgangTilSak = HttpStatusCode.Forbidden.errorJson(
    "Ikke tilgang til sak",
    "ikke_tilgang_til_sak",
)

internal val kravgrunnlagetHarEndretSeg = HttpStatusCode.BadRequest.errorJson(
    "Kravgrunnlaget har endret seg siden behandlingen ble opprettet. Kravgrunnlaget må oppdateres på behandlingen.",
    "kravgrunnlaget_har_endret_seg",
)

internal val periodeneIKravgrunnlagetSamsvarerIkkeMedVurderingene = HttpStatusCode.BadRequest.errorJson(
    "Periodene i kravgrunnlaget samsvarer ikke med periodene i vurderingene",
    "kravgrunnlag_samsvarer_ikke_med_vurderinger",
)
