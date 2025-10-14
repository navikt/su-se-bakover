package tilbakekreving.application.service.statistikk

import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.statistikk.BehandlingMetode
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import tilbakekreving.domain.AvbruttTilbakekrevingsbehandling
import tilbakekreving.domain.IverksattTilbakekrevingsbehandling
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import java.time.Clock
import java.util.UUID

fun Tilbakekrevingsbehandling.toTilbakeStatistikkOpprettet(
    generellSakStatistikk: GenerellSakStatistikk,
) = toTilbakeStatistikk(
    generellSakStatistikk = generellSakStatistikk,
    behandlingStatus = "REGISTRERT",
)

fun UnderBehandling.Utfylt.toTilbakeStatistikkTilAttestering(
    generellSakStatistikk: GenerellSakStatistikk,
) = toTilbakeStatistikk(
    generellSakStatistikk = generellSakStatistikk,
    behandlingStatus = "TIL_ATTESTERING",
    behandlingResultat = utledResultat(vurderingerMedKrav),
)

fun UnderBehandling.Utfylt.toTilbakeStatistikkUnderkjent(
    generellSakStatistikk: GenerellSakStatistikk,
) = toTilbakeStatistikk(
    generellSakStatistikk = generellSakStatistikk,
    behandlingStatus = "UNDERKJENT",
    behandlingResultat = utledResultat(vurderingerMedKrav),
)

fun AvbruttTilbakekrevingsbehandling.toTilbakeStatistikkAvbryt(
    generellSakStatistikk: GenerellSakStatistikk,
) = toTilbakeStatistikk(
    generellSakStatistikk = generellSakStatistikk,
    behandlingStatus = "AVBRUTT",
    ansvarligBeslutter = this.avsluttetAv.navIdent,
)

fun AvbruttTilbakekrevingsbehandling.toTilbakeStatistikkAnnuller(
    generellSakStatistikk: GenerellSakStatistikk,
) = toTilbakeStatistikk(
    generellSakStatistikk = generellSakStatistikk,
    behandlingStatus = "AVBRUTT",
    ansvarligBeslutter = this.avsluttetAv.navIdent,
)

fun IverksattTilbakekrevingsbehandling.toTilbakeStatistikkIverksatt(
    generellSakStatistikk: GenerellSakStatistikk,
    ferdigbehandletTid: Tidspunkt? = null,
) = toTilbakeStatistikk(
    generellSakStatistikk = generellSakStatistikk,
    behandlingStatus = "IVERKSATT",
    ferdigbehandletTid = ferdigbehandletTid,
    behandlingResultat = utledResultat(vurderingerMedKrav),
    ansvarligBeslutter = this.attesteringer.hentSisteAttestering().attestant.navIdent,
    tilbakekrevBeløp = this.vurderingerMedKrav.bruttoSkalTilbakekreveSummert.toLong(),
)

private fun utledResultat(vurderingerMedKrav: VurderingerMedKrav) =
    if (vurderingerMedKrav.minstEnPeriodeSkalTilbakekreves()) {
        // TODO bjg hva skal riktig verdi være her?
        "SKAL_TILBAKEKREVE"
    } else {
        "SKAL_IKKE_TILBAKEKREVE"
    }

/*
* Nødvendig for statistikk men ligger ikke på tilbakekrevingbehandling
*/
data class GenerellSakStatistikk(
    val sakType: Sakstype,
    val tekniskTid: Tidspunkt,
    val relatertId: UUID?,
) {
    companion object {
        fun create(
            clock: Clock,
            sak: Sak,
            relatertVedtakId: String,
        ) = GenerellSakStatistikk(
            sakType = sak.type,
            tekniskTid = Tidspunkt.now(clock),
            // TODO er eksternVedtakId UUID hos oppdrag eller sender vi et løpenummer?????
            relatertId = sak.finnBehandlingTilVedtak(UUID.fromString(relatertVedtakId))?.id?.value,
        )
    }
}

fun Tilbakekrevingsbehandling.toTilbakeStatistikk(
    generellSakStatistikk: GenerellSakStatistikk,
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
        tekniskTid = generellSakStatistikk.tekniskTid,
        sakId = behandling.sakId,
        saksnummer = behandling.saksnummer.nummer,
        behandlingId = behandling.id.value,
        relatertBehandlingId = null, // TODO trello - 252-statistikk-relatert-id
        aktorId = behandling.fnr,
        sakYtelse = when (generellSakStatistikk.sakType) {
            Sakstype.ALDER -> "SUALDER"
            Sakstype.UFØRE -> "SUUFORE"
        },
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
