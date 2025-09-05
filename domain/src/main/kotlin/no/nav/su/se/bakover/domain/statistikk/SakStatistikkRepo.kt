package no.nav.su.se.bakover.domain.statistikk

import statistikk.domain.SakStatistikk
import java.util.UUID

interface SakStatistikkRepo {
    fun lagreSakStatistikk(behandlingstatistikk: SakStatistikk)
    fun hentSakStatistikk(sakId: UUID): List<SakStatistikk>
}
