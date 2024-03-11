package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import vedtak.domain.Stønadsvedtak

interface IverksettSøknadsbehandlingService {
    /**
     * Tiltenkt å kalles fra web-laget.
     * Utfører både domenedelen og sideeffekter.
     */
    fun iverksett(command: IverksettSøknadsbehandlingCommand): Either<KunneIkkeIverksetteSøknadsbehandling, Triple<Sak, IverksattSøknadsbehandling, Stønadsvedtak>>

    /**
     * Utfører kun sideeffekter.
     * Kaster dersom noe kritisk går galt; logger ellers (oppgave + statistikk)
     * Tiltenkt å kalles fra andre servicer.
     */
    fun iverksett(iverksattSøknadsbehandlingResponse: IverksattSøknadsbehandlingResponse<*>)
}
