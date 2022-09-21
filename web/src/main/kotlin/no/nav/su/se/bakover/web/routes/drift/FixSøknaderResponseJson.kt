package no.nav.su.se.bakover.web.routes.drift

import no.nav.su.se.bakover.service.søknad.OpprettManglendeJournalpostOgOppgaveResultat

data class FixSøknaderResponseJson(
    val journalposteringer: Journalposteringer,
    val oppgaver: Oppgaver,
) {
    data class Journalposteringer(
        val ok: List<Journalpost>,
        val feilet: List<Feilet>,
    )

    data class Journalpost(
        val sakId: String,
        val søknadId: String,
        val journalpostId: String,
    )

    data class Oppgaver(
        val ok: List<Oppgave>,
        val feilet: List<Feilet>,
    )

    data class Oppgave(
        val sakId: String,
        val søknadId: String,
        val oppgaveId: String,
    )

    data class Feilet(
        val sakId: String,
        val søknadId: String,
        val grunn: String,
    )

    companion object {
        fun OpprettManglendeJournalpostOgOppgaveResultat.toJson() = FixSøknaderResponseJson(
            journalposteringer = Journalposteringer(
                ok = this.journalpostResultat.mapNotNull { it.orNull() }.map {
                    Journalpost(
                        sakId = it.sakId.toString(),
                        søknadId = it.id.toString(),
                        journalpostId = it.journalpostId.toString(),
                    )
                },
                feilet = this.journalpostResultat.mapNotNull { it.swap().orNull() }.map {
                    Feilet(
                        it.sakId.toString(),
                        søknadId = it.søknadId.toString(),
                        grunn = it.grunn,
                    )
                },
            ),
            oppgaver = Oppgaver(
                ok = this.oppgaveResultat.mapNotNull { it.orNull() }.map {
                    Oppgave(
                        it.sakId.toString(),
                        søknadId = it.id.toString(),
                        oppgaveId = it.oppgaveId.toString(),
                    )
                },
                feilet = this.oppgaveResultat.mapNotNull { it.swap().orNull() }.map {
                    Feilet(
                        it.sakId.toString(),
                        søknadId = it.søknadId.toString(),
                        grunn = it.grunn,
                    )
                },
            ),
        )
    }
}
