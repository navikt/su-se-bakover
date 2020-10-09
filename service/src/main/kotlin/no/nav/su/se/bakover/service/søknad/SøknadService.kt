package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.AvsluttetBegrunnelse
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadService {
    fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad
    fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad>
    fun slettBehandlingForSøknad(søknadId: UUID, avsluttetBegrunnelse: AvsluttetBegrunnelse)
}

object FantIkkeSøknad
