package no.nav.su.se.bakover.service.personhendelser

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr

sealed interface PersonhendelseresultatEps {
    fun ikkeTreff(): Boolean

    fun unikeSaksnummer(): List<Saksnummer> = when (this) {
        is IkkeTreffPåEps -> emptyList()
        is TreffPåEnEllerFlereEps -> this.treff.map { it.brukersSaksnummer }
    }.distinct().sortedBy { it.nummer }

    fun antallTreff(): Int

    data class IkkeTreffPåEps(val identer: List<String>) : PersonhendelseresultatEps {
        override fun ikkeTreff(): Boolean = true
        override fun antallTreff() = 0
    }

    data class TreffPåEnEllerFlereEps(val treff: List<TreffPåEps>) : PersonhendelseresultatEps {
        override fun ikkeTreff(): Boolean = treff.all { it is TreffPåEps.IkkeAktivtVedtak }
        override fun antallTreff() = this.treff.sumOf { it.antallEps() }
    }

    sealed interface TreffPåEps {
        val brukersSaksnummer: Saksnummer
        val brukersFnr: Fnr
        val identer: List<String>

        fun antallEps(): Int

        data class AktivtVedtak(
            override val brukersSaksnummer: Saksnummer,
            override val brukersFnr: Fnr,
            override val identer: List<String>,
        ) : TreffPåEps {
            override fun antallEps() = 1
        }

        data class IkkeAktivtVedtak(
            override val brukersSaksnummer: Saksnummer,
            override val brukersFnr: Fnr,
            override val identer: List<String>,
        ) : TreffPåEps {
            override fun antallEps() = 0
        }
    }
}
