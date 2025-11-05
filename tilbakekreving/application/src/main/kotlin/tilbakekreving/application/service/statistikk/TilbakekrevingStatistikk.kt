package tilbakekreving.application.service.statistikk

import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.statistikk.BehandlingMetode
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import tilbakekreving.domain.AvbruttTilbakekrevingsbehandling
import tilbakekreving.domain.IverksattTilbakekrevingsbehandling
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandling
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import java.time.Clock
import java.util.UUID

fun OpprettetTilbakekrevingsbehandling.toTilbakeStatistikkOpprettet(
    generellSakStatistikk: GenerellSakStatistikk,
) = toTilbakeStatistikk(
    generellSakStatistikk = generellSakStatistikk,
    behandlingStatus = "REGISTRERT",
)

// TODO bjg - statistikk for nye hendelser

fun UnderBehandling.MedKravgrunnlag.Utfylt.toTilbakeStatistikkTilAttestering(
    generellSakStatistikk: GenerellSakStatistikk,
): SakStatistikk {
    val behandlingResultat = utledResultat(vurderingerMedKrav)
    return toTilbakeStatistikk(
        generellSakStatistikk = generellSakStatistikk,
        behandlingStatus = "TIL_ATTESTERING",
        behandlingResultat = behandlingResultat.name,
        tilbakekrevBeløp = if (behandlingResultat == Resultat.SKAL_TILBAKEKREVE) {
            this.vurderingerMedKrav.bruttoSkalTilbakekreveSummert.toLong()
        } else {
            null
        },
    )
}

fun UnderBehandling.MedKravgrunnlag.Utfylt.toTilbakeStatistikkUnderkjent(
    generellSakStatistikk: GenerellSakStatistikk,
): SakStatistikk {
    val behandlingResultat = utledResultat(vurderingerMedKrav)
    return toTilbakeStatistikk(
        generellSakStatistikk = generellSakStatistikk,
        behandlingStatus = "UNDERKJENT",
        behandlingResultat = behandlingResultat.name,
        tilbakekrevBeløp = if (behandlingResultat == Resultat.SKAL_TILBAKEKREVE) {
            this.vurderingerMedKrav.bruttoSkalTilbakekreveSummert.toLong()
        } else {
            null
        },
    )
}

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
    behandlingResultat = utledResultat(vurderingerMedKrav).name,
    ansvarligBeslutter = this.attesteringer.hentSisteAttestering().attestant.navIdent,
    tilbakekrevBeløp = this.vurderingerMedKrav.bruttoSkalTilbakekreveSummert.toLong(),
)

private fun utledResultat(vurderingerMedKrav: VurderingerMedKrav) =
    if (vurderingerMedKrav.minstEnPeriodeSkalTilbakekreves()) {
        Resultat.SKAL_TILBAKEKREVE
    } else {
        Resultat.SKAL_IKKE_TILBAKEKREVE
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
        ) = GenerellSakStatistikk(
            sakType = sak.type,
            tekniskTid = Tidspunkt.now(clock),
            relatertId = null, // TODO trello - 252-statistikk-relatert-id
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
    return SakStatistikk(
        hendelseTid = opprettet,
        tekniskTid = generellSakStatistikk.tekniskTid,
        sakId = sakId,
        saksnummer = saksnummer.nummer,
        behandlingId = id.value,
        relatertBehandlingId = generellSakStatistikk.relatertId,
        aktorId = fnr,
        sakYtelse = when (generellSakStatistikk.sakType) {
            Sakstype.ALDER -> "SUALDER"
            Sakstype.UFØRE -> "SUUFORE"
        },
        behandlingType = "TILBAKEKREVING",
        mottattTid = opprettet,
        registrertTid = opprettet,
        ferdigbehandletTid = ferdigbehandletTid,
        utbetaltTid = null,
        behandlingStatus = behandlingStatus,
        behandlingResultat = behandlingResultat,
        resultatBegrunnelse = resultatBegrunnelse,
        opprettetAv = opprettetAv.navIdent,
        saksbehandler = opprettetAv.navIdent,
        ansvarligBeslutter = ansvarligBeslutter,
        behandlingMetode = BehandlingMetode.Manuell,
        tilbakekrevBeløp = tilbakekrevBeløp,
        funksjonellPeriodeFom = kravgrunnlag?.periode?.fraOgMed,
        funksjonellPeriodeTom = kravgrunnlag?.periode?.tilOgMed,
    )
}

private enum class Resultat {
    // TODO bjg hva skal riktig verdi være her?
    SKAL_TILBAKEKREVE,
    SKAL_IKKE_TILBAKEKREVE,
}
