package no.nav.su.se.bakover.domain.fritekst

import java.util.UUID

interface FritekstService {
    fun hentFritekst(referanseId: UUID, type: FritekstType): Fritekst?
    fun lagreFritekst(referanseId: UUID, type: FritekstType)
    fun tømFritekst(referanseId: UUID, type: FritekstType)
}

class FritekstServiceImpl : FritekstService {

    override fun hentFritekst(referanseId: UUID, type: FritekstType): Fritekst? {
        // val fritekst = repo.hentFritekst(referanseId, type)
        val fritekst = Fritekst(
            referanseId = referanseId,
            type = type,
            fritekst = "Hubba bubba!",
        )
        return fritekst
    }

    override fun lagreFritekst(referanseId: UUID, type: FritekstType) {
    }

    override fun tømFritekst(referanseId: UUID, type: FritekstType) {
    }
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
