package no.nav.su.se.bakover.kontrollsamtale.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.time.LocalDate
import java.util.UUID

interface KontrollsamtaleRepo {
    fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext = defaultSessionContext())
    fun hentForSakId(sakId: UUID, sessionContext: SessionContext = defaultSessionContext()): Kontrollsamtaler
    fun hentAllePlanlagte(tilOgMed: LocalDate, sessionContext: SessionContext): Kontrollsamtaler

    fun hentFristUtløptFørEllerPåDato(fristFørEllerPåDato: LocalDate): LocalDate?
    fun hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato: LocalDate): Kontrollsamtaler
    fun defaultSessionContext(): SessionContext

    fun defaultTransactionContext(): TransactionContext
}
