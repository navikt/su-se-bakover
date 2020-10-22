package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class LukketSøknadJson(
    val tidspunkt: String,
    val saksbehandler: String,
    val typeLukking: TypeLukking,
    val datoSøkerTrakkSøknad: LocalDate? = null
) {

    enum class TypeLukking() {
        Trukket
    }

    companion object {
        fun Søknad.Lukket.toJson() = LukketSøknadJson(
            tidspunkt = DateTimeFormatter.ISO_INSTANT.format(tidspunkt),
            saksbehandler = saksbehandler.toString(),
            typeLukking = when (this) {
                is Søknad.Lukket.Trukket -> TypeLukking.Trukket
            },
            datoSøkerTrakkSøknad = this.datoSøkerTrakkSøknad
        )
    }

    fun toLukket() = when (typeLukking) {
        TypeLukking.Trukket -> Søknad.Lukket.Trukket(
            tidspunkt = Instant.parse(tidspunkt).toTidspunkt(),
            saksbehandler = Saksbehandler(saksbehandler),
            datoSøkerTrakkSøknad = datoSøkerTrakkSøknad!!
        )
    }
}
