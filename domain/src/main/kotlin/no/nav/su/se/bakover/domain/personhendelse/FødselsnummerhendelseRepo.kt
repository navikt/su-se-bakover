package no.nav.su.se.bakover.domain.personhendelse

import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

interface FødselsnummerhendelseRepo {
    /** Lagrer en innboks-rad for at saken skal sjekkes mot PDL ved neste jobb-kjøring. */
    fun lagre(sakId: UUID)
    fun hentUbehandlede(limit: Int = 50): List<Fødselsnummerhendelse>
    fun markerProsessert(id: UUID, tidspunkt: Tidspunkt, transactionContext: TransactionContext)
}
