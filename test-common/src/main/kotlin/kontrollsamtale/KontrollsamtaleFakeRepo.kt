package no.nav.su.se.bakover.test.kontrollsamtale

import no.nav.su.se.bakover.common.domain.extensions.singleOrNullOrThrow
import no.nav.su.se.bakover.common.domain.tid.isEqualOrBefore
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtalestatus
import java.time.LocalDate
import java.util.UUID

/**
 * Ikke trådsikker. Ikke ment å brukes på tvers av tester. Ignorerer [SessionContext].
 */
class KontrollsamtaleFakeRepo : KontrollsamtaleRepo {

    val kontrollsamtaler = mutableListOf<Kontrollsamtale>()
    override fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext?) {
        kontrollsamtaler.add(kontrollsamtale)
    }

    override fun hentForSakId(sakId: UUID, sessionContext: SessionContext?): Kontrollsamtaler {
        return Kontrollsamtaler(sakId, kontrollsamtaler.toList().filter { it.sakId == sakId })
    }

    override fun hentForKontrollsamtaleId(kontrollsamtaleId: UUID, sessionContext: SessionContext?): Kontrollsamtale? {
        return kontrollsamtaler.singleOrNullOrThrow { it.id == kontrollsamtaleId }
    }

    override fun hentAllePlanlagte(tilOgMed: LocalDate, sessionContext: SessionContext?): List<Kontrollsamtale> {
        return kontrollsamtaler
            .filter { it.innkallingsdato.isEqualOrBefore(tilOgMed) }
            .filter { it.status == Kontrollsamtalestatus.PLANLAGT_INNKALLING }
    }

    override fun hentFristUtløptFørEllerPåDato(fristFørEllerPåDato: LocalDate): LocalDate? {
        TODO("Not yet implemented")
    }

    override fun hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato: LocalDate): List<Kontrollsamtale> {
        TODO("Not yet implemented")
    }
}
