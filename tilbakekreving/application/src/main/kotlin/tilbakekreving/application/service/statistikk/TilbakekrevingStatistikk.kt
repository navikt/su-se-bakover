package tilbakekreving.application.service.statistikk

import no.nav.su.se.bakover.common.domain.statistikk.BehandlingMetode
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.AvbruttTilbakekrevingsbehandling
import tilbakekreving.domain.IverksattTilbakekrevingsbehandling
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import java.time.Clock

fun Tilbakekrevingsbehandling.toTilbakeStatistikkOpprettet(
    clock: Clock,
) = toTilbakeStatistikk(
    clock = clock,
    behandlingStatus = "REGISTRERT",
)

fun UnderBehandling.Utfylt.toTilbakeStatistikkTilAttestering(
    clock: Clock,
) = toTilbakeStatistikk(
    clock = clock,
    behandlingStatus = "TIL_ATTESTERING",
    behandlingResultat = utledResultat(vurderingerMedKrav),
)

fun UnderBehandling.Utfylt.toTilbakeStatistikkUnderkjent(
    clock: Clock,
) = toTilbakeStatistikk(
    clock = clock,
    behandlingStatus = "UNDERKJENT",
    behandlingResultat = utledResultat(vurderingerMedKrav),
)

fun AvbruttTilbakekrevingsbehandling.toTilbakeStatistikkAvbryt(
    clock: Clock,
) = toTilbakeStatistikk(
    clock = clock,
    behandlingStatus = "AVBRUTT",
    ansvarligBeslutter = this.avsluttetAv.navIdent,
)

fun AvbruttTilbakekrevingsbehandling.toTilbakeStatistikkAnnuller(
    clock: Clock,
) = toTilbakeStatistikk(
    clock = clock,
    behandlingStatus = "AVBRUTT",
    ansvarligBeslutter = this.avsluttetAv.navIdent,
)

fun IverksattTilbakekrevingsbehandling.toTilbakeStatistikkIverksatt(
    clock: Clock,
    ferdigbehandletTid: Tidspunkt? = null,
) = toTilbakeStatistikk(
    clock = clock,
    behandlingStatus = "IVERKSATT",
    ferdigbehandletTid = ferdigbehandletTid,
    behandlingResultat = utledResultat(vurderingerMedKrav),
    ansvarligBeslutter = this.attesteringer.hentSisteAttestering().attestant.navIdent,
    tilbakekrevBeløp = this.vurderingerMedKrav.bruttoSkalTilbakekreveSummert.toLong(),
)

private fun utledResultat(vurderingerMedKrav: VurderingerMedKrav) = if (vurderingerMedKrav.minstEnPeriodeSkalTilbakekreves()) {
    // TODO bjg hva skal riktig verdi være her?
    "SKAL_TILBAKEKREVE"
} else {
    "SKAL_IKKE_TILBAKEKREVE"
}

fun Tilbakekrevingsbehandling.toTilbakeStatistikk(
    clock: Clock,
    behandlingStatus: String,
    ferdigbehandletTid: Tidspunkt? = null,
    behandlingResultat: String? = null,
    resultatBegrunnelse: String? = null,
    ansvarligBeslutter: String? = null,
    tilbakekrevBeløp: Long? = null,
): SakStatistikk {
    val behandling = this
    return SakStatistikk(
        hendelseTid = behandling.opprettet,
        tekniskTid = Tidspunkt.now(clock),
        sakId = behandling.sakId,
        saksnummer = behandling.saksnummer.nummer,
        behandlingId = behandling.id.value,
        relatertBehandlingId = null, // TODO trello - 252-statistikk-relatert-id
        aktorId = behandling.fnr,
        sakYtelse = "alder", // TODO bjg Tilbakekreving har ikke saktype.. men kan trolig utledes sammen med relatertid..
        behandlingType = "TILBAKEKREVING",
        mottattTid = behandling.opprettet,
        registrertTid = behandling.opprettet,
        ferdigbehandletTid = ferdigbehandletTid,
        utbetaltTid = null,
        behandlingStatus = behandlingStatus,
        behandlingResultat = behandlingResultat,
        resultatBegrunnelse = resultatBegrunnelse,
        opprettetAv = behandling.opprettetAv.navIdent,
        saksbehandler = behandling.opprettetAv.navIdent, // TODO bjg Må verifiseres
        ansvarligBeslutter = ansvarligBeslutter,
        behandlingMetode = BehandlingMetode.Manuell,
        tilbakekrevBeløp = tilbakekrevBeløp,
        // TODO bjg Hva skal disse feltene være?
        funksjonellPeriodeFom = null,
        funksjonellPeriodeTom = null,
    )
}
