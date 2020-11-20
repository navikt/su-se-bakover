package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Søknad

data class LukketJson(
    val tidspunkt: Tidspunkt,
    val saksbehandler: String,
    val type: Søknad.Lukket.LukketType,
    val journalpostId: String?,
    val brevbestillingId: String?
) {
    companion object {
        fun Søknad.Lukket.toLukketJson() = LukketJson(
            tidspunkt = this.lukketTidspunkt,
            saksbehandler = this.lukketAv.toString(),
            type = this.lukketType,
            journalpostId = this.lukketJournalpostId.toString(),
            brevbestillingId = this.lukketBrevbestillingId.toString()
        )
    }
}
