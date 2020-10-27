package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import java.time.Instant
import java.time.format.DateTimeFormatter

data class LukketSøknadJson(
    val tidspunkt: String,
    val saksbehandler: String,
    val type: LukketType
) {
    enum class LukketType(val value: String) {
        TRUKKET("TRUKKET"),
        BORTFALT("BORTFALT")
    }

    companion object {
        fun Søknad.Lukket.toJson() = LukketSøknadJson(
            tidspunkt = DateTimeFormatter.ISO_INSTANT.format(tidspunkt),
            saksbehandler = saksbehandler.toString(),
            type = when (this) {
                is Søknad.Lukket.Trukket -> LukketType.TRUKKET
                is Søknad.Lukket.Bortfalt -> LukketType.BORTFALT
            }
        )
    }

    fun toLukket() = when (type) {
        LukketType.TRUKKET -> Søknad.Lukket.Trukket(
            tidspunkt = Instant.parse(tidspunkt).toTidspunkt(),
            saksbehandler = Saksbehandler(saksbehandler)
        )
        LukketType.BORTFALT -> Søknad.Lukket.Bortfalt(
            tidspunkt = Instant.parse(tidspunkt).toTidspunkt(),
            saksbehandler = Saksbehandler(saksbehandler)
        )
    }
}
