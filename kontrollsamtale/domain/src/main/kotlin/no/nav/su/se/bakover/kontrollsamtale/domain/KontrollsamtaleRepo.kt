package no.nav.su.se.bakover.kontrollsamtale.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.time.LocalDate
import java.util.UUID

interface KontrollsamtaleRepo {
    fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext? = null)
    fun hentForSakId(sakId: UUID, sessionContext: SessionContext? = null): Kontrollsamtaler
    fun hentForKontrollsamtaleId(
        kontrollsamtaleId: UUID,
        sessionContext: SessionContext? = null,
    ): Kontrollsamtale?

    /** Her kan vi ikke bruke [Kontrollsamtaler], siden den er begrenset til en sak. */
    fun hentAllePlanlagte(tilOgMed: LocalDate, sessionContext: SessionContext? = null): List<Kontrollsamtale>
    fun hentFristUtløptFørEllerPåDato(fristFørEllerPåDato: LocalDate): LocalDate?

    /** Her kan vi ikke bruke [Kontrollsamtaler], siden den er begrenset til en sak. */
    fun hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato: LocalDate): List<Kontrollsamtale>
}
