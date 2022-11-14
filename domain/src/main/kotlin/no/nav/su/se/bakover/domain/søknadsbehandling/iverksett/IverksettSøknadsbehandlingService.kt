package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import arrow.core.Either
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling

fun interface IverksettSøknadsbehandlingService {
    fun iverksett(
        request: IverksettRequest,
    ): Either<KunneIkkeIverksette, Søknadsbehandling.Iverksatt>
}
