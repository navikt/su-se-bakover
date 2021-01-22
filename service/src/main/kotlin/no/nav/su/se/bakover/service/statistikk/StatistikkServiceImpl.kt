package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.behandling.Behandling
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
            is Event.Statistikk.BehandlingOpprettet -> {
                val behandling = event.behandling
                publiser(
                    Statistikk.Behandling(
                        funksjonellTid = behandling.opprettet,
                        tekniskTid = Tidspunkt.now(clock),
                        registrertDato = when (val forNav = behandling.søknad.søknadInnhold.forNav) {
                            is ForNav.DigitalSøknad -> behandling.opprettet.toLocalDate(zoneIdOslo)
                            is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
                        },
                        mottattDato = behandling.opprettet.toLocalDate(zoneIdOslo),
                        behandlingId = behandling.id,
                        sakId = behandling.sakId,
                        saksnummer = behandling.saksnummer.nummer,
                        behandlingStatus = behandling.status(),
                        versjon = clock.millis()
                    )
                )
            }
            is Event.Statistikk.BehandlingTilAttestering -> {
                val behandling = event.behandling
                publiser(
                    Statistikk.Behandling(
                        funksjonellTid = behandling.beregning()?.getPeriode()?.getFraOgMed()?.startOfDay(zoneIdOslo)
                            ?: behandling.opprettet,
                        tekniskTid = Tidspunkt.now(clock),
                        registrertDato = when (val forNav = behandling.søknad.søknadInnhold.forNav) {
                            is ForNav.DigitalSøknad -> behandling.opprettet.toLocalDate(zoneIdOslo)
                            is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
                        },
                        mottattDato = behandling.opprettet.toLocalDate(zoneIdOslo),
                        behandlingId = behandling.id,
                        sakId = behandling.sakId,
                        saksnummer = behandling.saksnummer.nummer,
                        behandlingStatus = behandling.status(),
                        versjon = clock.millis(),
                        saksbehandler = behandling.saksbehandler()?.navIdent,
                    )
                )
            }
            is Event.Statistikk.BehandlingAttesteringUnderkjent -> {
                val behandling = event.behandling
                publiser(
                    Statistikk.Behandling(
                        funksjonellTid = behandling.beregning()?.getPeriode()?.getFraOgMed()?.startOfDay(zoneIdOslo)
                            ?: behandling.opprettet,
                        tekniskTid = Tidspunkt.now(clock),
                        registrertDato = when (val forNav = behandling.søknad.søknadInnhold.forNav) {
                            is ForNav.DigitalSøknad -> behandling.opprettet.toLocalDate(zoneIdOslo)
                            is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
                        },
                        mottattDato = behandling.opprettet.toLocalDate(zoneIdOslo),
                        behandlingId = behandling.id,
                        sakId = behandling.sakId,
                        saksnummer = behandling.saksnummer.nummer,
                        behandlingStatus = behandling.status(),
                        behandlingStatusBeskrivelse = "Sendt tilbake til saksbehandler",
                        versjon = clock.millis(),
                        saksbehandler = behandling.saksbehandler()?.navIdent,
                        beslutter = behandling.attestering()?.attestant?.navIdent,
                    )
                )
            }
            is Event.Statistikk.BehandlingIverksatt -> {
                val behandling = event.behandling.behandling
                publiser(
                    Statistikk.Behandling(
                        funksjonellTid = behandling.beregning()?.getPeriode()?.getFraOgMed()?.startOfDay(zoneIdOslo)
                            ?: behandling.opprettet,
                        tekniskTid = Tidspunkt.now(clock),
                        registrertDato = when (val forNav = behandling.søknad.søknadInnhold.forNav) {
                            is ForNav.DigitalSøknad -> behandling.opprettet.toLocalDate(zoneIdOslo)
                            is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
                        },
                        mottattDato = behandling.opprettet.toLocalDate(zoneIdOslo),
                        behandlingId = behandling.id,
                        sakId = behandling.sakId,
                        saksnummer = behandling.saksnummer.nummer,
                        behandlingStatus = behandling.status(),
                        versjon = clock.millis(),
                        saksbehandler = behandling.saksbehandler()?.navIdent,
                        beslutter = behandling.attestering()?.attestant?.navIdent,
                        resultat = when (behandling.status()) {
                            Behandling.BehandlingsStatus.IVERKSATT_INNVILGET -> "Innvilget"
                            Behandling.BehandlingsStatus.IVERKSATT_AVSLAG -> "Avslått"
                            else -> null
                        },
                        resultatBegrunnelse = when (behandling.status()) {
                            Behandling.BehandlingsStatus.IVERKSATT_AVSLAG -> behandling.utledAvslagsgrunner()
                                .joinToString(",")
                            else -> null
                        },
                    )
                )
            }
        }
    }
}
