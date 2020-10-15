package no.nav.su.se.bakover.web.routes.søknad

import no.nav.su.se.bakover.database.søknad.LukketSøknadJson
import no.nav.su.se.bakover.database.søknad.LukketSøknadJson.Companion.toJson
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import java.time.format.DateTimeFormatter

internal data class SøknadJson(
    val id: String,
    val søknadInnhold: SøknadInnholdJson,
    val opprettet: String,
    val lukket: LukketSøknadJson?
)

internal fun Søknad.toJson() = SøknadJson(
    id = id.toString(),
    søknadInnhold = søknadInnhold.toSøknadInnholdJson(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    lukket = lukket?.toJson()
)
