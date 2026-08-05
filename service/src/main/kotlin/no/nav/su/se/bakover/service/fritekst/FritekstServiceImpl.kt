package no.nav.su.se.bakover.service.fritekst

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.fritekst.Fritekst
import no.nav.su.se.bakover.domain.fritekst.FritekstDomain
import no.nav.su.se.bakover.domain.fritekst.FritekstFeil
import no.nav.su.se.bakover.domain.fritekst.FritekstHentDomain
import no.nav.su.se.bakover.domain.fritekst.FritekstRepo
import no.nav.su.se.bakover.domain.fritekst.FritekstService
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import java.util.UUID

class FritekstServiceImpl(
    private val repository: FritekstRepo,
) : FritekstService {
    override fun hentFritekst(hentDomain: FritekstHentDomain): Either<FritekstFeil, Fritekst> {
        return hentFritekst(
            referanseId = hentDomain.referanseId,
            type = hentDomain.type,
        )
    }

    override fun hentFritekst(referanseId: UUID, type: FritekstType, sessionContext: SessionContext?): Either<FritekstFeil, Fritekst> {
        val fritekst = repository.hentFritekst(referanseId = referanseId, type = type, sessionContext = sessionContext)
        return fritekst?.right() ?: FritekstFeil.FantIkkeFritekst.left()
    }

    override fun lagreFritekst(fritekst: FritekstDomain) {
        return repository.lagreFritekst(fritekst.toFritekst())
    }

    override fun slettFritekst(referanseId: UUID, type: FritekstType, sakId: UUID): Either<FritekstFeil, Unit> {
        return repository.slettFritekst(referanseId, type).right()
    }
}
