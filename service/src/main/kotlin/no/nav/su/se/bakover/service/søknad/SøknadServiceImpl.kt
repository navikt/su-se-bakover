package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AvsluttSøkndsBehandlingBegrunnelse
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

internal class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo
) : SøknadService {
    override fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad {
        return søknadRepo.opprettSøknad(sakId, søknad)
    }

    override fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad> {
        return søknadRepo.hentSøknad(søknadId)?.right() ?: FantIkkeSøknad.left()
    }

    override fun avsluttSøknadsBehandling(
        søknadId: UUID,
        avsluttSøkndsBehandlingBegrunnelse: AvsluttSøkndsBehandlingBegrunnelse
    ) {
        return søknadRepo.avsluttSøknadsBehandling(søknadId, avsluttSøkndsBehandlingBegrunnelse)
    }
}
