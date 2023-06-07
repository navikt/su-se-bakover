package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import java.time.Clock

class SakFactory(
    private val uuidFactory: UUIDFactory = UUIDFactory(),
    private val clock: Clock,
) {
    fun nySakMedNySøknad(
        fnr: Fnr,
        søknadInnhold: SøknadInnhold,
        innsendtAv: NavIdentBruker,
    ): NySak {
        require(fnr == søknadInnhold.personopplysninger.fnr) {
            "Fnr i søknadinnhold (${søknadInnhold.personopplysninger.fnr}) må være lik fnr i søknad ($fnr)"
        }
        val opprettet = Tidspunkt.now(clock)
        val sakId = uuidFactory.newUUID()
        return NySak(
            id = sakId,
            fnr = fnr,
            opprettet = opprettet,
            søknad = Søknad.Ny(
                id = uuidFactory.newUUID(),
                opprettet = opprettet,
                sakId = sakId,
                søknadInnhold = søknadInnhold,
                innsendtAv = innsendtAv,
            ),
        )
    }
}
