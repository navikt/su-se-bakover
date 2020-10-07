package no.nav.su.se.bakover.database.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.AvsluttSøknadsBehandlingBody
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadRepo {
    fun hentSøknad(søknadId: UUID): Søknad?
    fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad
    fun avsluttSøknadsBehandling(
        avsluttSøknadsBehandlingBody: AvsluttSøknadsBehandlingBody
    ): Either<KunneIkkeAvslutteSøknadsBehandling, AvsluttetSøknadsBehandlingOK>
}

object AvsluttetSøknadsBehandlingOK
object KunneIkkeAvslutteSøknadsBehandling
