package no.nav.su.se.bakover.kontrollsamtale.domain

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.kontrollsamtale.domain.annuller.KunneIkkeAnnullereKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.KanIkkeOppretteKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.OpprettKontrollsamtaleCommand
import java.time.LocalDate
import java.util.UUID

interface KontrollsamtaleService {
    fun kallInn(
        kontrollsamtale: Kontrollsamtale,
    ): Either<KunneIkkeKalleInnTilKontrollsamtale, Unit>

    fun nyDato(sakId: UUID, dato: LocalDate): Either<KunneIkkeSetteNyDatoForKontrollsamtale, Unit>
    fun hentNestePlanlagteKontrollsamtale(
        sakId: UUID,
        sessionContext: SessionContext? = null,
    ): Either<KunneIkkeHenteKontrollsamtale, Kontrollsamtale>

    fun hentPlanlagteKontrollsamtaler(sessionContext: SessionContext? = null): List<Kontrollsamtale>

    fun hentFristUtløptFørEllerPåDato(fristFørEllerPåDato: LocalDate): LocalDate?

    fun hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato: LocalDate): List<Kontrollsamtale>

    fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext? = null)
    fun hentKontrollsamtaler(sakId: UUID): Kontrollsamtaler

    fun annullerKontrollsamtale(
        sakId: UUID,
        kontrollsamtaleId: UUID,
        sessionContext: SessionContext? = null,
    ): Either<KunneIkkeAnnullereKontrollsamtale, Unit>

    fun opprettKontrollsamtale(
        opprettKontrollsamtaleCommand: OpprettKontrollsamtaleCommand,
        sessionContext: SessionContext? = null,
    ): Either<KanIkkeOppretteKontrollsamtale, Kontrollsamtale>
}
