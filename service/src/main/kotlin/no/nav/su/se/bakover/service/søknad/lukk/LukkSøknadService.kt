package no.nav.su.se.bakover.service.søknad.lukk

import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling

interface LukkSøknadService {
    /**
     * Lukker søknaden og eventuell søknadsbehandling.
     *
     * @return Søknaden, og eventuelt tilhørende behandling som er blitt lukket. Fnr er for audit loggen i route
     */
    fun lukkSøknad(command: LukkSøknadCommand): Triple<Søknad.Journalført.MedOppgave.Lukket, LukketSøknadsbehandling?, Fnr>
    fun lagBrevutkast(command: LukkSøknadCommand): Pair<Fnr, PdfA>
}
