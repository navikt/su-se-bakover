package no.nav.su.se.bakover.statistikk.behandling.søknadsbehandling

import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus
import no.nav.su.se.bakover.statistikk.behandling.BehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.Behandlingstype
import no.nav.su.se.bakover.statistikk.behandling.behandlingYtelseDetaljer
import no.nav.su.se.bakover.statistikk.behandling.mottattDato
import no.nav.su.se.bakover.statistikk.behandling.toBehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.toFunksjonellTid
import no.nav.su.se.bakover.statistikk.sak.toYtelseType
import vilkår.common.domain.Avslagsgrunn
import java.time.Clock
import java.time.LocalDate

internal fun StatistikkEvent.Behandling.Omgjøring.toBehandlingsstatistikkDto(
    gitCommit: GitCommit?,
    clock: Clock,
): BehandlingsstatistikkDto {
    when (this) {
        is StatistikkEvent.Behandling.Omgjøring.AvslåttOmgjøring ->
            return BehandlingsstatistikkDto(
                behandlingType = Behandlingstype.OMGJØRING_AVSLAG,
                behandlingTypeBeskrivelse = Behandlingstype.OMGJØRING_AVSLAG.beskrivelse,
                funksjonellTid = søknadsbehandling.opprettet,
                tekniskTid = Tidspunkt.now(clock),
                registrertDato = søknadsbehandling.opprettet.toLocalDate(zoneIdOslo),
                mottattDato = LocalDate.now(clock),
                behandlingId = søknadsbehandling.id.value,
                sakId = søknadsbehandling.sakId,
                søknadId = søknadsbehandling.søknad.id,
                saksnummer = søknadsbehandling.saksnummer.nummer,
                versjon = gitCommit?.value,
                avsluttet = false,
                saksbehandler = saksbehandler.toString(),
                beslutter = null,
                behandlingYtelseDetaljer = søknadsbehandling.behandlingYtelseDetaljer(),
                behandlingStatus = BehandlingStatus.Registrert.name,
                behandlingStatusBeskrivelse = BehandlingStatus.Registrert.beskrivelse,
                resultat = null,
                resultatBeskrivelse = null,
                resultatBegrunnelse = null,
                totrinnsbehandling = true,
                ytelseType = this.søknadsbehandling.sakstype.toYtelseType(),
                omgjøringsgrunn = this.søknadsbehandling.omgjøringsgrunn?.name,
            )
    }
}

internal fun StatistikkEvent.Behandling.Søknad.toBehandlingsstatistikkDto(
    gitCommit: GitCommit?,
    clock: Clock,
): BehandlingsstatistikkDto {
    return when (this) {
        is StatistikkEvent.Behandling.Søknad.Opprettet -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.søknadsbehandling.opprettet,
            behandlingStatus = BehandlingStatus.UnderBehandling,
            behandlingsresultat = null,
            resultatBegrunnelse = null,
            beslutter = null,
            totrinnsbehandling = true,
            avsluttet = false,
            saksbehandler = this.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            // Her lagres det ikke noe mer nøyaktig.
            funksjonellTid = Tidspunkt.now(clock),
            behandlingStatus = BehandlingStatus.TilAttestering,
            behandlingsresultat = BehandlingResultat.Innvilget,
            resultatBegrunnelse = null,
            beslutter = null,
            totrinnsbehandling = true,
            avsluttet = false,
            saksbehandler = this.søknadsbehandling.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.TilAttestering.Avslag -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            // Her lagres det ikke noe mer nøyaktig.
            funksjonellTid = Tidspunkt.now(clock),
            behandlingStatus = BehandlingStatus.TilAttestering,
            behandlingsresultat = BehandlingResultat.AvslåttSøknadsbehandling,
            resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
            beslutter = null,
            totrinnsbehandling = true,
            avsluttet = false,
            saksbehandler = this.søknadsbehandling.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.Underkjent.Innvilget -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.søknadsbehandling.attesteringer.toFunksjonellTid(this.søknadsbehandling.id.value, clock),
            behandlingStatus = BehandlingStatus.Underkjent,
            behandlingsresultat = BehandlingResultat.Innvilget,
            resultatBegrunnelse = null,
            beslutter = this.søknadsbehandling.prøvHentSisteAttestant(),
            totrinnsbehandling = true,
            avsluttet = false,
            saksbehandler = this.søknadsbehandling.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.Underkjent.Avslag -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.søknadsbehandling.attesteringer.toFunksjonellTid(this.søknadsbehandling.id.value, clock),
            behandlingStatus = BehandlingStatus.Underkjent,
            behandlingsresultat = BehandlingResultat.AvslåttSøknadsbehandling,
            resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
            beslutter = this.søknadsbehandling.prøvHentSisteAttestant(),
            totrinnsbehandling = true,
            avsluttet = false,
            saksbehandler = this.søknadsbehandling.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.vedtak.opprettet,
            behandlingStatus = BehandlingStatus.Iverksatt,
            behandlingsresultat = BehandlingResultat.Innvilget,
            resultatBegrunnelse = null,
            beslutter = this.vedtak.attestant,
            totrinnsbehandling = true,
            avsluttet = true,
            saksbehandler = this.søknadsbehandling.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.vedtak.opprettet,
            behandlingStatus = BehandlingStatus.Iverksatt,
            behandlingsresultat = BehandlingResultat.AvslåttSøknadsbehandling,
            resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
            beslutter = this.vedtak.attestant,
            totrinnsbehandling = true,
            avsluttet = true,
            saksbehandler = this.søknadsbehandling.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.Lukket -> {
            toDto(
                clock = clock,
                gitCommit = gitCommit,
                funksjonellTid = this.søknadsbehandling.lukketTidspunkt,
                behandlingStatus = BehandlingStatus.Avsluttet,
                behandlingsresultat = this.søknadsbehandling.toBehandlingResultat(),
                resultatBegrunnelse = null,
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
    behandlingsresultat: BehandlingResultat?,
    resultatBegrunnelse: String?,
    beslutter: NavIdentBruker.Attestant?,
    totrinnsbehandling: Boolean,
    avsluttet: Boolean,
    saksbehandler: NavIdentBruker.Saksbehandler?,
): BehandlingsstatistikkDto {
    val søknadsbehandling = this.søknadsbehandling
    val søknad = søknadsbehandling.søknad
    return BehandlingsstatistikkDto(
        behandlingType = Behandlingstype.SOKNAD,
        behandlingTypeBeskrivelse = Behandlingstype.SOKNAD.beskrivelse,
        funksjonellTid = funksjonellTid,
        tekniskTid = Tidspunkt.now(clock),
        // registrertDato skal samsvare med REGISTRERT-hendelsen sin funksjonellTid (som er når søknaden ble registrert i systemet vårt)
        registrertDato = søknad.opprettet.toLocalDate(zoneIdOslo),
        mottattDato = søknad.mottattDato(),
        behandlingId = søknadsbehandling.id.value,
        sakId = søknadsbehandling.sakId,
        søknadId = søknadsbehandling.søknad.id,
        saksnummer = søknadsbehandling.saksnummer.nummer,
        versjon = gitCommit?.value,
        avsluttet = avsluttet,
        saksbehandler = saksbehandler?.toString(),
        beslutter = beslutter?.toString(),
        behandlingYtelseDetaljer = søknadsbehandling.behandlingYtelseDetaljer(),
        behandlingStatus = behandlingStatus.toString(),
        behandlingStatusBeskrivelse = behandlingStatus.beskrivelse,
        resultat = behandlingsresultat?.toString(),
        resultatBeskrivelse = behandlingsresultat?.beskrivelse,
        resultatBegrunnelse = resultatBegrunnelse,
        totrinnsbehandling = totrinnsbehandling,
        ytelseType = this.søknadsbehandling.sakstype.toYtelseType(),
    )
}

private fun utledAvslagsgrunner(avslagsgrunner: List<Avslagsgrunn>): String? {
    return if (avslagsgrunner.isEmpty()) null else avslagsgrunner.joinToString(",")
}
