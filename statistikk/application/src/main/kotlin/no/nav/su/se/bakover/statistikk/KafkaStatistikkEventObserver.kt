package no.nav.su.se.bakover.statistikk

import arrow.core.Either
import arrow.core.right
import com.networknt.schema.ValidationMessage
import no.nav.su.se.bakover.common.domain.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.service.statistikk.StønadService
import no.nav.su.se.bakover.statistikk.behandling.toBehandlingsstatistikk
import no.nav.su.se.bakover.statistikk.behandling.toBehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.sak.toBehandlingsstatistikk
import no.nav.su.se.bakover.statistikk.stønad.toStønadstatistikkDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import person.domain.PersonService
import java.time.Clock

internal class KafkaStatistikkEventObserver(
    private val publisher: KafkaPublisher,
    private val personService: PersonService,
    private val clock: Clock,
    private val log: Logger = LoggerFactory.getLogger(KafkaStatistikkEventObserver::class.java),
    private val gitCommit: GitCommit?,
    private val stønadService: StønadService,
) : StatistikkEventObserver {

    override fun handle(event: StatistikkEvent) {
        Either.catch {
            when (event) {
                is StatistikkEvent.SakOpprettet -> {
                    if (ApplicationConfig.isProd()) {
                        val sak = event.sak
                        personService.hentAktørIdMedSystembruker(sak.fnr).fold(
                            { log.info("Finner ikke person sak med sakid: ${sak.id} i PDL.") },
                            { aktørId -> publiserEllerLoggFeil(event.toBehandlingsstatistikk(aktørId, gitCommit)) },
                        )
                    } else {
                        val sak = event.sak
                        personService.hentAktørIdMedSystembruker(sak.fnr).fold(
                            { log.info("Finner ikke person sak med sakid: ${sak.id} i PDL.") },
                            { aktørId -> publiserEllerLoggFeil(event.toBehandlingsstatistikk(aktørId, gitCommit)) },
                        )
                    }
                }
                is StatistikkEvent.Behandling -> {
                    if (ApplicationConfig.isProd()) {
                        publiserEllerLoggFeil(
                            event.toBehandlingsstatistikkDto(
                                gitCommit,
                                clock,
                            ),
                        )
                    } else {
                        publiserEllerLoggFeil(
                            event.toBehandlingsstatistikkDto(
                                gitCommit,
                                clock,
                            ),
                        )
                    }
                }
                is StatistikkEvent.Søknad -> {
                    if (ApplicationConfig.isProd()) {
                        publiserEllerLoggFeil(event.toBehandlingsstatistikk(gitCommit, clock))
                    } else {
                        publiserEllerLoggFeil(event.toBehandlingsstatistikk(gitCommit, clock))
                    }
                }

                is StatistikkEvent.Stønadsvedtak -> {
                    publiserEllerLoggFeil(
                        event.toStønadstatistikkDto(
                            hentSak = event.hentSak,
                            clock = clock,
                            gitCommit = gitCommit,
                            lagreStatstikkHendelse = { dto -> stønadService.lagreHendelse(dto) },
                        ).right(),
                    )
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
