package no.nav.su.se.bakover.domain.notat

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

data class Notat(
    val id: UUID,
    val sakId: UUID,
    val referanseId: UUID,
    val notat: String,
    val opprettet: Tidspunkt,
    val endret: Tidspunkt,
    val saksbehandler: NotatSaksbehandler,
)

data class NotatSaksbehandler(
    val navIdent: NavIdentBruker.Saksbehandler,
    val tidspunkt: Tidspunkt,
    val handling: String,
)

data class NotatVedlegg(
    val id: UUID,
    val notatId: UUID,
    val filnavn: String,
    val innhold: ByteArray,
    val opprettet: Tidspunkt,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotatVedlegg) return false
        return id == other.id &&
            notatId == other.notatId &&
            filnavn == other.filnavn &&
            innhold.contentEquals(other.innhold) &&
            opprettet == other.opprettet
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + notatId.hashCode()
        result = 31 * result + filnavn.hashCode()
        result = 31 * result + innhold.contentHashCode()
        result = 31 * result + opprettet.hashCode()
        return result
    }
}
