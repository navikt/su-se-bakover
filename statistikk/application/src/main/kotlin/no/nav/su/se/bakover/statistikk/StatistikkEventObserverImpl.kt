package no.nav.su.se.bakover.statistikk

import arrow.core.Either
import com.networknt.schema.ValidationMessage
import no.nav.su.se.bakover.common.domain.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.statistikk.behandling.toBehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.sak.toBehandlingsstatistikkOverordnet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock

class StatistikkEventObserverImpl(
    private val publisher: KafkaPublisher,
    private val clock: Clock,
    private val log: Logger = LoggerFactory.getLogger(StatistikkEventObserver::class.java),
    private val gitCommit: GitCommit?,
    private val sakStatistikkRepo: SakStatistikkRepo,
) : StatistikkEventObserver {

    override fun handle(event: StatistikkEvent, sessionContext: SessionContext?) {
        Either.catch {
            when (event) {
                is StatistikkEvent.Behandling -> {
                    publiserEllerLoggFeil(
                        event.toBehandlingsstatistikkDto(
                            gitCommit,
                            clock,
                        ),
                    )
                    event.toBehandlingsstatistikkOverordnet(clock).let {
                        sakStatistikkRepo.lagreSakStatistikk(it, sessionContext)
                    }
                }
            }
        }.mapLeft {
            log.error("Feil ved publisering av statistikk", it)
        }
    }

    private fun publiserEllerLoggFeil(melding: Either<Set<ValidationMessage>, ValidertStatistikkJsonMelding>) {
        melding.onRight {
            publisher.publiser(
                topic = it.topic,
                melding = it.validertJsonMelding,
            )
        }.onLeft {
            log.error("Skjemavalidering av statistikkmelding feilet: $it")
        }
    }
}
