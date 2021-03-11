package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import java.time.Clock

internal class SøknadStatistikkMapper(private val clock: Clock) {
    fun map(
        søknad: Søknad,
        saksnummer: Saksnummer,
        søknadStatus: Statistikk.Behandling.SøknadStatus
    ): Statistikk.Behandling =
        Statistikk.Behandling(
            funksjonellTid = when (søknad) {
                is Søknad.Lukket -> søknad.lukketTidspunkt
                else -> søknad.opprettet
            },
            tekniskTid = when (søknad) {
                is Søknad.Lukket -> søknad.lukketTidspunkt
                else -> søknad.opprettet
            },
            mottattDato = when (val forNav = søknad.søknadInnhold.forNav) {
                is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
                is ForNav.DigitalSøknad -> søknad.opprettet.toLocalDate(zoneIdOslo)
            },
            registrertDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = søknad.id,
            relatertBehandlingId = null,
            sakId = søknad.sakId,
            saksnummer = saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            behandlingStatus = søknadStatus.name,
            behandlingStatusBeskrivelse = søknadStatus.beskrivelse,
            totrinnsbehandling = false,
            versjon = clock.millis(),
            resultat = null,
            resultatBegrunnelse = null,
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = null,
            saksbehandler = when (søknad) {
                is Søknad.Lukket -> søknad.lukketAv.toString()
                else -> null
            },
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = when (søknad) {
                is Søknad.Lukket -> true
                else -> false
            }
        )
}
