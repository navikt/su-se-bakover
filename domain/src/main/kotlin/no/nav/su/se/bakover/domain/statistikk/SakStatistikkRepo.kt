package no.nav.su.se.bakover.domain.statistikk

import statistikk.domain.SakStatistikk

interface SakStatistikkRepo {
    fun lagreSakStatistikk(behandlingstatistikk: SakStatistikk)
}
