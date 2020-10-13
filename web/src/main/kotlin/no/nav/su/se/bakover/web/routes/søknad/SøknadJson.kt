package no.nav.su.se.bakover.web.routes.søknad

import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.Trukket
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import no.nav.su.se.bakover.web.routes.søknad.TrukketSøknadJson.Companion.toTrukketJson
import java.time.format.DateTimeFormatter

internal data class SøknadJson(
    val sakId: String,
    val id: String,
    val søknadInnhold: SøknadInnholdJson,
    val opprettet: String,
    val trukket: TrukketSøknadJson?
)

data class TrukketSøknadJson(
    val tidspunkt: String,
    val saksbehandlerIdent: String
) {
    companion object {
        fun toTrukketJson(trukket: Trukket?): TrukketSøknadJson? {
            if (trukket == null) {
                return null
            }
            return TrukketSøknadJson(
                tidspunkt = trukket.tidspunkt.toString(),
                saksbehandlerIdent = trukket.saksbehandler.navIdent
            )
        }
    }
}

internal fun Søknad.toJson() = SøknadJson(
    sakId = sakId.toString(),
    id = id.toString(),
    søknadInnhold = søknadInnhold.toSøknadInnholdJson(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    trukket = toTrukketJson(trukket)
)
