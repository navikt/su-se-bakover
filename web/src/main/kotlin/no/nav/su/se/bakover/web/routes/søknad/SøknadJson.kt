package no.nav.su.se.bakover.web.routes.søknad

import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import java.time.format.DateTimeFormatter

internal data class SøknadJson(
    val sakId: String,
    val id: String,
    val søknadInnhold: SøknadInnholdJson,
    val opprettet: String,
    val trukket: Boolean
)

internal fun Søknad.toJson() = SøknadJson(
    sakId = sakId.toString(),
    id = id.toString(),
    søknadInnhold = søknadInnhold.toSøknadInnholdJson(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    trukket = søknadTrukket
)
