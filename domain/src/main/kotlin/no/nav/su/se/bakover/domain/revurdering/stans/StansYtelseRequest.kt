package no.nav.su.se.bakover.domain.revurdering.stans

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import java.time.LocalDate
import java.util.UUID

sealed class StansYtelseRequest {
    abstract val sakId: UUID
    abstract val saksbehandler: NavIdentBruker.Saksbehandler
    abstract val fraOgMed: LocalDate
    abstract val revurderingsårsak: Revurderingsårsak

    data class Opprett(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val fraOgMed: LocalDate,
        override val revurderingsårsak: Revurderingsårsak,
    ) : StansYtelseRequest()

    data class Oppdater(
        override val sakId: UUID,
        val revurderingId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val fraOgMed: LocalDate,
        override val revurderingsårsak: Revurderingsårsak,
    ) : StansYtelseRequest()
}
