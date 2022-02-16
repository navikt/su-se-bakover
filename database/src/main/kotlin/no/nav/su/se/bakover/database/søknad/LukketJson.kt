package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.søknad.Søknad

data class LukketJson(
    val tidspunkt: Tidspunkt,
    val saksbehandler: String,
    val type: Søknad.Journalført.MedOppgave.Lukket.LukketType
) {
    companion object {
        fun Søknad.Journalført.MedOppgave.Lukket.toLukketJson() = LukketJson(
            tidspunkt = this.lukketTidspunkt,
            saksbehandler = this.lukketAv.toString(),
            type = this.lukketType,
        )
    }
}
