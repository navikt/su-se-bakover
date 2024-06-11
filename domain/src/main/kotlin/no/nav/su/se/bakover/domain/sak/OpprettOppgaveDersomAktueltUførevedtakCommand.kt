package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.person.Fnr

data class OpprettOppgaveDersomAktueltUførevedtakCommand(
    val fnr: Fnr,
    val periode: PeriodeMedOptionalTilOgMed,
    val uføreSakId: String,
    val uføreVedtakId: String,
    val uføreVedtakstype: String,
    val behandlingstype: UførevedtakBehandlingstype,
) {

    fun erBehandlingstypeAutomatisk() = behandlingstype == UførevedtakBehandlingstype.AUTOMATISK
    fun erVedtakstypeRegulering() = uføreVedtakstype == "REGULERING"

    override fun toString() = "OpprettOppgaveDersomAktueltUførevedtakCommand(fnr=***, periode=$periode, uføreSakId=$uføreSakId, uføreVedtakId=$uføreVedtakId, uføreVedtakstype=$uføreVedtakstype)"
    fun toSikkerloggString() = "OpprettOppgaveDersomAktueltUførevedtakCommand(fnr=$fnr, periode=$periode, uføreSakId=$uføreSakId, uføreVedtakId=$uføreVedtakId, uføreVedtakstype=$uføreVedtakstype)"
}

enum class UførevedtakBehandlingstype {
    AUTOMATISK,
    DELVIS_AUTOMATISK,
    MANUELL,
}
