package no.nav.su.se.bakover.statistikk

import no.nav.su.se.bakover.common.domain.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.service.statistikk.StatistikkService
import person.domain.PersonService
import java.time.Clock

class StatistikkEventObserverBuilder(
    kafkaPublisher: KafkaPublisher,
    personService: PersonService,
    clock: Clock,
    gitCommit: GitCommit?,
    statistikkService: StatistikkService,
) {
    val statistikkService: StatistikkEventObserver = KafkaStatistikkEventObserver(
        publisher = kafkaPublisher,
        personService = personService,
        clock = clock,
        gitCommit = gitCommit,
        statistikkService = statistikkService,
    )
}
