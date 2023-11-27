package no.nav.su.se.bakover.kontrollsamtale.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.time.LocalDate
import java.util.UUID

interface KontrollsamtaleRepo {
    fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext = defaultSessionContext())
    fun hentForSakId(sakId: UUID, sessionContext: SessionContext = defaultSessionContext()): List<Kontrollsamtale>
    fun hentAllePlanlagte(tilOgMed: LocalDate, sessionContext: SessionContext): List<Kontrollsamtale>

    fun hentFristUtløptFørEllerPåDato(fristFørEllerPåDato: LocalDate): LocalDate?
    fun hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato: LocalDate): List<Kontrollsamtale>
    fun defaultSessionContext(): SessionContext

    fun defaultTransactionContext(): TransactionContext
}
