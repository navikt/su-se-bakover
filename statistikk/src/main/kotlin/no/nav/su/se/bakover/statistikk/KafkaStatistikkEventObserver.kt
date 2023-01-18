package no.nav.su.se.bakover.statistikk

import arrow.core.Either
import com.networknt.schema.ValidationMessage
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.GitCommit
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.statistikk.behandling.toBehandlingsstatistikk
import no.nav.su.se.bakover.statistikk.behandling.toBehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.sak.toBehandlingsstatistikk
import no.nav.su.se.bakover.statistikk.stønad.toStønadstatistikkDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock

internal class KafkaStatistikkEventObserver(
    private val publisher: KafkaPublisher,
    private val personService: PersonService,
    private val clock: Clock,
    private val log: Logger = LoggerFactory.getLogger(KafkaStatistikkEventObserver::class.java),
    private val gitCommit: GitCommit?,
) : StatistikkEventObserver {

    override fun handle(event: StatistikkEvent) {
        Either.catch {
            when (event) {
                is StatistikkEvent.SakOpprettet -> {
                    val sak = event.sak
                    personService.hentAktørIdMedSystembruker(sak.fnr).fold(
                        { log.info("Finner ikke person sak med sakid: ${sak.id} i PDL.") },
                        { aktørId -> publiserEllerLoggFeil(event.toBehandlingsstatistikk(aktørId, gitCommit)) },
                    )
                }

                is StatistikkEvent.Stønadsvedtak -> {
                    val sakinfo = event.vedtak.sakinfo()
                    personService.hentAktørIdMedSystembruker(sakinfo.fnr).fold(
                        ifLeft = { log.error("Finner ikke aktørId for person med sakId: ${sakinfo.sakId}") },
                        ifRight = { aktørId ->
                            publiserEllerLoggFeil(
                                event.toStønadstatistikkDto(
                                    aktørId = aktørId,
                                    // TODO jah: Føles rart å sende med hele saken her dersom man kun ønsker å kalle `hentGjeldendeBeregningForEndringIYtelsePåDato(...)` lenger inn?
                                    hentSak = event.hentSak,
                                    clock = clock,
                                    gitCommit = gitCommit,
                                ),
                            )
                        },
                    )
                }

                is StatistikkEvent.Behandling -> publiserEllerLoggFeil(
                    event.toBehandlingsstatistikkDto(
                        gitCommit,
                        clock,
                    ),
                )

                is StatistikkEvent.Søknad -> publiserEllerLoggFeil(event.toBehandlingsstatistikk(gitCommit, clock))
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
