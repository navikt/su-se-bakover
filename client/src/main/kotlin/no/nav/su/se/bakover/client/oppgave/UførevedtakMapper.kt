package no.nav.su.se.bakover.client.oppgave

import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig

fun OppgaveConfig.NyttUførevedtak.toBeskrivelse(): String {
    return "Nytt uførevedtak fra pensjon/pesys:\n" +
        "\tPeriode: ${this.periode.toOppgavePeriode()}\n" +
        "\tVedtakstype: ${this.uføreVedtakstype}\n" +
        "\tBehandlingstype: ${this.behandlingstype}\n" +
        "\tSakId: ${this.uføreSakId}\n" +
        "\tVedtakId: ${this.uføreVedtakId}"
}
