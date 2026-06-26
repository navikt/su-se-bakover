package no.nav.su.se.bakover.domain.notat

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

sealed interface NotatFeil {
    data object FantIkkeSak : NotatFeil
    data object FantIkkeNotat : NotatFeil
    data object FantIkkeVedlegg : NotatFeil
    data object VedleggTilhørerIkkeNotat : NotatFeil
    data object NotatTilhørerIkkeSak : NotatFeil
    data object TomtNotat : NotatFeil
    data object ReferanseIdAlleredeIBruk : NotatFeil
    data object UgyldigMimeType : NotatFeil
    data object MimeTypeMatcherIkkeFilnavn : NotatFeil
    data object FilForStor : NotatFeil
}

enum class NotatHandling {
    OPPRETTET,
    OPPDATERT,
    VEDLEGG_LAGT_TIL,
    VEDLEGG_SLETTET,
}

data class Notat(
    val id: UUID,
    val sakId: UUID,
    val referanseId: UUID,
    val notat: String,
    val attestantNotat: String = "",
    val opprettet: Tidspunkt,
    val endret: Tidspunkt,
    val hendelser: List<NotatHendelse>,
) {
    init {
        require(hendelser.isNotEmpty()) { "Notat må ha minst én saksbehandlerhendelse" }
    }
}

data class NotatHendelse(
    val navIdent: NavIdentBruker,
    val tidspunkt: Tidspunkt,
    val handling: NotatHandling,
)

data class NotatMedVedlegg(
    val notat: Notat,
    val vedlegg: List<NotatVedlegg>,
)

data class NotatVedlegg(
    val id: UUID,
    val notatId: UUID,
    val filnavn: String,
    val mimeType: String,
    val innhold: ByteArray,
    val opprettet: Tidspunkt,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotatVedlegg) return false
        return id == other.id &&
            notatId == other.notatId &&
            filnavn == other.filnavn &&
            mimeType == other.mimeType &&
            innhold.contentEquals(other.innhold) &&
            opprettet == other.opprettet
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + notatId.hashCode()
        result = 31 * result + filnavn.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + innhold.contentHashCode()
        result = 31 * result + opprettet.hashCode()
        return result
    }
}

fun Notat.leggTilHendelse(hendelse: NotatHendelse): Notat {
    return copy(hendelser = this.hendelser + hendelse)
}
