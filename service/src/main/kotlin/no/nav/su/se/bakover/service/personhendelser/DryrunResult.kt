package no.nav.su.se.bakover.service.personhendelser

import no.nav.su.se.bakover.common.domain.Saksnummer

data class DryrunResult(
    val perHendelse: List<DryRunResultPerHendelse>,
) {
    companion object {
        fun empty() = DryrunResult(emptyList())
    }

    fun leggTilHendelse(resultatBruker: PersonhendelseresultatBruker, resultatEps: PersonhendelseresultatEps) =
        DryrunResult(perHendelse + DryRunResultPerHendelse(resultatBruker, resultatEps))

    val antallForkastet: Int by lazy { forkastet.size }
    val antallBruker: Int by lazy { bruker.sumOf { it.antallTreff() } }
    val antallEps: Int by lazy { eps.sumOf { it.antallTreff() } }
    val antallOppgaver: Int by lazy {
        oppgaver.size
    }

    val forkastet: List<DryRunResultPerHendelse> by lazy { perHendelse.filter { it.ikkeTreff() } }
    val bruker: List<PersonhendelseresultatBruker> by lazy { perHendelse.map { it.resultatBruker } }
    val eps: List<PersonhendelseresultatEps> by lazy { perHendelse.map { it.resultatEps } }
    val oppgaver: List<Saksnummer> by lazy {
        (bruker.flatMap { it.unikeSaksnummer() } + eps.flatMap { it.unikeSaksnummer() }).distinct()
            .sortedBy { it.nummer }
    }

    data class DryRunResultPerHendelse(
        val resultatBruker: PersonhendelseresultatBruker,
        val resultatEps: PersonhendelseresultatEps,
    ) {
        fun ikkeTreff(): Boolean = resultatBruker.ikkeTreff() && resultatEps.ikkeTreff()
    }

    override fun toString() =
        "DryrunResult(antallHendelser=${perHendelse.size}, $antallForkastet=$antallForkastet, antallBruker=$antallBruker, antallEps=$antallEps, antallOppgaver=$antallOppgaver). Se sikkerlogg for mer detaljer"

    fun toSikkerloggString(): String =
        "DryrunResult(antallHendelser=${perHendelse.size},antallForkastet=$antallForkastet, antallBruker=$antallBruker, antallEps=$antallEps, antallOppgaver=$antallOppgaver). Forkastet: $forkastet, Bruker: $bruker, Eps: $eps, Oppgaver: $oppgaver"
}
