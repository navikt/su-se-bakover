package no.nav.su.se.bakover.kontrollsamtale.application

import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleDriftOversikt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleRepo
import java.time.LocalDate

class KontrollsamtaleDriftOversiktImpl(
    private val kontrollsamtaleRepo: KontrollsamtaleRepo,
) : KontrollsamtaleDriftOversikt {
    override fun hentInnkalteKontrollsamtaleForDrift(fristPåDato: LocalDate): List<Kontrollsamtale> {
        return kontrollsamtaleRepo.hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato)
    }
}
