package no.nav.su.se.bakover.kontrollsamtale.domain

import java.time.LocalDate

interface KontrollsamtaleDriftOversikt {
    fun hentInnkalteKontrollsamtaleForDrift(fristPåDato: LocalDate): List<Kontrollsamtale>
}
