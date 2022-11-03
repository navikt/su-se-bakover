package no.nav.su.se.bakover.statistikk

import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.GitCommit
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import java.time.Clock

class StatistikkEventObserverBuilder(
    kafkaPublisher: KafkaPublisher,
    personService: PersonService,
    clock: Clock,
    gitCommit: GitCommit?,
) {
    val statistikkService: StatistikkEventObserver = KafkaStatistikkEventObserver(
        publisher = kafkaPublisher,
        personService = personService,
        clock = clock,
        gitCommit = gitCommit,
    )
}
