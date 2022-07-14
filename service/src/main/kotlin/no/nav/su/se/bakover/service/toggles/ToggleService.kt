package no.nav.su.se.bakover.service.toggles

interface ToggleService {

    fun isEnabled(toggleName: String): Boolean

    companion object {
        val toggleSendAutomatiskPåminnelseOmNyStønadsperiode = "supstonad.ufore.automatisk.paaminnelse.ny.stonadsperiode"
        val supstonadAalderInnsending = "supstonad.alder.innsending"
        val supstonadSkattemelding = "supstonad.skattemelding"
    }
}
