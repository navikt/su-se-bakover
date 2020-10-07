package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.AvsluttSøkndsBehandlingBegrunnelse
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadService {
    fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad
    fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad>
    fun avsluttSøknadsBehandling(søknadId: UUID, avsluttSøkndsBehandlingBegrunnelse: AvsluttSøkndsBehandlingBegrunnelse)
}

object FantIkkeSøknad
