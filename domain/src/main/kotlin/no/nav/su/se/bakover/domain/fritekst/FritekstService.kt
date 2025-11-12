package no.nav.su.se.bakover.domain.fritekst

import java.util.UUID

interface FritekstService {
    fun hentFritekst(referanseId: UUID, type: FritekstType): Fritekst?
    fun lagreFritekst(fritekst: Fritekst)
    fun tømFritekst(referanseId: UUID, type: FritekstType)
}

interface FritekstRepo {
    fun hentFritekst(referanseId: UUID, type: FritekstType): Fritekst?
    fun lagreFritekst(fritekst: Fritekst)
    fun tømFritekst(referanseId: UUID, type: FritekstType)
}

class FritekstServiceImpl(
    private val repository: FritekstRepo,
) : FritekstService {

    override fun hentFritekst(referanseId: UUID, type: FritekstType): Fritekst? = repository.hentFritekst(referanseId, type)

    override fun lagreFritekst(fritekst: Fritekst) = repository.lagreFritekst(fritekst)

    override fun tømFritekst(referanseId: UUID, type: FritekstType) = repository.tømFritekst(referanseId, type)
}

data class Fritekst(
    val referanseId: UUID,
    val type: FritekstType,
    val fritekst: String,
    // sis endret? elns?
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
