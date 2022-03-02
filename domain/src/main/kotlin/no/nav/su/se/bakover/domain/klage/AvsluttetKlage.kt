package no.nav.su.se.bakover.domain.klage

import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker

/**
 * Representerer en feilregistrert klage. Eksempler kan være:
 * - Klagen var tillagt feil person.
 * - Klagen var allerede registrert fra før.
 * - Journalpost eller dato NAV mottok kloagen på er feilregistrert.
 * - Klagen ble håndtert på annet vis. F.eks. manuelt via Gosys.
 */
data class AvsluttetKlage(
    // Ønsker å skille oss fra konseptet forrigeSteg her, siden vi støtter veldig mange forskjellige steg og ikke bare ett som i de andre tilfellene for klage.
    private val underliggendeKlage: Klage,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    val begrunnelse: String,
    val tidspunktAvsluttet: Tidspunkt,
) : Klage by underliggendeKlage {

    /**
     * Skal kun kalles av intergrasjonslagene for å avgjøre typen (og tester for å forenkle).
     * Egentlig ønsker vi ikke eksponere selve feltet, men vi trenger å avgjøre den underliggende typen for å instansiere/serialisere den.
     * */
    fun hentUnderliggendeKlage() = underliggendeKlage

    override fun erÅpen() = false

    override fun kanAvsluttes() = false

    override fun avslutt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ) = KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
}
