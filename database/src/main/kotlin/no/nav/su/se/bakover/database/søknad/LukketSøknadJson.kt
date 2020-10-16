package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import java.time.Instant
import java.time.format.DateTimeFormatter

data class LukketSøknadJson(
    val tidspunkt: String,
    val saksbehandler: String,
    val type: Søknad.TypeLukking
) {

    companion object {
        fun Søknad.Lukket.toJson() = LukketSøknadJson(
            tidspunkt = DateTimeFormatter.ISO_INSTANT.format(tidspunkt),
            saksbehandler = saksbehandler.toString(),
            type = when (this) {
                is Søknad.Lukket.Trukket -> Søknad.TypeLukking.Trukket
            }
        )
    }

    fun toLukket() = when (type) {
        Søknad.TypeLukking.Trukket -> Søknad.Lukket.Trukket(
            tidspunkt = Instant.parse(tidspunkt).toTidspunkt(),
            saksbehandler = Saksbehandler(saksbehandler),
            typeLukking = Søknad.TypeLukking.Trukket
        )
    }
}
