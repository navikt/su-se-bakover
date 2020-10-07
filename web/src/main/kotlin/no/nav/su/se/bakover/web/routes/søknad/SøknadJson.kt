package no.nav.su.se.bakover.web.routes.søknad

import no.nav.su.se.bakover.domain.AvsluttSøkndsBehandlingBegrunnelse
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import java.time.format.DateTimeFormatter

internal data class SøknadJson(
    val id: String,
    val søknadInnhold: SøknadInnholdJson,
    val opprettet: String,
    val avsluttetBegrunnelse: AvsluttSøkndsBehandlingBegrunnelse? = null
)

internal fun Søknad.toJson() = SøknadJson(
    id = id.toString(),
    søknadInnhold = søknadInnhold.toSøknadInnholdJson(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    avsluttetBegrunnelse = avsluttetBegrunnelse
)
