package no.nav.su.se.bakover.domain.fritekst

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface FritekstRepo {
    fun hentFritekst(referanseId: UUID, type: FritekstType, sessionContext: SessionContext? = null): Fritekst?
    fun lagreFritekst(fritekst: Fritekst)
    fun slettFritekst(referanseId: UUID, type: FritekstType)
}
