package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.domain.statistikk.StønadMånedStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkRepo
import org.mockito.kotlin.mock

class StatistikkServiceTest {

    private val statistikkHendelseRepo: StønadStatistikkRepo = mock()
    private val stønadstatistikkMånedRepo: StønadMånedStatistikkRepo = mock()

    private val statistikkService = StønadStatistikkService(statistikkHendelseRepo, stønadstatistikkMånedRepo)
}
