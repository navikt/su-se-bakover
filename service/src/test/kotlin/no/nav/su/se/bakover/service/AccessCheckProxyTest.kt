package no.nav.su.se.bakover.service

import arrow.core.Either
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.assertions.throwables.shouldThrow
import no.nav.su.se.bakover.client.StubClientsBuilder
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.person.PersonRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AccessCheckProxyTest {
    private val services = Services(
        avstemming = mock(),
        utbetaling = mock(),
        behandling = mock(),
        sak = mock(),
        søknad = mock(),
        brev = mock(),
        lukkSøknad = mock(),
        oppgave = mock()
    )

    @Nested
    inner class `Kaster feil når PDL sier at man ikke har tilgang` {
        @Test
        fun `Når man gjør oppslag på fnr`() {
            val fnr = FnrGenerator.random()
            val sakId = UUID.randomUUID()

            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on {
                        hentFnrForSak(sakId)
                    } doReturn listOf(fnr)
                },
                clients = StubClientsBuilder.build().copy(
                    personOppslag = object : PersonOppslag {
                        override fun person(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun aktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
                            TODO("Not yet implemented")
                        }
                    }
                )
            ).proxy(
                services.copy(
                    sak = mock {
                        on {
                            hentSak(fnr)
                        } doReturn Either.right(
                            Sak(
                                id = sakId,
                                saksnummer = Saksnummer(1234),
                                fnr = fnr,
                                utbetalinger = emptyList()
                            )
                        )
                    }
                )
            )

            shouldThrow<Tilgangssjekkfeil> { proxied.sak.hentSak(fnr) }
        }

        @Test
        fun `Når man gjør oppslag på sakId`() {
            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on { hentFnrForSak(any()) } doReturn listOf(FnrGenerator.random())
                },
                clients = StubClientsBuilder.build().copy(
                    personOppslag = object : PersonOppslag {
                        override fun person(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun aktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
                            TODO("Not yet implemented")
                        }
                    }
                )
            ).proxy(services)

            shouldThrow<Tilgangssjekkfeil> { proxied.sak.hentSak(UUID.randomUUID()) }
        }

        @Test
        fun `Når man gjør oppslag på søknadId`() {
            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on { hentFnrForSøknad(any()) } doReturn listOf(FnrGenerator.random())
                },
                clients = StubClientsBuilder.build().copy(
                    personOppslag = object : PersonOppslag {
                        override fun person(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun aktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
                            TODO("Not yet implemented")
                        }
                    }
                )
            ).proxy(services)

            shouldThrow<Tilgangssjekkfeil> { proxied.søknad.hentSøknad(UUID.randomUUID()) }
        }

        @Test
        fun `Når man gjør oppslag på behandlingId`() {
            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on { hentFnrForBehandling(any()) } doReturn listOf(FnrGenerator.random())
                },
                clients = StubClientsBuilder.build().copy(
                    personOppslag = object : PersonOppslag {
                        override fun person(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun aktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
                            TODO("Not yet implemented")
                        }
                    }
                )
            ).proxy(services)

            shouldThrow<Tilgangssjekkfeil> { proxied.behandling.hentBehandling(UUID.randomUUID()) }
        }

        @Test
        fun `Når man gjør oppslag på utbetalingId`() {
            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on { hentFnrForUtbetaling(any()) } doReturn listOf(FnrGenerator.random())
                },
                clients = StubClientsBuilder.build().copy(
                    personOppslag = object : PersonOppslag {
                        override fun person(fnr: Fnr) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun aktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
                            TODO("Not yet implemented")
                        }
                    }
                )
            ).proxy(services)

            shouldThrow<Tilgangssjekkfeil> { proxied.utbetaling.hentUtbetaling(UUID30.randomUUID()) }
        }
    }

    @Nested
    inner class `Kaller videre til underliggende service` {
        private val fnr = FnrGenerator.random()

        private val servicesReturningSak = services.copy(
            sak = mock {
                on {
                    hentSak(fnr)
                } doReturn Either.right(
                    Sak(
                        id = UUID.randomUUID(),
                        saksnummer = Saksnummer(1234),
                        fnr = fnr,
                        utbetalinger = emptyList()
                    )
                )
            }
        )
        private val proxied = AccessCheckProxy(
            personRepo = object : PersonRepo {
                override fun hentFnrForSak(sakId: UUID): List<Fnr> {
                    return listOf(FnrGenerator.random())
                }

                override fun hentFnrForSøknad(søknadId: UUID): List<Fnr> {
                    return listOf(FnrGenerator.random())
                }

                override fun hentFnrForBehandling(behandlingId: UUID): List<Fnr> {
                    return listOf(FnrGenerator.random())
                }

                override fun hentFnrForUtbetaling(utbetalingId: UUID30): List<Fnr> {
                    return listOf(FnrGenerator.random())
                }
            },
            clients = StubClientsBuilder.build()
        ).proxy(servicesReturningSak)

        @Test
        fun `Når man gjør oppslag på fnr`() {
            proxied.sak.hentSak(fnr)
            verify(servicesReturningSak.sak).hentSak(fnr = argShouldBe(fnr))
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
            proxied.behandling.hentBehandling(id)
            verify(servicesReturningSak.behandling).hentBehandling(behandlingId = id)
        }

        @Test
        fun `Når man gjør oppslag på utbetalingId`() {
            val id = UUID30.randomUUID()
            proxied.utbetaling.hentUtbetaling(id)
            verify(servicesReturningSak.utbetaling).hentUtbetaling(utbetalingId = id)
        }
    }
}
