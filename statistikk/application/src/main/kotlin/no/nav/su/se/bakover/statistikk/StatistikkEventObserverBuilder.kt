package no.nav.su.se.bakover.statistikk

import no.nav.su.se.bakover.common.domain.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import java.time.Clock

class StatistikkEventObserverBuilder(
    kafkaPublisher: KafkaPublisher,
    clock: Clock,
    gitCommit: GitCommit?,
    sakStatistikkRepo: SakStatistikkRepo,
) {
    val statistikkService: StatistikkEventObserver = StatistikkEventObserver(
        publisher = kafkaPublisher,
        clock = clock,
        gitCommit = gitCommit,
        sakStatistikkRepo = sakStatistikkRepo,
    )
}
