package no.nav.su.se.bakover.service

import arrow.core.Either
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonRepo
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class AccessCheckProxyTest {
    private val services = Services(
        avstemming = mock(),
        utbetaling = mock(),
        sak = mock(),
        søknad = mock(),
        brev = mock(),
        lukkSøknad = mock(),
        oppgave = mock(),
        person = mock(),
        statistikk = mock(),
        toggles = mock(),
        søknadsbehandling = mock(),
        ferdigstillVedtak = mock(),
        revurdering = mock(),
        vedtakService = mock(),
        nøkkeltallService = mock(),
        avslåSøknadManglendeDokumentasjonService = mock(),
        kontrollsamtale = mock(),
        klageService = mock(),
        klageinstanshendelseService = mock(),
        reguleringService = mock(),
        tilbakekrevingService = mock(),
        sendPåminnelserOmNyStønadsperiodeService = mock(),
        skatteService = mock()
    )

    @Nested
    inner class `Kaster feil når PDL sier at man ikke har tilgang` {
        @Test
        fun `Når man gjør oppslag på fnr`() {
            val fnr = Fnr.generer()
            val sakId = UUID.randomUUID()

            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on {
                        hentFnrForSak(sakId)
                    } doReturn listOf(fnr)
                },
                services = services.copy(
                    sak = mock {
                        on {
                            hentSak(fnr, Sakstype.UFØRE)
                        } doReturn Either.Right(
                            Sak(
                                id = sakId,
                                saksnummer = Saksnummer(2021),
                                opprettet = fixedTidspunkt,
                                fnr = fnr,
                                utbetalinger = emptyList(),
                                type = Sakstype.UFØRE
                            ),
                        )
                    },
                    person = object : PersonService {
                        override fun hentPerson(fnr: Fnr) = Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSystembruker(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentAktørId(fnr: Fnr) = throw NotImplementedError()
                        override fun hentAktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun sjekkTilgangTilPerson(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                    },
                ),
            ).proxy()

            shouldThrow<Tilgangssjekkfeil> { proxied.sak.hentSak(fnr, Sakstype.UFØRE) }
        }

        @Test
        fun `Når man gjør oppslag på sakId`() {
            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on { hentFnrForSak(any()) } doReturn listOf(Fnr.generer())
                },
                services = services.copy(
                    person = object : PersonService {
                        override fun hentPerson(fnr: Fnr) = Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSystembruker(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentAktørId(fnr: Fnr) = throw NotImplementedError()
                        override fun hentAktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun sjekkTilgangTilPerson(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                    },
                ),
            ).proxy()

            shouldThrow<Tilgangssjekkfeil> { proxied.sak.hentSak(UUID.randomUUID()) }
        }

        @Test
        fun `Når man gjør oppslag på søknadId`() {
            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on { hentFnrForSøknad(any()) } doReturn listOf(Fnr.generer())
                },
                services = services.copy(
                    person = object : PersonService {
                        override fun hentPerson(fnr: Fnr) = Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSystembruker(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentAktørId(fnr: Fnr) = throw NotImplementedError()
                        override fun hentAktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun sjekkTilgangTilPerson(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                    },
                ),
            ).proxy()

            shouldThrow<Tilgangssjekkfeil> { proxied.søknad.hentSøknad(UUID.randomUUID()) }
        }

        @Test
        fun `Når man gjør oppslag på behandlingId`() {
            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on { hentFnrForBehandling(any()) } doReturn listOf(Fnr.generer())
                },
                services = services.copy(
                    person = object : PersonService {
                        override fun hentPerson(fnr: Fnr) = Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSystembruker(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentAktørId(fnr: Fnr) = throw NotImplementedError()
                        override fun hentAktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun sjekkTilgangTilPerson(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                    },
                ),
            ).proxy()

            shouldThrow<Tilgangssjekkfeil> {
                proxied.søknadsbehandling.hent(
                    SøknadsbehandlingService.HentRequest(UUID.randomUUID()),
                )
            }
        }

        @Test
        fun `Når man gjør oppslag på utbetalingId`() {
            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on { hentFnrForUtbetaling(any()) } doReturn listOf(Fnr.generer())
                },
                services = services.copy(
                    person = object : PersonService {
                        override fun hentPerson(fnr: Fnr) = Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSystembruker(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentAktørId(fnr: Fnr) = throw NotImplementedError()
                        override fun hentAktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun sjekkTilgangTilPerson(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                    },
                ),
            ).proxy()

            shouldThrow<Tilgangssjekkfeil> { proxied.utbetaling.hentUtbetaling(UUID30.randomUUID()) }
        }
    }

    @Nested
    inner class `Kaller videre til underliggende service` {
        private val fnr = Fnr.generer()

        private val servicesReturningSak = services.copy(
            sak = mock {
                on {
                    hentSak(fnr, Sakstype.UFØRE)
                } doReturn Either.Right(
                    Sak(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        saksnummer = Saksnummer(2021),
                        fnr = fnr,
                        utbetalinger = emptyList(),
                        type = Sakstype.UFØRE
                    ),
                )
            },
        )
        private val proxied = AccessCheckProxy(
            personRepo = object : PersonRepo {
                override fun hentFnrForSak(sakId: UUID): List<Fnr> {
                    return listOf(Fnr.generer())
                }

                override fun hentFnrForSøknad(søknadId: UUID): List<Fnr> {
                    return listOf(Fnr.generer())
                }

                override fun hentFnrForBehandling(behandlingId: UUID): List<Fnr> {
                    return listOf(Fnr.generer())
                }

                override fun hentFnrForUtbetaling(utbetalingId: UUID30): List<Fnr> {
                    return listOf(Fnr.generer())
                }

                override fun hentFnrForRevurdering(revurderingId: UUID): List<Fnr> {
                    return listOf(Fnr.generer())
                }

                override fun hentFnrForVedtak(vedtakId: UUID): List<Fnr> {
                    return listOf(Fnr.generer())
                }

                override fun hentFnrForKlage(klageId: UUID): List<Fnr> {
                    return listOf(Fnr.generer())
                }
            },
            services = servicesReturningSak.copy(
                person = mock {
                    on { sjekkTilgangTilPerson(any()) } doReturn Unit.right()
                },
            ),
        ).proxy()

        @Test
        fun `Når man gjør oppslag på fnr`() {
            proxied.sak.hentSak(fnr, Sakstype.UFØRE)
            verify(servicesReturningSak.sak).hentSak(fnr = argShouldBe(fnr), type = argShouldBe(Sakstype.UFØRE))
        }

        @Test
        fun `Når man gjør oppslag på sakId`() {
            val id = UUID.randomUUID()
            proxied.sak.hentSak(id)
            verify(servicesReturningSak.sak).hentSak(sakId = id)
        }

        @Test
        fun `Når man gjør oppslag på søknadId`() {
            val id = UUID.randomUUID()
            proxied.søknad.hentSøknad(id)
            verify(servicesReturningSak.søknad).hentSøknad(søknadId = id)
        }

        @Test
        fun `Når man gjør oppslag på behandlingId`() {
            val id = UUID.randomUUID()
            proxied.søknadsbehandling.hent(SøknadsbehandlingService.HentRequest(id))
            verify(servicesReturningSak.søknadsbehandling).hent(SøknadsbehandlingService.HentRequest(id))
        }

        @Test
        fun `Når man gjør oppslag på utbetalingId`() {
            val id = UUID30.randomUUID()
            proxied.utbetaling.hentUtbetaling(id)
            verify(servicesReturningSak.utbetaling).hentUtbetaling(utbetalingId = id)
        }
    }
}
