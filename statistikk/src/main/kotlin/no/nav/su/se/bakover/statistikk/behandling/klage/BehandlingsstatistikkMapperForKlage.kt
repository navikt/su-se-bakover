package no.nav.su.se.bakover.statistikk.behandling.klage

import no.nav.su.se.bakover.common.GitCommit
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
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
            behandlingStatus = BehandlingStatus.REGISTRERT,
            funksjonellTid = this.klage.opprettet,
        )

        is StatistikkEvent.Behandling.Klage.Avvist -> toDto(
            klage = this.klage,
            gitCommit = gitCommit,
            clock = clock,
            behandlingStatus = BehandlingStatus.IVERKSATT,
            behandlingResultat = BehandlingResultat.Avvist,
            resultatBegrunnelse = this.klage.vilkårsvurderinger.toResultatBegrunnelse(),
            avsluttet = true,
            funksjonellTid = this.vedtak.opprettet,
        )

        is StatistikkEvent.Behandling.Klage.Oversendt -> toDto(
            klage = this.klage,
            gitCommit = gitCommit,
            clock = clock,
            behandlingStatus = BehandlingStatus.OVERSENDT,
            behandlingResultat = BehandlingResultat.Opprettholdt,
            resultatBegrunnelse = (this.klage.vurderinger.vedtaksvurdering as? VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold)?.hjemler?.toResultatBegrunnelse(),
            // Vi legger til en attesteringshendelse når vi oversender til klageinstansen
            funksjonellTid = this.klage.attesteringer.toFunksjonellTid(this.klage.id, clock),
        )

        is StatistikkEvent.Behandling.Klage.Avsluttet -> toDto(
            klage = this.klage,
            gitCommit = gitCommit,
            clock = clock,
            behandlingStatus = BehandlingStatus.AVSLUTTET,
            behandlingResultat = BehandlingResultat.Avbrutt,
            // Klagebehandlingene krever i utgangspunktet totrinngsbehandling, bortsett fra hvis den avsluttes.
            avsluttet = true,
            totrinnsbehandling = false,
            funksjonellTid = this.klage.tidspunktAvsluttet,
        )
    }
}

private fun toDto(
    klage: Klage,
    gitCommit: GitCommit?,
    clock: Clock,
    behandlingStatus: BehandlingStatus,
    behandlingResultat: BehandlingResultat? = null,
    resultatBegrunnelse: String? = null,
    avsluttet: Boolean = false,
    totrinnsbehandling: Boolean = true,
    funksjonellTid: Tidspunkt,
): BehandlingsstatistikkDto {
    val nå = Tidspunkt.now(clock)
    return BehandlingsstatistikkDto(
        behandlingType = Behandlingstype.KLAGE,
        behandlingTypeBeskrivelse = Behandlingstype.KLAGE.beskrivelse,
        funksjonellTid = funksjonellTid,
        tekniskTid = nå,
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
        beslutter = klage.attesteringer.prøvHentSisteAttestering()?.attestant?.navIdent,
        behandlingStatus = behandlingStatus,
        behandlingStatusBeskrivelse = behandlingStatus.beskrivelse,
        resultat = behandlingResultat,
        resultatBeskrivelse = behandlingResultat?.beskrivelse,
        resultatBegrunnelse = resultatBegrunnelse,
    )
}
