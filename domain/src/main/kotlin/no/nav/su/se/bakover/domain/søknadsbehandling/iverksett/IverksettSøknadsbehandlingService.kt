package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak

fun interface IverksettSøknadsbehandlingService {
    fun iverksett(
        command: IverksettSøknadsbehandlingCommand,
    ): Either<KunneIkkeIverksetteSøknadsbehandling, Triple<Sak, Søknadsbehandling.Iverksatt, Stønadsvedtak>>
}
