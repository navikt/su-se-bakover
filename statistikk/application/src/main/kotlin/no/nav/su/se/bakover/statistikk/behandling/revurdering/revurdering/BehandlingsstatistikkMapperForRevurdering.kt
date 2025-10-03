package no.nav.su.se.bakover.statistikk.behandling.revurdering.revurdering

import behandling.revurdering.domain.Opphørsgrunn
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus
import no.nav.su.se.bakover.statistikk.behandling.BehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.Behandlingstype
import no.nav.su.se.bakover.statistikk.behandling.behandlingYtelseDetaljer
import no.nav.su.se.bakover.statistikk.behandling.toFunksjonellTid
import no.nav.su.se.bakover.statistikk.sak.toYtelseType
import java.time.Clock
import java.util.UUID

internal fun StatistikkEvent.Behandling.Revurdering.toBehandlingsstatistikkDto(
    gitCommit: GitCommit?,
    clock: Clock,
): BehandlingsstatistikkDto {
    return when (this) {
        is StatistikkEvent.Behandling.Revurdering.Opprettet -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.Registrert,
            behandlingResultat = null,
            resultatBegrunnelse = null,
            avsluttet = false,
            totrinnsbehandling = true,
            beslutter = null,
            // Selvom en opprettet revurdering kopierer med seg `behandlingYtelseDetaljer` fra tidslinja, blir det unaturlig å sette det på dette tidspunktet.
            behandlingYtelseDetaljer = emptyList(),
            funksjonellTid = this.revurdering.opprettet,
            saksbehandler = this.revurdering.saksbehandler,
            relatertBehandlingId = relatertId,
        )

        is StatistikkEvent.Behandling.Revurdering.TilAttestering.Innvilget -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.TilAttestering,
            behandlingResultat = BehandlingResultat.Innvilget,
            resultatBegrunnelse = null,
            avsluttet = false,
            totrinnsbehandling = true,
            beslutter = null,
            behandlingYtelseDetaljer = this.revurdering.behandlingYtelseDetaljer(),
            // Her lagres det ikke noe mer nøyaktig.
            funksjonellTid = Tidspunkt.now(clock),
            saksbehandler = this.revurdering.saksbehandler,
        )

        is StatistikkEvent.Behandling.Revurdering.TilAttestering.Opphør -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.TilAttestering,
            behandlingResultat = BehandlingResultat.Opphør,
            resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
            avsluttet = false,
            totrinnsbehandling = true,
            beslutter = null,
            behandlingYtelseDetaljer = this.revurdering.behandlingYtelseDetaljer(),
            // Her lagres det ikke noe mer nøyaktig.
            funksjonellTid = Tidspunkt.now(clock),
            saksbehandler = this.revurdering.saksbehandler,
        )

        is StatistikkEvent.Behandling.Revurdering.Underkjent.Innvilget -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.Underkjent,
            behandlingResultat = BehandlingResultat.Innvilget,
            resultatBegrunnelse = null,
            avsluttet = false,
            totrinnsbehandling = true,
            beslutter = this.revurdering.prøvHentSisteAttestant(),
            behandlingYtelseDetaljer = this.revurdering.behandlingYtelseDetaljer(),
            funksjonellTid = this.revurdering.attesteringer.toFunksjonellTid(this.revurdering.id.value, clock),
            saksbehandler = this.revurdering.saksbehandler,
        )

        is StatistikkEvent.Behandling.Revurdering.Underkjent.Opphør -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.Underkjent,
            behandlingResultat = BehandlingResultat.Opphør,
            resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
            avsluttet = false,
            totrinnsbehandling = true,
            beslutter = this.revurdering.prøvHentSisteAttestant(),
            behandlingYtelseDetaljer = this.revurdering.behandlingYtelseDetaljer(),
            funksjonellTid = this.revurdering.attesteringer.toFunksjonellTid(this.revurdering.id.value, clock),
            saksbehandler = this.revurdering.saksbehandler,
        )

        is StatistikkEvent.Behandling.Revurdering.Iverksatt.Opphørt -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.Iverksatt,
            behandlingResultat = BehandlingResultat.Opphør,
            resultatBegrunnelse = listUtOpphørsgrunner(revurdering.utledOpphørsgrunner(clock)),
            avsluttet = true,
            totrinnsbehandling = true,
            beslutter = this.revurdering.prøvHentSisteAttestant(),
            behandlingYtelseDetaljer = this.revurdering.behandlingYtelseDetaljer(),
            funksjonellTid = this.vedtak.opprettet,
            saksbehandler = this.revurdering.saksbehandler,
        )

        is StatistikkEvent.Behandling.Revurdering.Iverksatt.Innvilget -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.Iverksatt,
            behandlingResultat = BehandlingResultat.Innvilget,
            resultatBegrunnelse = null,
            avsluttet = true,
            totrinnsbehandling = true,
            beslutter = this.revurdering.prøvHentSisteAttestant(),
            behandlingYtelseDetaljer = this.revurdering.behandlingYtelseDetaljer(),
            funksjonellTid = this.vedtak.opprettet,
            saksbehandler = this.revurdering.saksbehandler,
        )

        is StatistikkEvent.Behandling.Revurdering.Avsluttet -> this.revurdering.toDto(
            clock = clock,
            gitCommit = gitCommit,
            behandlingStatus = BehandlingStatus.Avsluttet,
            behandlingResultat = BehandlingResultat.Avbrutt,
            resultatBegrunnelse = null,
            avsluttet = true,
            totrinnsbehandling = false,
            beslutter = null,
            behandlingYtelseDetaljer = this.revurdering.behandlingYtelseDetaljer(),
            funksjonellTid = this.revurdering.avsluttetTidspunkt,
            saksbehandler = this.saksbehandler,
        )
    }
}

private fun Revurdering.toDto(
    clock: Clock,
    gitCommit: GitCommit?,
    behandlingStatus: BehandlingStatus,
    behandlingResultat: BehandlingResultat?,
    resultatBegrunnelse: String?,
    avsluttet: Boolean,
    totrinnsbehandling: Boolean,
    beslutter: NavIdentBruker.Attestant?,
    behandlingYtelseDetaljer: List<BehandlingsstatistikkDto.BehandlingYtelseDetaljer>,
    funksjonellTid: Tidspunkt,
    saksbehandler: NavIdentBruker.Saksbehandler,
    relatertBehandlingId: UUID? = null,
): BehandlingsstatistikkDto {
    return BehandlingsstatistikkDto(
        behandlingType = Behandlingstype.REVURDERING,
        behandlingTypeBeskrivelse = Behandlingstype.REVURDERING.beskrivelse,
        funksjonellTid = funksjonellTid,
        tekniskTid = Tidspunkt.now(clock),
        registrertDato = this.opprettet.toLocalDate(zoneIdOslo),
        mottattDato = this.opprettet.toLocalDate(zoneIdOslo),
        behandlingId = this.id.value,
        sakId = this.sakId,
        saksnummer = this.saksnummer.nummer,
        versjon = gitCommit?.value,
        saksbehandler = saksbehandler.toString(),
        // En revurdering kan være knyttet til flere tidligere behandlinger/vedtak, så det er bedre å sette denne til null. Behandlingene knyttes via sak og tid.
        // Knytter den til klage hvis omgjøring
        relatertBehandlingId = relatertBehandlingId,
        avsluttet = avsluttet,
        beslutter = beslutter?.toString(),
        // Revurdering krever i utgangspunktet totrinnsbehandling, med unntak av lukking/avslutting.
        totrinnsbehandling = totrinnsbehandling,
        behandlingYtelseDetaljer = behandlingYtelseDetaljer,
        behandlingStatus = behandlingStatus.toString(),
        behandlingStatusBeskrivelse = behandlingStatus.beskrivelse,
        resultat = behandlingResultat?.toString(),
        resultatBeskrivelse = behandlingResultat?.beskrivelse,
        resultatBegrunnelse = resultatBegrunnelse,
        ytelseType = sakstype.toYtelseType(),
        omgjøringsgrunn = this.omgjøringsgrunn?.name,
    )
}

private fun listUtOpphørsgrunner(opphørsgrunner: List<Opphørsgrunn>): String? {
    return if (opphørsgrunner.isEmpty()) null else opphørsgrunner.joinToString(",")
}
