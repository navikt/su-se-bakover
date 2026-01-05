package no.nav.su.se.bakover.domain.fritekst

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.UUID

interface FritekstService {
    // Denne brukes i routes for å kunne gjennomføres med tilgangsjekk.
    fun hentFritekst(hentDomain: FritekstHentDomain): Either<FritekstFeil, Fritekst>
    fun hentFritekst(referanseId: UUID, type: FritekstType): Either<FritekstFeil, Fritekst>
    fun lagreFritekst(fritekst: FritekstDomain): Unit
    fun slettFritekst(referanseId: UUID, type: FritekstType, sakId: UUID): Either<FritekstFeil, Unit>
}

interface FritekstRepo {
    fun hentFritekst(referanseId: UUID, type: FritekstType): Fritekst?
    fun lagreFritekst(fritekst: Fritekst)
    fun slettFritekst(referanseId: UUID, type: FritekstType)
}

class FritekstServiceImpl(
    private val repository: FritekstRepo,
) : FritekstService {
    override fun hentFritekst(hentDomain: FritekstHentDomain): Either<FritekstFeil, Fritekst> {
        return hentFritekst(
            referanseId = hentDomain.referanseId,
            type = hentDomain.type,
        )
    }

    override fun hentFritekst(referanseId: UUID, type: FritekstType): Either<FritekstFeil, Fritekst> {
        val fritekst = repository.hentFritekst(referanseId = referanseId, type = type)
        return fritekst?.right() ?: FritekstFeil.FantIkkeFritekst.left()
    }

    override fun lagreFritekst(fritekst: FritekstDomain) {
        return repository.lagreFritekst(fritekst.toFritekst())
    }

    override fun slettFritekst(referanseId: UUID, type: FritekstType, sakId: UUID): Either<FritekstFeil, Unit> {
        return repository.slettFritekst(referanseId, type).right()
    }
}

data class FritekstHentDomain(
    val referanseId: UUID,
    val type: FritekstType,
    val sakId: UUID,
)

data class FritekstDomain(
    val referanseId: UUID,
    val sakId: UUID,
    val type: FritekstType,
    val fritekst: String,
) {
    fun toFritekst(): Fritekst =
        Fritekst(
            referanseId = referanseId,
            type = type,
            fritekst = fritekst,
        )
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
    VEDTAKSBREV_REVURDERING,

    FORHÅNDSVARSEL_TILBAKEKREVING,
    VEDTAKSBREV_TILBAKEKREVING,
    NOTAT_TILBAKEKREVING,
}

sealed interface FritekstFeil {
    data object FantIkkeFritekst : FritekstFeil
}
