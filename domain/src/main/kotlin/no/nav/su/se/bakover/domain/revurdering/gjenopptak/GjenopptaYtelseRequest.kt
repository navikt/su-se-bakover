package no.nav.su.se.bakover.domain.revurdering.gjenopptak

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import java.util.UUID

sealed class GjenopptaYtelseRequest {
    abstract val sakId: UUID
    abstract val saksbehandler: NavIdentBruker.Saksbehandler
    abstract val revurderingsårsak: Revurderingsårsak

    data class Opprett(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val revurderingsårsak: Revurderingsårsak,
    ) : GjenopptaYtelseRequest()

    data class Oppdater(
        override val sakId: UUID,
        val revurderingId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val revurderingsårsak: Revurderingsårsak,
    ) : GjenopptaYtelseRequest()
}
