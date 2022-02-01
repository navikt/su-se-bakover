package no.nav.su.se.bakover.domain.kontrollsamtale

import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.time.LocalDate
import java.util.UUID

interface KontrollsamtaleRepo {
    fun lagre(kontrollsamtale: Kontrollsamtale, transactionContext: TransactionContext = defaultTransactionContext())
    fun hentForSakId(sakId: UUID): List<Kontrollsamtale>
    fun hentAllePlanlagte(tilOgMed: LocalDate): List<Kontrollsamtale>
    fun defaultTransactionContext(): TransactionContext
}
