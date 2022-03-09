package no.nav.su.se.bakover.domain.kontrollsamtale

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.time.LocalDate
import java.util.UUID

interface KontrollsamtaleRepo {
    fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext = defaultSessionContext())
    fun hentForSakId(sakId: UUID, sessionContext: SessionContext = defaultSessionContext()): List<Kontrollsamtale>
    fun hentAllePlanlagte(tilOgMed: LocalDate, sessionContext: SessionContext): List<Kontrollsamtale>
    fun defaultSessionContext(): SessionContext
}
