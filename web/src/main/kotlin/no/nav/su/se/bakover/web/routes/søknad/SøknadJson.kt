package no.nav.su.se.bakover.web.routes.søknad

import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import java.time.format.DateTimeFormatter

internal data class OpprettetSøknadJson(
    val saksnummer: Long,
    val søknad: SøknadJson
)
internal data class SøknadJson(
    val id: String,
    val sakId: String,
    val søknadInnhold: SøknadInnholdJson,
    val opprettet: String,
    val lukket: LukketJson?
)

data class LukketJson(
    val tidspunkt: String,
    val saksbehandler: String,
    val type: String
)

internal fun Søknad.toJson(): SøknadJson {
    return SøknadJson(
        id = id.toString(),
        sakId = sakId.toString(),
        søknadInnhold = søknadInnhold.toSøknadInnholdJson(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        lukket = if (this is Søknad.Lukket) this.toJson() else null
    )
}

internal fun Søknad.Lukket.toJson() = LukketJson(
    tidspunkt = DateTimeFormatter.ISO_INSTANT.format(lukketTidspunkt),
    saksbehandler = lukketAv.toString(),
    type = lukketType.toString()
)
