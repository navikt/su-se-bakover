package no.nav.su.se.bakover.statistikk.behandling.søknadsbehandling

import no.nav.su.se.bakover.common.GitCommit
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus
import no.nav.su.se.bakover.statistikk.behandling.BehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.Behandlingstype
import no.nav.su.se.bakover.statistikk.behandling.behandlingYtelseDetaljer
import no.nav.su.se.bakover.statistikk.behandling.mottattDato
import no.nav.su.se.bakover.statistikk.behandling.toBehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.toFunksjonellTid
import java.time.Clock

internal fun StatistikkEvent.Behandling.Søknad.toBehandlingsstatistikkDto(
    gitCommit: GitCommit?,
    clock: Clock,
): BehandlingsstatistikkDto {
    return when (this) {
        is StatistikkEvent.Behandling.Søknad.Opprettet -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.søknadsbehandling.opprettet,
            behandlingStatus = BehandlingStatus.REGISTRERT,
            saksbehandler = this.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.søknadsbehandling.attesteringer.toFunksjonellTid(this.søknadsbehandling.id, clock),
            behandlingStatus = BehandlingStatus.TIL_ATTESTERING,
            behandlingsresultat = BehandlingResultat.Innvilget,
        )

        is StatistikkEvent.Behandling.Søknad.TilAttestering.Avslag -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.søknadsbehandling.attesteringer.toFunksjonellTid(this.søknadsbehandling.id, clock),
            behandlingStatus = BehandlingStatus.TIL_ATTESTERING,
            behandlingsresultat = BehandlingResultat.Avslag,
            resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
        )

        is StatistikkEvent.Behandling.Søknad.Underkjent.Innvilget -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.søknadsbehandling.attesteringer.toFunksjonellTid(this.søknadsbehandling.id, clock),
            behandlingStatus = BehandlingStatus.UNDERKJENT,
            behandlingsresultat = BehandlingResultat.Innvilget,
        )

        is StatistikkEvent.Behandling.Søknad.Underkjent.Avslag -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.søknadsbehandling.attesteringer.toFunksjonellTid(this.søknadsbehandling.id, clock),
            behandlingStatus = BehandlingStatus.UNDERKJENT,
            behandlingsresultat = BehandlingResultat.Avslag,
            resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
        )

        is StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.vedtak.opprettet,
            behandlingStatus = BehandlingStatus.IVERKSATT,
            behandlingsresultat = BehandlingResultat.Innvilget,
            beslutter = this.vedtak.attestant,
            avsluttet = true,
        )

        is StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.vedtak.opprettet,
            behandlingStatus = BehandlingStatus.IVERKSATT,
            behandlingsresultat = BehandlingResultat.Avslag,
            resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
            beslutter = this.vedtak.attestant,
            avsluttet = true,
        )

        is StatistikkEvent.Behandling.Søknad.Lukket -> {
            toDto(
                clock = clock,
                gitCommit = gitCommit,
                funksjonellTid = this.søknadsbehandling.lukketTidspunkt,
                behandlingStatus = BehandlingStatus.AVSLUTTET,
                behandlingsresultat = this.søknadsbehandling.toBehandlingResultat(),
                // Det kreves ikke attestant ved lukking.
                beslutter = null,
                totrinnsbehandling = false,
                avsluttet = true,
                saksbehandler = this.saksbehandler,
            )
        }
    }
}

private fun StatistikkEvent.Behandling.Søknad.toDto(
    clock: Clock,
    gitCommit: GitCommit?,
    funksjonellTid: Tidspunkt,
    behandlingStatus: BehandlingStatus,
    behandlingsresultat: BehandlingResultat? = null,
    resultatBegrunnelse: String? = null,
    beslutter: NavIdentBruker.Attestant? = null,
    totrinnsbehandling: Boolean = true,
    avsluttet: Boolean = false,
    saksbehandler: NavIdentBruker.Saksbehandler? = this.søknadsbehandling.saksbehandler,
): BehandlingsstatistikkDto {
    val søknadsbehandling = this.søknadsbehandling
    val søknad = søknadsbehandling.søknad
    return BehandlingsstatistikkDto(
        behandlingType = Behandlingstype.SOKNAD,
        behandlingTypeBeskrivelse = Behandlingstype.SOKNAD.beskrivelse,
        funksjonellTid = funksjonellTid,
        tekniskTid = Tidspunkt.now(clock),
        registrertDato = søknad.opprettet.toLocalDate(zoneIdOslo),
        mottattDato = søknad.mottattDato(),
        behandlingId = søknadsbehandling.id,
        sakId = søknadsbehandling.sakId,
        søknadId = søknadsbehandling.søknad.id,
        saksnummer = søknadsbehandling.saksnummer.nummer,
        versjon = gitCommit?.value,
        avsluttet = avsluttet,
        saksbehandler = saksbehandler?.toString(),
        beslutter = beslutter?.toString(),
        behandlingYtelseDetaljer = søknadsbehandling.behandlingYtelseDetaljer(),
        behandlingStatus = behandlingStatus,
        behandlingStatusBeskrivelse = behandlingStatus.beskrivelse,
        resultat = behandlingsresultat,
        resultatBeskrivelse = behandlingsresultat?.beskrivelse,
        resultatBegrunnelse = resultatBegrunnelse,
        totrinnsbehandling = totrinnsbehandling,
    )
}

private fun utledAvslagsgrunner(avslagsgrunner: List<Avslagsgrunn>): String? {
    return if (avslagsgrunner.isEmpty()) null else avslagsgrunner.joinToString(",")
}
