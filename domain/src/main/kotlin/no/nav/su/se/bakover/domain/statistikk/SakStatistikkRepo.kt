package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikkTilBiquery
import no.nav.su.se.bakover.common.persistence.SessionContext
import java.time.LocalDate
import java.util.UUID

interface SakStatistikkRepo {
    fun lagreSakStatistikk(sakStatistikk: SakStatistikk, sessionContext: SessionContext? = null)
    fun hentSakStatistikk(sakId: UUID): List<SakStatistikk>
    fun hentSakStatistikk(fom: LocalDate, tom: LocalDate): List<SakStatistikkTilBiquery>
}
