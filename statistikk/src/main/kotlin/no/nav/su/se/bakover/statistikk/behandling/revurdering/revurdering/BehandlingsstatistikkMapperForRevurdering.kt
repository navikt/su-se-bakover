package no.nav.su.se.bakover.statistikk.behandling.revurdering.revurdering

import no.nav.su.se.bakover.common.GitCommit
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus
import no.nav.su.se.bakover.statistikk.behandling.BehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.Behandlingstype
import no.nav.su.se.bakover.statistikk.behandling.behandlingYtelseDetaljer
import no.nav.su.se.bakover.statistikk.behandling.toFunksjonellTid
import java.time.Clock

internal fun StatistikkEvent.Behandling.Revurdering.toBehandlingsstatistikkDto(
    gitCommit: GitCommit?,
    clock: Clock,
): BehandlingsstatistikkDto {

    return when (this) {
        is StatistikkEvent.Behandling.Revurdering.Opprettet -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.REGISTRERT,
            // Selvom en opprettet revurdering kopierer med seg `behandlingYtelseDetaljer` fra tidslinja, blir det unaturlig å sette det på dette tidspunktet.
            behandlingYtelseDetaljer = emptyList(),
            funksjonellTid = this.revurdering.opprettet,
        )

        is StatistikkEvent.Behandling.Revurdering.TilAttestering.Innvilget -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.TIL_ATTESTERING,
            behandlingResultat = BehandlingResultat.Innvilget,
            funksjonellTid = this.revurdering.attesteringer.toFunksjonellTid(this.revurdering.id, clock),
        )

        is StatistikkEvent.Behandling.Revurdering.TilAttestering.Opphør -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.TIL_ATTESTERING,
            behandlingResultat = BehandlingResultat.Opphør,
            resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
            funksjonellTid = this.revurdering.attesteringer.toFunksjonellTid(this.revurdering.id, clock),
        )

        is StatistikkEvent.Behandling.Revurdering.Underkjent.Innvilget -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.UNDERKJENT,
            behandlingResultat = BehandlingResultat.Innvilget,
            beslutter = this.revurdering.attestering.attestant,
            funksjonellTid = this.revurdering.attesteringer.toFunksjonellTid(this.revurdering.id, clock),
        )

        is StatistikkEvent.Behandling.Revurdering.Underkjent.Opphør -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.UNDERKJENT,
            behandlingResultat = BehandlingResultat.Opphør,
            resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
            beslutter = this.revurdering.attestering.attestant,
            funksjonellTid = this.revurdering.attesteringer.toFunksjonellTid(this.revurdering.id, clock),
        )

        is StatistikkEvent.Behandling.Revurdering.Iverksatt.Opphørt -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.IVERKSATT,
            behandlingResultat = BehandlingResultat.Opphør,
            resultatBegrunnelse = listUtOpphørsgrunner(revurdering.utledOpphørsgrunner(clock)),
            avsluttet = true,
            beslutter = this.revurdering.attestering.attestant,
            funksjonellTid = this.vedtak.opprettet,
        )

        is StatistikkEvent.Behandling.Revurdering.Iverksatt.Innvilget -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.IVERKSATT,
            behandlingResultat = BehandlingResultat.Innvilget,
            avsluttet = true,
            beslutter = this.revurdering.attestering.attestant,
            funksjonellTid = this.vedtak.opprettet,
        )

        is StatistikkEvent.Behandling.Revurdering.Avsluttet -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.AVSLUTTET,
            behandlingResultat = BehandlingResultat.Avbrutt,
            avsluttet = true,
            totrinnsbehandling = false,
            funksjonellTid = this.revurdering.tidspunktAvsluttet,
            saksbehandler = this.saksbehandler,
        )
    }
}

private fun Revurdering.toDto(
    clock: Clock,
    gitCommit: GitCommit?,
    behandlingStatus: BehandlingStatus,
    behandlingResultat: BehandlingResultat? = null,
    resultatBegrunnelse: String? = null,
    avsluttet: Boolean = false,
    totrinnsbehandling: Boolean = true,
    beslutter: NavIdentBruker.Attestant? = null,
    behandlingYtelseDetaljer: List<BehandlingsstatistikkDto.BehandlingYtelseDetaljer> = this.behandlingYtelseDetaljer(),
    funksjonellTid: Tidspunkt,
    saksbehandler: NavIdentBruker.Saksbehandler = this.saksbehandler,
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
        saksbehandler = saksbehandler.toString(),
        // En revurdering kan være knyttet til flere tidligere behandlinger/vedtak, så det er bedre å sette denne til null. Behandlingene knyttes via sak og tid.
        relatertBehandlingId = null,
        avsluttet = avsluttet,
        beslutter = beslutter?.toString(),
        // Revurdering krever i utgangspunktet totrinnsbehandling, med unntak av lukking/avslutting.
        totrinnsbehandling = totrinnsbehandling,
        behandlingYtelseDetaljer = behandlingYtelseDetaljer,
        behandlingStatus = behandlingStatus,
        behandlingStatusBeskrivelse = behandlingStatus.beskrivelse,
        resultat = behandlingResultat,
        resultatBeskrivelse = behandlingResultat?.beskrivelse,
        resultatBegrunnelse = resultatBegrunnelse,
    )
}

private fun listUtOpphørsgrunner(opphørsgrunner: List<Opphørsgrunn>): String? {
    return if (opphørsgrunner.isEmpty()) null else opphørsgrunner.joinToString(",")
}
