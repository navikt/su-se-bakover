package no.nav.su.se.bakover.statistikk.behandling.klage

import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus
import no.nav.su.se.bakover.statistikk.behandling.BehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.Behandlingstype
import no.nav.su.se.bakover.statistikk.behandling.toFunksjonellTid
import java.time.Clock

internal fun StatistikkEvent.Behandling.Klage.toBehandlingsstatistikkDto(
    gitCommit: GitCommit?,
    clock: Clock,
): BehandlingsstatistikkDto {
    return when (this) {
        is StatistikkEvent.Behandling.Klage.Opprettet -> toDto(
            klage = this.klage,
            gitCommit = gitCommit,
            clock = clock,
            behandlingStatus = BehandlingStatus.Registrert,
            behandlingResultat = null,
            resultatBegrunnelse = null,
            avsluttet = false,
            totrinnsbehandling = true,
            funksjonellTid = this.klage.opprettet,
            beslutter = null,
        )

        is StatistikkEvent.Behandling.Klage.Avvist -> toDto(
            klage = this.klage,
            gitCommit = gitCommit,
            clock = clock,
            behandlingStatus = BehandlingStatus.Iverksatt,
            behandlingResultat = BehandlingResultat.Avvist,
            resultatBegrunnelse = this.klage.vilkårsvurderinger.toResultatBegrunnelse(),
            avsluttet = true,
            totrinnsbehandling = true,
            funksjonellTid = this.vedtak.opprettet,
            beslutter = this.vedtak.attestant,
        )

        is StatistikkEvent.Behandling.Klage.Oversendt -> toDto(
            klage = this.klage,
            gitCommit = gitCommit,
            clock = clock,
            behandlingStatus = BehandlingStatus.OversendtKlage,
            behandlingResultat = BehandlingResultat.OpprettholdtKlage,
            resultatBegrunnelse = (this.klage.vurderinger.vedtaksvurdering as? VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold)?.hjemler?.toResultatBegrunnelse(),
            // Spesialtilfelle der vi sender en innstilling (ikke vedtak) til klageinstansen som vil si at behandlingen ikke er avsluttet for brukeren.
            avsluttet = false,
            totrinnsbehandling = true,
            // Vi legger til en attesteringshendelse når vi oversender til klageinstansen
            funksjonellTid = this.klage.attesteringer.toFunksjonellTid(this.klage.id, clock),
            beslutter = this.klage.prøvHentSisteAttestant(),
        )

        is StatistikkEvent.Behandling.Klage.Avsluttet -> toDto(
            klage = this.klage,
            gitCommit = gitCommit,
            clock = clock,
            behandlingStatus = BehandlingStatus.Avsluttet,
            behandlingResultat = BehandlingResultat.Avbrutt,
            resultatBegrunnelse = null,
            // Klagebehandlingene krever i utgangspunktet totrinnsbehandling, bortsett fra hvis den avsluttes.
            avsluttet = true,
            totrinnsbehandling = false,
            funksjonellTid = this.klage.avsluttetTidspunkt,
            beslutter = null,
        )
    }
}

private fun toDto(
    klage: Klage,
    gitCommit: GitCommit?,
    clock: Clock,
    behandlingStatus: BehandlingStatus,
    behandlingResultat: BehandlingResultat?,
    resultatBegrunnelse: String?,
    avsluttet: Boolean,
    totrinnsbehandling: Boolean,
    funksjonellTid: Tidspunkt,
    beslutter: NavIdentBruker.Attestant?,
): BehandlingsstatistikkDto {
    return BehandlingsstatistikkDto(
        behandlingType = Behandlingstype.KLAGE,
        behandlingTypeBeskrivelse = Behandlingstype.KLAGE.beskrivelse,
        funksjonellTid = funksjonellTid,
        tekniskTid = Tidspunkt.now(clock),
        registrertDato = klage.opprettet.toLocalDate(zoneIdOslo),
        mottattDato = klage.datoKlageMottatt,
        behandlingId = klage.id,
        sakId = klage.sakId,
        saksnummer = klage.saksnummer.nummer,
        versjon = gitCommit?.value,
        saksbehandler = klage.saksbehandler.navIdent,
        relatertBehandlingId = klage.vilkårsvurderinger?.vedtakId,
        avsluttet = avsluttet,
        totrinnsbehandling = totrinnsbehandling,
        beslutter = beslutter?.toString(),
        behandlingStatus = behandlingStatus.toString(),
        behandlingStatusBeskrivelse = behandlingStatus.beskrivelse,
        resultat = behandlingResultat?.toString(),
        resultatBeskrivelse = behandlingResultat?.beskrivelse,
        resultatBegrunnelse = resultatBegrunnelse,
    )
}
