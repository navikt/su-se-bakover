package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import java.time.Instant
import java.time.format.DateTimeFormatter

data class LukketSøknadJson(
    val tidspunkt: String,
    val saksbehandler: String,
    val begrunnelse: String,
    val type: String
) {
    companion object {
        fun Søknad.Lukket.toJson() = LukketSøknadJson(
            tidspunkt = DateTimeFormatter.ISO_INSTANT.format(tidspunkt),
            saksbehandler = saksbehandler.toString(),
            begrunnelse = begrunnelse,
            type = when (this) {
                is Søknad.Lukket.Trukket -> "TRUKKET"
            }
        )
    }

    fun toLukket() = when (type) {
        "TRUKKET" -> Søknad.Lukket.Trukket(
            tidspunkt = Instant.parse(tidspunkt).toTidspunkt(),
            saksbehandler = Saksbehandler(saksbehandler),
            begrunnelse = begrunnelse
        )
        else -> throw IllegalStateException("Ugyldig tilstand")
    }
}
