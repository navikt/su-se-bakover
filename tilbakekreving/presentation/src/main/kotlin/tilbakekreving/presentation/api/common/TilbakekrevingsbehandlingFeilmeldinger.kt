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

// TODO jah: flytt til person infra/presentation
internal val ikkeTilgangTilSak = HttpStatusCode.Forbidden.errorJson(
    "Ikke tilgang til sak",
    "ikke_tilgang_til_sak",
)

internal val kravgrunnlagetHarEndretSeg = HttpStatusCode.BadRequest.errorJson(
    "Kravgrunnlaget har endret seg siden behandlingen ble opprettet. Kravgrunnlaget må oppdateres på behandlingen.",
    "kravgrunnlaget_har_endret_seg",
)
