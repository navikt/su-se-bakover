package no.nav.su.se.bakover.web.routes.søknad

import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import java.time.format.DateTimeFormatter

internal data class SøknadJson(
    val id: String,
    val søknadInnhold: SøknadInnholdJson,
    val opprettet: String
)

internal fun Søknad.toJson() = SøknadJson(
    id = id.toString(),
    søknadInnhold = søknadInnhold.toSøknadInnholdJson(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet)
)

// internal fun HttpStatusCode.jsonBody(dtoConvertable: DtoConvertable<SøknadDto>) =
//     Resultat.json(this, objectMapper.writeValueAsString(dtoConvertable.toDto().toJson()))
