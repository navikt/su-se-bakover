package no.nav.su.se.bakover.domain.revurdering.stans

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import java.time.LocalDate
import java.util.UUID

sealed interface StansYtelseRequest {
    val sakId: UUID
    val saksbehandler: NavIdentBruker.Saksbehandler
    val fraOgMed: LocalDate
    val revurderingsårsak: Revurderingsårsak

    data class Opprett(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val fraOgMed: LocalDate,
        override val revurderingsårsak: Revurderingsårsak,
    ) : StansYtelseRequest

    data class Oppdater(
        override val sakId: UUID,
        val revurderingId: RevurderingId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val fraOgMed: LocalDate,
        override val revurderingsårsak: Revurderingsårsak,
    ) : StansYtelseRequest
}
