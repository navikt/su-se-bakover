package no.nav.su.se.bakover.service.søknad.lukk

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand

interface LukkSøknadService {
    fun lukkSøknad(command: LukkSøknadCommand): Sak
    fun lagBrevutkast(command: LukkSøknadCommand): Pair<Fnr, ByteArray>
}
