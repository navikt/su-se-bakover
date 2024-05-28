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

    fun hentAllePlanlagte(tilOgMed: LocalDate, sessionContext: SessionContext? = null): Kontrollsamtaler
    fun hentFristUtløptFørEllerPåDato(fristFørEllerPåDato: LocalDate): LocalDate?
    fun hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato: LocalDate): Kontrollsamtaler
}
