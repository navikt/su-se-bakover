package no.nav.su.se.bakover.statistikk.behandling.revurdering.gjenopptak

import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus
import no.nav.su.se.bakover.statistikk.behandling.BehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.Behandlingstype
import no.nav.su.se.bakover.statistikk.behandling.behandlingYtelseDetaljer
import no.nav.su.se.bakover.statistikk.behandling.revurdering.toResultatBegrunnelse
import java.time.Clock

internal fun StatistikkEvent.Behandling.Gjenoppta.toBehandlingsstatistikkDto(
    gitCommit: GitCommit?,
    clock: Clock,
): BehandlingsstatistikkDto {
    return when (this) {
        // Selvom typen er [GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse] ønsker statistikk seg en registrert-hendelse.
        is StatistikkEvent.Behandling.Gjenoppta.Opprettet -> this.revurdering.toBehandlingsstatistikk(
            gitCommit = gitCommit,
            clock = clock,
            behandlingStatus = BehandlingStatus.Registrert,
            behandlingResultat = BehandlingResultat.Gjenopptatt,
            // I praksis så skal bare MOTTATT_KONTROLLERKLÆRING brukes per tidspunkt, men vi kan ikke være mer spesifikke en domenemodellen.
            resultatBegrunnelse = this.revurdering.revurderingsårsak.toResultatBegrunnelse(),
            avsluttet = false,
            funksjonellTid = this.revurdering.opprettet,
        )

        is StatistikkEvent.Behandling.Gjenoppta.Iverksatt -> this.revurdering.toBehandlingsstatistikk(
            gitCommit = gitCommit,
            clock = clock,
            behandlingStatus = BehandlingStatus.Iverksatt,
            behandlingResultat = BehandlingResultat.Gjenopptatt,
            // I praksis så skal bare MOTTATT_KONTROLLERKLÆRING brukes per tidspunkt, men vi kan ikke være mer spesifikke en domenemodellen.
            resultatBegrunnelse = this.revurdering.revurderingsårsak.toResultatBegrunnelse(),
            avsluttet = true,
            funksjonellTid = this.vedtak.opprettet,
        )

        is StatistikkEvent.Behandling.Gjenoppta.Avsluttet -> this.revurdering.toBehandlingsstatistikk(
            gitCommit = gitCommit,
            clock = clock,
            behandlingStatus = BehandlingStatus.Avsluttet,
            behandlingResultat = BehandlingResultat.Avbrutt,
            resultatBegrunnelse = null,
            avsluttet = true,
            funksjonellTid = this.revurdering.avsluttetTidspunkt,
        )
    }
}

internal fun GjenopptaYtelseRevurdering.toBehandlingsstatistikk(
    gitCommit: GitCommit?,
    clock: Clock,
    behandlingStatus: BehandlingStatus,
    behandlingResultat: BehandlingResultat,
    resultatBegrunnelse: String?,
    avsluttet: Boolean,
    funksjonellTid: Tidspunkt,
): BehandlingsstatistikkDto {
    return BehandlingsstatistikkDto(
        behandlingType = Behandlingstype.REVURDERING,
        behandlingTypeBeskrivelse = Behandlingstype.REVURDERING.beskrivelse,
        funksjonellTid = funksjonellTid,
        tekniskTid = Tidspunkt.now(clock),
        registrertDato = this.opprettet.toLocalDate(zoneIdOslo),
        mottattDato = this.opprettet.toLocalDate(zoneIdOslo),
        behandlingId = this.id,
        sakId = this.sakId,
        saksnummer = this.saksnummer.nummer,
        versjon = gitCommit?.value,
        relatertBehandlingId = null,
        avsluttet = avsluttet,
        behandlingStatus = behandlingStatus.toString(),
        behandlingStatusBeskrivelse = behandlingStatus.beskrivelse,
        resultat = behandlingResultat.toString(),
        resultatBegrunnelse = resultatBegrunnelse,
        resultatBeskrivelse = behandlingResultat.beskrivelse,
        saksbehandler = this.saksbehandler.toString(),
        // Det er ikke krav om totrinngsbehandling for stans/gjenoppta, men den som iverksatte kan være forskjellig fra den som startet behandlingen.
        beslutter = null,
        totrinnsbehandling = false,
        // Denne er uforandret i en stans/gjenopptak behandling
        behandlingYtelseDetaljer = this.behandlingYtelseDetaljer(),
    )
}
