package no.nav.su.se.bakover.domain.revurdering.gjenopptak

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import java.util.UUID

sealed interface GjenopptaYtelseRequest {
    val sakId: UUID
    val saksbehandler: NavIdentBruker.Saksbehandler
    val revurderingsårsak: Revurderingsårsak

    data class Opprett(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val revurderingsårsak: Revurderingsårsak,
    ) : GjenopptaYtelseRequest

    data class Oppdater(
        override val sakId: UUID,
        val revurderingId: RevurderingId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val revurderingsårsak: Revurderingsårsak,
    ) : GjenopptaYtelseRequest
}
