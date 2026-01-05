package no.nav.su.se.bakover.domain.statistikk

import java.time.YearMonth

interface FritekstAvslagRepo {
    fun hentAntallAvslagsvedtakUtenFritekst(): List<AvslagsvedtakUtenFritekst>
}

data class AvslagsvedtakUtenFritekst(
    val antall: Int,
    val yearMonth: YearMonth,
)
