package no.nav.su.se.bakover.service

import arrow.core.Either
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.assertions.throwables.shouldThrow
import no.nav.su.se.bakover.client.StubClientsBuilder
import no.nav.su.se.bakover.client.person.PdlFeil
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.person.PersonRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AccessCheckProxyTest {
    private val services = Services(
        avstemming = mock(),
        utbetaling = mock(),
        oppdrag = mock(),
        behandling = mock(),
        sak = mock(),
        søknad = mock(),
        brev = mock()
    )

    @Nested
    inner class `Kaster feil når PDL sier at man ikke har tilgang` {
        @Test
        fun `Når man gjør oppslag på fnr`() {
            val proxied = AccessCheckProxy(
                personRepo = mock(),
                clients = StubClientsBuilder.build().copy(
                    personOppslag = object : PersonOppslag {
                        override fun person(fnr: Fnr) =
                            Either.Left(PdlFeil.IkkeTilgangTilPerson)

                        override fun aktørId(fnr: Fnr): Either<PdlFeil, AktørId> {
                            TODO("Not yet implemented")
                        }
                    }
                )
            ).proxy(services)

            shouldThrow<Tilgangssjekkfeil> { proxied.sak.hentSak(FnrGenerator.random()) }
        }

        @Test
        fun `Når man gjør oppslag på sakId`() {
            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on { hentFnrForSak(any()) } doReturn FnrGenerator.random()
                },
                clients = StubClientsBuilder.build().copy(
                    personOppslag = object : PersonOppslag {
                        override fun person(fnr: Fnr) =
                            Either.Left(PdlFeil.IkkeTilgangTilPerson)

                        override fun aktørId(fnr: Fnr): Either<PdlFeil, AktørId> {
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
                    on { hentFnrForSøknad(any()) } doReturn FnrGenerator.random()
                },
                clients = StubClientsBuilder.build().copy(
                    personOppslag = object : PersonOppslag {
                        override fun person(fnr: Fnr) =
                            Either.Left(PdlFeil.IkkeTilgangTilPerson)

                        override fun aktørId(fnr: Fnr): Either<PdlFeil, AktørId> {
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
                    on { hentFnrForBehandling(any()) } doReturn FnrGenerator.random()
                },
                clients = StubClientsBuilder.build().copy(
                    personOppslag = object : PersonOppslag {
                        override fun person(fnr: Fnr) =
                            Either.Left(PdlFeil.IkkeTilgangTilPerson)

                        override fun aktørId(fnr: Fnr): Either<PdlFeil, AktørId> {
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
                    on { hentFnrForUtbetaling(any()) } doReturn FnrGenerator.random()
                },
                clients = StubClientsBuilder.build().copy(
                    personOppslag = object : PersonOppslag {
                        override fun person(fnr: Fnr) =
                            Either.Left(PdlFeil.IkkeTilgangTilPerson)

                        override fun aktørId(fnr: Fnr): Either<PdlFeil, AktørId> {
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
        private val proxied = AccessCheckProxy(
            personRepo = object : PersonRepo {
                override fun hentFnrForSak(sakId: UUID): Fnr? {
                    return FnrGenerator.random()
                }

                override fun hentFnrForSøknad(søknadId: UUID): Fnr? {
                    return FnrGenerator.random()
                }

                override fun hentFnrForBehandling(behandlingId: UUID): Fnr? {
                    return FnrGenerator.random()
                }

                override fun hentFnrForUtbetaling(utbetalingId: UUID30): Fnr? {
                    return FnrGenerator.random()
                }
            },
            clients = StubClientsBuilder.build()
        ).proxy(services)

        @Test
        fun `Når man gjør oppslag på fnr`() {
            val fnr = FnrGenerator.random()
            proxied.sak.hentSak(fnr)
            verify(services.sak).hentSak(fnr = argShouldBe(fnr))
        }

        @Test
        fun `Når man gjør oppslag på sakId`() {
            val id = UUID.randomUUID()
            proxied.sak.hentSak(id)
            verify(services.sak).hentSak(sakId = id)
        }

        @Test
        fun `Når man gjør oppslag på søknadId`() {
            val id = UUID.randomUUID()
            proxied.søknad.hentSøknad(id)
            verify(services.søknad).hentSøknad(søknadId = id)
        }

        @Test
        fun `Når man gjør oppslag på behandlingId`() {
            val id = UUID.randomUUID()
            proxied.behandling.hentBehandling(id)
            verify(services.behandling).hentBehandling(behandlingId = id)
        }

        @Test
        fun `Når man gjør oppslag på utbetalingId`() {
            val id = UUID30.randomUUID()
            proxied.utbetaling.hentUtbetaling(id)
            verify(services.utbetaling).hentUtbetaling(utbetalingId = id)
        }
    }
}
