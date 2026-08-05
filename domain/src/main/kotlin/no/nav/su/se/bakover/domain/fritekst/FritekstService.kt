package no.nav.su.se.bakover.domain.fritekst

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface FritekstService {
    // Denne brukes i routes for å kunne gjennomføres med tilgangsjekk.
    fun hentFritekst(hentDomain: FritekstHentDomain): Either<FritekstFeil, Fritekst>
    fun hentFritekst(referanseId: UUID, type: FritekstType, sessionContext: SessionContext? = null): Either<FritekstFeil, Fritekst>
    fun lagreFritekst(fritekst: FritekstDomain): Unit
    fun slettFritekst(referanseId: UUID, type: FritekstType, sakId: UUID): Either<FritekstFeil, Unit>
}
