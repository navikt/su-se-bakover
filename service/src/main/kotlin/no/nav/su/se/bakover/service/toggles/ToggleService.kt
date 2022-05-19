package no.nav.su.se.bakover.service.toggles

interface ToggleService {

    fun isEnabled(toggleName: String): Boolean

    companion object {
        /**
         * Toggle for å aktivere mulighet for feilutbetaling/tilbakekrevingsløp.
         */
        val toggleForFeilutbetaling = "supstonad.ufore.feilutbetaling"
        val toggleSendAutomatiskPåminnelseOmNyStønadsperiode = "supstonad.ufore.automatisk.paaminnelse.ny.stonadsperiode"
        val supstonadAalderInnsending = "supstonad.alder.innsending"
    }
}
