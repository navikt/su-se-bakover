package no.nav.su.se.bakover.service.statistikk

import arrow.core.Either
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.Statistikkhendelse
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.mappers.BehandlingStatistikkMapper
import no.nav.su.se.bakover.service.statistikk.mappers.SakStatistikkMapper
import no.nav.su.se.bakover.service.statistikk.mappers.StønadsstatistikkMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock

internal class StatistikkServiceImpl(
    private val publisher: KafkaPublisher,
    private val personService: PersonService,
    private val sakRepo: SakRepo,
    private val vedtakRepo: VedtakRepo,
    private val clock: Clock,
    private val log: Logger = LoggerFactory.getLogger(StatistikkServiceImpl::class.java),
) : StatistikkService, EventObserver {
    private val schemaValidator = StatistikkSchemaValidator

    // TODO: Kalles bare fra handle, burde være private?
    override fun publiser(statistikk: Statistikk) {
        val json = objectMapper.writeValueAsString(statistikk)
        val isValid = when (statistikk) {
            is Statistikk.Sak -> schemaValidator.validerSak(json)
            is Statistikk.Behandling -> schemaValidator.validerBehandling(json)
            is Statistikk.Stønad -> schemaValidator.validerStønad(json)
        }
        if (isValid) {
            val topic = when (statistikk) {
                is Statistikk.Sak -> "supstonad.aapen-su-sak-statistikk-v1"
                is Statistikk.Behandling -> "supstonad.aapen-su-behandling-statistikk-v1"
                is Statistikk.Stønad -> "supstonad.aapen-su-stonad-statistikk-v1"
            }

            publisher.publiser(topic = topic, melding = json)
        } else {
            log.error("Statistikk-objekt validerer ikke mot json-schema!")
        }
    }

    override fun handle(event: Statistikkhendelse) {
        Either.catch {
            when (event) {
                is Statistikkhendelse.SakOpprettet -> {
                    val sak = event.sak
                    personService.hentAktørIdMedSystembruker(sak.fnr).fold(
                        { log.info("Finner ikke person sak med sakid: ${sak.id} i PDL.") },
                        { aktørId -> publiser(SakStatistikkMapper(clock).map(sak, aktørId)) },
                    )
                }

                is Statistikkhendelse.Søknad.Mottatt ->
                    publiser(
                        BehandlingStatistikkMapper(clock).map(
                            event.søknad,
                            event.saksnummer,
                            Statistikk.Behandling.SøknadStatus.SØKNAD_MOTTATT,
                        ),
                    )

                is Statistikkhendelse.Søknad.Lukket ->
                    publiser(
                        BehandlingStatistikkMapper(clock).map(
                            event.søknad,
                            event.saksnummer,
                            Statistikk.Behandling.SøknadStatus.SØKNAD_LUKKET,
                        ),
                    )

                is Statistikkhendelse.Søknadsbehandling -> publiser(BehandlingStatistikkMapper(clock).map(event.søknadsbehandling))
                is Statistikkhendelse.Revurdering -> publiser(BehandlingStatistikkMapper(clock).map(event.revurdering))
                is Statistikkhendelse.Vedtak -> {
                    sakRepo.hentSak(event.vedtak.behandling.sakId)!!.let { sak ->
                        personService.hentAktørIdMedSystembruker(sak.fnr).fold(
                            ifLeft = { log.error("Finner ikke aktørId for person med sakId: ${sak.id}") },
                            ifRight = { aktørId ->
                                val ytelseVirkningstidspunkt = vedtakRepo.hentForSakId(event.vedtak.behandling.sakId)
                                    .filterIsInstance<VedtakSomKanRevurderes.EndringIYtelse>()
                                    .minOf { it.periode.fraOgMed }

                                publiser(
                                    StønadsstatistikkMapper(clock).map(
                                        event.vedtak,
                                        aktørId,
                                        ytelseVirkningstidspunkt,
                                        sak,
                                    ),
                                )
                            },
                        )
                    }
                }

                is Statistikkhendelse.Revurdering.Gjenoppta -> publiser(
                    BehandlingStatistikkMapper(clock).map(
                        event.gjenoppta,
                    ),
                )

                is Statistikkhendelse.Revurdering.Stans -> publiser(BehandlingStatistikkMapper(clock).map(event.stans))
                is Statistikkhendelse.Klagestatistikk -> publiser(BehandlingStatistikkMapper(clock).map(event.klage))
            }
        }.mapLeft {
            log.error("Feil ved publisering av statistikk", it)
        }
    }
}
