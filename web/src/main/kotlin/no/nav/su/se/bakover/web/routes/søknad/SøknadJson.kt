package no.nav.su.se.bakover.web.routes.søknad

import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdJson.Companion.toSøknadsinnholdJson
import java.time.format.DateTimeFormatter

data class OpprettetSøknadJson(
    val saksnummer: Long,
    val søknad: SøknadJson,
)
data class SøknadJson(
    val id: String,
    val sakId: String,
    val søknadInnhold: SøknadsinnholdJson,
    val opprettet: String,
    val lukket: LukketJson?,
    val innsendtAv: String,
)

data class LukketJson(
    val tidspunkt: String,
    val saksbehandler: String,
    val type: String,
    val dokumenttilstand: String,
)

fun Søknad.toJson(): SøknadJson {
    return SøknadJson(
        id = id.toString(),
        sakId = sakId.toString(),
        søknadInnhold = søknadInnhold.toSøknadsinnholdJson(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        innsendtAv = innsendtAv.navIdent,
        lukket = if (this is Søknad.Journalført.MedOppgave.Lukket) this.toLukketJson() else null,
    )
}

internal fun Søknad.Journalført.MedOppgave.Lukket.toLukketJson() = LukketJson(
    tidspunkt = DateTimeFormatter.ISO_INSTANT.format(lukketTidspunkt),
    saksbehandler = lukketAv.toString(),
    type = when (this) {
        is Søknad.Journalført.MedOppgave.Lukket.Avvist -> "AVVIST"
        is Søknad.Journalført.MedOppgave.Lukket.Bortfalt -> "BORTFALT"
        is Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker -> "TRUKKET"
    },
    dokumenttilstand = this.dokumenttilstand.toString(),
)
