package no.nav.su.se.bakover.statistikk.behandling.søknad

import no.nav.su.se.bakover.common.GitCommit
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.BehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.Behandlingstype
import no.nav.su.se.bakover.statistikk.behandling.mottattDato
import no.nav.su.se.bakover.statistikk.behandling.toBehandlingResultat
import java.time.Clock

internal fun StatistikkEvent.Søknad.toBehandlingsstatistikkDto(
    gitCommit: GitCommit?,
    clock: Clock,
): BehandlingsstatistikkDto {
    return when (this) {
        is StatistikkEvent.Søknad.Mottatt ->
            tilStatistikkbehandling(
                gitCommit = gitCommit,
                clock = clock,
                søknad = søknad,
                saksnummer = saksnummer,
                behandlingStatus = no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus.SØKNAD_MOTTATT,
                avsluttet = false,
                saksbehandler = søknad.innsendtAv,
                funksjonellTid = søknad.opprettet,
                totrinnsbehandling = true,
            )

        is StatistikkEvent.Søknad.Lukket ->
            tilStatistikkbehandling(
                gitCommit = gitCommit,
                clock = clock,
                søknad = søknad,
                saksnummer = saksnummer,
                behandlingStatus = no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus.AVSLUTTET,
                avsluttet = true,
                saksbehandler = this.søknad.lukketAv,
                funksjonellTid = this.søknad.lukketTidspunkt,
                totrinnsbehandling = false,
                resultat = this.søknad.toBehandlingResultat(),
            )
    }
}

private fun tilStatistikkbehandling(
    gitCommit: GitCommit?,
    clock: Clock,
    søknad: Søknad,
    saksnummer: Saksnummer,
    behandlingStatus: no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus,
    saksbehandler: NavIdentBruker?,
    avsluttet: Boolean,
    funksjonellTid: Tidspunkt,
    totrinnsbehandling: Boolean,
    resultat: BehandlingResultat? = null,
) = BehandlingsstatistikkDto(
    funksjonellTid = funksjonellTid,
    tekniskTid = Tidspunkt.now(clock),
    mottattDato = søknad.mottattDato(),
    registrertDato = søknad.opprettet.toLocalDate(zoneIdOslo),
    søknadId = søknad.id,
    behandlingId = null,
    sakId = søknad.sakId,
    saksnummer = saksnummer.nummer,
    behandlingType = Behandlingstype.SOKNAD,
    behandlingTypeBeskrivelse = Behandlingstype.SOKNAD.beskrivelse,
    // Søknadsbehandling krever i utgangspunktet totrinnsbehandling, med unntak av avbryting/lukking/avslutting.
    totrinnsbehandling = totrinnsbehandling,
    versjon = gitCommit?.value,
    saksbehandler = saksbehandler?.toString(),
    avsluttet = avsluttet,
    behandlingStatus = behandlingStatus,
    behandlingStatusBeskrivelse = behandlingStatus.beskrivelse,
    resultat = resultat,
    resultatBeskrivelse = resultat?.beskrivelse,

)
