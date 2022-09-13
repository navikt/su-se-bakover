package no.nav.su.se.bakover.web.routes.revurdering.avslutt

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.routes.brev.SaksbehandlerBrevvalgJson

/**
 * Dersom
 *
 * @param fritekst null dersom SKAL_IKKE_SENDE_BREV og utfylt dersom SKAL_SENDE_BREV_MED_FRITEKST.
 * @param begrunnelse Skal inneholde informasjon om hvorfor saksbehandler valgte å avslutte revurderingen. Dersom saksbehandler velger at det ikke skal sendes brev, skal dette og begrunnes her.
 */
internal data class AvsluttRevurderingRequestJson(
    val begrunnelse: String,
    val fritekst: String?,
    val brevvalg: SaksbehandlerBrevvalgJson?,
) {
    fun toBrevvalg(): Either<Resultat, Brevvalg.SaksbehandlersValg?> {
        // Så lenge avslutt revurdering er 1-1 med brevvalg, kan vi ta denne snarveien her.
        return when (brevvalg) {
            SaksbehandlerBrevvalgJson.SKAL_SENDE_BREV_MED_FRITEKST -> {
                fritekst?.let {
                    Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst(it).right()
                } ?: BadRequest.errorJson(
                    message = "Mangler fritekst",
                    code = "mangler_fritekst",
                ).left()
            }
            SaksbehandlerBrevvalgJson.SKAL_IKKE_SENDE_BREV -> {
                Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev(begrunnelse).right()
            }
            null -> null.right()
        }
    }
}
