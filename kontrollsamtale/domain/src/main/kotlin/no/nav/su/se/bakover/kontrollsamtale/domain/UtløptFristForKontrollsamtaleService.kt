package no.nav.su.se.bakover.kontrollsamtale.domain

import java.time.LocalDate

interface UtløptFristForKontrollsamtaleService {
    fun håndterUtløpsdato(dato: LocalDate): UtløptFristForKontrollsamtaleContext
}
