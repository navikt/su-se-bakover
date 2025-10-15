package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface SakStatistikkRepo {
    fun lagreSakStatistikk(behandlingstatistikk: SakStatistikk, sessionContext: SessionContext? = null)
    fun hentSakStatistikk(sakId: UUID): List<SakStatistikk>
}
