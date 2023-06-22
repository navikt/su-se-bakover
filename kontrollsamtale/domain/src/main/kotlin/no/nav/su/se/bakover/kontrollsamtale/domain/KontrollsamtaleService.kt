package no.nav.su.se.bakover.kontrollsamtale.domain

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.SessionContext
import java.time.LocalDate
import java.util.UUID

interface KontrollsamtaleService {
    fun kallInn(
        sakId: UUID,
        kontrollsamtale: Kontrollsamtale,
    ): Either<KunneIkkeKalleInnTilKontrollsamtale, Unit>

    fun nyDato(sakId: UUID, dato: LocalDate): Either<KunneIkkeSetteNyDatoForKontrollsamtale, Unit>
    fun hentNestePlanlagteKontrollsamtale(
        sakId: UUID,
        sessionContext: SessionContext = defaultSessionContext(),
    ): Either<KunneIkkeHenteKontrollsamtale, Kontrollsamtale>

    fun hentPlanlagteKontrollsamtaler(sessionContext: SessionContext = defaultSessionContext()): Either<KunneIkkeHenteKontrollsamtale, List<Kontrollsamtale>>

    fun defaultSessionContext(): SessionContext

    fun hentInnkalteKontrollsamtalerMedFristUtløpt(dato: LocalDate): List<Kontrollsamtale>

    fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext)
    fun hentKontrollsamtaler(sakId: UUID): List<Kontrollsamtale>
}
