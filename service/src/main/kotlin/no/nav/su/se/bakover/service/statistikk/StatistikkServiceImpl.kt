package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.service.person.PersonService
import org.slf4j.LoggerFactory
import java.time.Clock

internal class StatistikkServiceImpl(
    private val publisher: KafkaPublisher,
    private val personService: PersonService,
    private val clock: Clock,
) : StatistikkService, EventObserver {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val schemaValidator = StatistikkSchemaValidator

    override fun publiser(statistikk: Statistikk) {
        val json = objectMapper.writeValueAsString(statistikk)
        val isValid = when (statistikk) {
            is Statistikk.Sak -> schemaValidator.validerSak(json)
            is Statistikk.Behandling -> schemaValidator.validerBehandling(json)
        }
        if (isValid) {
            when (statistikk) {
                is Statistikk.Sak -> publisher.publiser(
                    topic = "supstonad.aapen-su-sak-statistikk-v1",
                    melding = json
                )
                is Statistikk.Behandling -> publisher.publiser(
                    topic = "supstonad.aapen-su-behandling-statistikk-v1",
                    melding = json
                )
            }
        } else {
            log.error("Statistikk-objekt validerer ikke mot json-schema!")
        }
    }

    override fun handle(event: Event) {
        when (event) {
            is Event.Statistikk.SakOpprettet -> {
                val sak = event.sak
                personService.hentAktørId(sak.fnr).fold(
                    { log.info("Finner ikke person sak med sakid: ${sak.id} i PDL.") },
                    { aktørId ->
                        publiser(
                            Statistikk.Sak(
                                funksjonellTid = sak.opprettet,
                                tekniskTid = sak.opprettet,
                                opprettetDato = sak.opprettet.toLocalDate(zoneIdOslo),
                                sakId = sak.id,
                                aktorId = aktørId.toString().toLong(),
                                saksnummer = sak.saksnummer.nummer,
                                sakStatus = "OPPRETTET",
                                sakStatusBeskrivelse = "Sak er opprettet men ingen vedtak er fattet.",
                                versjon = clock.millis()
                            )
                        )
                    }
                )
            }
            is Event.Statistikk.SøknadStatistikk.SøknadMottatt ->
                publiser(SøknadStatistikkMapper(clock).map(event.søknad, event.saksnummer, Statistikk.Behandling.SøknadStatus.SØKNAD_MOTTATT))
            is Event.Statistikk.SøknadStatistikk.SøknadLukket ->
                publiser(SøknadStatistikkMapper(clock).map(event.søknad, event.saksnummer, Statistikk.Behandling.SøknadStatus.SØKNAD_LUKKET))
            is Event.Statistikk.SøknadsbehandlingStatistikk ->
                publiser(SøknadsbehandlingStatistikkMapper(clock).map(event.søknadsbehandling))
            is Event.Statistikk.RevurderingStatistikk -> {
                publiser(RevurderingStatistikkMapper(clock).map(event.revurdering))
            }
        }
    }
}
