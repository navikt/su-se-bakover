package no.nav.su.se.bakover.statistikk

import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.GitCommit
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.HentSakRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import java.time.Clock

class StatistikkEventObserverBuilder(
    kafkaPublisher: KafkaPublisher,
    personService: PersonService,
    sakRepo: HentSakRepo,
    clock: Clock,
    gitCommit: GitCommit?,
) {
    val statistikkService: StatistikkEventObserver = KafkaStatistikkEventObserver(
        publisher = kafkaPublisher,
        personService = personService,
        sakRepo = sakRepo,
        clock = clock,
        gitCommit = gitCommit,
    )
}
