package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.database.søknad.AvsluttetSøknadsBehandlingOK
import no.nav.su.se.bakover.database.søknad.KunneIkkeAvslutteSøknadsBehandling
import no.nav.su.se.bakover.domain.AvsluttSøknadsBehandlingBody
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadService {
    fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad
    fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad>
    fun avsluttSøknadsBehandling(avsluttSøknadsBehandlingBody: AvsluttSøknadsBehandlingBody):Either<KunneIkkeAvslutteSøknadsBehandling, AvsluttetSøknadsBehandlingOK>
}

object FantIkkeSøknad
