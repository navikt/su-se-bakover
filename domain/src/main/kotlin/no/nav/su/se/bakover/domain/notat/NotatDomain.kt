package no.nav.su.se.bakover.domain.notat

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

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
    val opprettet: Tidspunkt,
    val endret: Tidspunkt,
    val saksbehandler: List<NotatSaksbehandler>,
) {
    init {
        require(saksbehandler.isNotEmpty()) { "Notat må ha minst én saksbehandlerhendelse" }
    }
}

data class NotatSaksbehandler(
    val navIdent: NavIdentBruker.Saksbehandler,
    val tidspunkt: Tidspunkt,
    val handling: NotatHandling,
)

data class NotatMedVedlegg(
    val notat: Notat,
    val vedlegg: List<NotatVedlegg>,
)

@Suppress("ArrayInDataClass")
data class NotatVedlegg(
    val id: UUID,
    val notatId: UUID,
    val filnavn: String,
    val mimeType: String,
    val innhold: ByteArray,
    val opprettet: Tidspunkt,
)

fun Notat.leggTilSaksbehandlerhendelse(saksbehandler: NotatSaksbehandler): Notat {
    return copy(saksbehandler = this.saksbehandler + saksbehandler)
}
