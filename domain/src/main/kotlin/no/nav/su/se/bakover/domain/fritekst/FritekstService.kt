package no.nav.su.se.bakover.domain.fritekst

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.UUID

interface FritekstService {
    fun hentFritekst(referanseId: UUID, type: FritekstType): Either<FritekstFeil, Fritekst>
    fun lagreFritekst(fritekst: Fritekst): Either<FritekstFeil, Unit>
    fun tømFritekst(referanseId: UUID, type: FritekstType): Either<FritekstFeil, Unit>
}

interface FritekstRepo {
    fun hentFritekst(referanseId: UUID, type: FritekstType): Fritekst?
    fun lagreFritekst(fritekst: Fritekst)
    fun tømFritekst(referanseId: UUID, type: FritekstType)
}

class FritekstServiceImpl(
    private val repository: FritekstRepo,
) : FritekstService {

    override fun hentFritekst(referanseId: UUID, type: FritekstType): Either<FritekstFeil, Fritekst> {
        val fritekst = repository.hentFritekst(referanseId, type)
        return fritekst?.right() ?: FritekstFeil.FantIkkeFritekst.left()
    }

    override fun lagreFritekst(fritekst: Fritekst): Either<FritekstFeil, Unit> {
        return repository.lagreFritekst(fritekst).right()
    }

    override fun tømFritekst(referanseId: UUID, type: FritekstType): Either<FritekstFeil, Unit> {
        return repository.tømFritekst(referanseId, type).right()
    }
}

data class Fritekst(
    val referanseId: UUID,
    val type: FritekstType,
    val fritekst: String,
)

enum class FritekstType {
    FRITEKST_BREV,

    FORHÅNDSVARSEL_SØKNADSBEHANDLING,
    VEDTAKSBREV_SØKNADSBEHANDLING,

    FORHÅNDSVARSEL_REVURDERING,
    VEDTAKSBREV_REVRUDERING,

    FORHÅNDSVARSEL_TILBAKEKREVING,
    VEDTAKSBREV_TILBAKEKREVING,
    NOTAT_TILBAKEKREVING,
}

sealed interface FritekstFeil {
    data object FantIkkeFritekst : FritekstFeil
}
