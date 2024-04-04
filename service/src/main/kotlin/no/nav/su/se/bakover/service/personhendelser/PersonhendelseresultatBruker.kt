package no.nav.su.se.bakover.service.personhendelser

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr

sealed interface PersonhendelseresultatBruker {
    fun ikkeTreff(): Boolean = this is IkkeRelevantHendelseForBruker

    fun unikeSaksnummer(): List<Saksnummer> = when (this) {
        is IkkeRelevantHendelseForBruker -> emptyList()
        is TreffPåBruker -> listOf(this.saksnummer)
    }

    fun antallTreff(): Int

    sealed interface IkkeRelevantHendelseForBruker : PersonhendelseresultatBruker {
        val identer: List<String>

        /** Enten har vi ikke en sak, eller så har ikke den saken vedtak av typen søknad, endring, opphør. */
        data class IngenSakEllerVedtak(override val identer: List<String>) : IkkeRelevantHendelseForBruker {
            override fun antallTreff(): Int = 0
        }

        /** Vi har en sak med vedtak av typen søknad, endring, opphør; men ingen av disse var aktive etter fraOgMed dato */
        data class IngenAktiveVedtak(
            override val identer: List<String>,
            val saksnummer: Saksnummer,
            val fnr: Fnr,
        ) : IkkeRelevantHendelseForBruker {
            override fun antallTreff(): Int = 0
        }
    }

    data class TreffPåBruker(
        val saksnummer: Saksnummer,
        val fnr: Fnr,
        val identer: List<String>,
    ) : PersonhendelseresultatBruker {
        override fun antallTreff(): Int = 1
    }
}
