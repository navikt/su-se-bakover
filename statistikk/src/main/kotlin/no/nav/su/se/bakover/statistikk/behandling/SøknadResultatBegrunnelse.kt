package no.nav.su.se.bakover.statistikk.behandling

import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling

internal fun LukketSøknadsbehandling.toBehandlingResultat(): BehandlingResultat {
    return when (this.søknad) {
        is Søknad.Journalført.MedOppgave.Lukket.Avvist -> BehandlingResultat.Avvist
        is Søknad.Journalført.MedOppgave.Lukket.Bortfalt -> BehandlingResultat.Bortfalt
        is Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker -> BehandlingResultat.Trukket
    }
}

internal fun Søknad.Journalført.MedOppgave.Lukket.toBehandlingResultat(): BehandlingResultat {
    return when (this) {
        is Søknad.Journalført.MedOppgave.Lukket.Avvist -> BehandlingResultat.Avvist
        is Søknad.Journalført.MedOppgave.Lukket.Bortfalt -> BehandlingResultat.Bortfalt
        is Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker -> BehandlingResultat.Trukket
    }
}
