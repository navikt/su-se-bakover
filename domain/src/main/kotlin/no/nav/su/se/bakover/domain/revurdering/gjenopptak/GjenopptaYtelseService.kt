package no.nav.su.se.bakover.domain.revurdering.gjenopptak

import arrow.core.Either
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import java.util.UUID

interface GjenopptaYtelseService {
    fun gjenopptaYtelse(
        request: GjenopptaYtelseRequest,
    ): Either<KunneIkkeSimulereGjenopptakAvYtelse, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse>

    fun iverksettGjenopptakAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse>
}
