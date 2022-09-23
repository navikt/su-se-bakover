package no.nav.su.se.bakover.statistikk.behandling

import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadinnhold.ForNav
import java.time.LocalDate

internal fun Søknad.mottattDato(): LocalDate {
    return when (val forNav = this.søknadInnhold.forNav) {
        is ForNav.DigitalSøknad -> this.opprettet.toLocalDate(zoneIdOslo)
        is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
    }
}
