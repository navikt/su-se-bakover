package no.nav.su.se.bakover.kontrollsamtale.domain

interface UtløptFristForKontrollsamtaleService {
    fun stansStønadsperioderHvorKontrollsamtaleHarUtløptFrist(): UtløptFristForKontrollsamtaleContext?
}
