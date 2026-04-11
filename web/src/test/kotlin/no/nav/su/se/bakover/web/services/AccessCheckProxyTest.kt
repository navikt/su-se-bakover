package no.nav.su.se.bakover.web.services

import arrow.core.Either
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.test.argShouldBe
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import person.domain.KunneIkkeHentePerson
import person.domain.PersonRepo
import person.domain.PersonService
import person.domain.PersonerOgSakstype
import økonomi.domain.utbetaling.Utbetalinger
import java.util.UUID

internal class AccessCheckProxyTest {
    private val services = Services(
        avstemming = mock(),
        utbetaling = mock(),
        sak = mock(),
        søknad = mock(),
        brev = mock(),
        fritekstService = mock(),
        lukkSøknad = mock(),
        oppgave = mock(),
        person = mock(),
        søknadsbehandling = SøknadsbehandlingServices(mock(), mock()),
        ferdigstillVedtak = mock(),
        revurdering = mock(),
        stansYtelse = mock(),
        gjenopptaYtelse = mock(),
        vedtakService = mock(),
        nøkkeltallService = mock(),
        avslåSøknadManglendeDokumentasjonService = mock(),
        klageService = mock(),
        klageinstanshendelseService = mock(),
        journalpostAdresseService = mock(),
        sendPåminnelserOmNyStønadsperiodeService = mock(),
        skatteService = mock(),
        kontrollsamtaleSetup = mock(),
        resendStatistikkhendelserService = mock(),
        personhendelseService = mock(),
        stønadStatistikkJobService = mock(),
        pesysJobService = mock(),
        aapJobService = mock(),
        fradragsjobbenService = mock(),
        sakstatistikkBigQueryService = mock(),
        fritekstAvslagService = mock(),
        søknadStatistikkService = mock(),
        mottakerService = mock(),
        kontrollsamtaleDriftOversiktService = mock(),
        reguleringManuellService = mock(),
        reguleringAutomatiskService = mock(),
        reguleringStatusUteståendeService = mock(),
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
                        hentFnrOgSaktypeForSak(sakId)
                    } doReturn PersonerOgSakstype(Sakstype.UFØRE, listOf(fnr))
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
                                utbetalinger = Utbetalinger(),
                                type = Sakstype.UFØRE,
                                versjon = Hendelsesversjon(1),
                                uteståendeKravgrunnlag = null,
                            ),
                        )
                    },
                    person = object : PersonService {
                        override fun hentPerson(fnr: Fnr, sakstype: Sakstype) = Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSkjermingOgKontaktinfo(fnr: Fnr, sakstype: Sakstype) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSystembruker(fnr: Fnr, sakstype: Sakstype) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentAktørIdMedSystembruker(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, AktørId> =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun sjekkTilgangTilPerson(fnr: Fnr, sakstype: Sakstype) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentFnrForSak(sakId: UUID) = PersonerOgSakstype(Sakstype.UFØRE, emptyList())
                    },
                ),
            ).proxy()

            shouldThrow<Tilgangssjekkfeil>
            { proxied.sak.hentSak(fnr, Sakstype.UFØRE) }
        }

        @Test
        fun `Når man gjør oppslag på sakId`() {
            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on { hentFnrOgSaktypeForSak(any()) } doReturn PersonerOgSakstype(Sakstype.UFØRE, listOf(Fnr.generer()))
                },
                services = services.copy(
                    person = object : PersonService {
                        override fun hentPerson(fnr: Fnr, sakstype: Sakstype) = Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSkjermingOgKontaktinfo(fnr: Fnr, sakstype: Sakstype) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSystembruker(fnr: Fnr, sakstype: Sakstype) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentAktørIdMedSystembruker(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, AktørId> =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun sjekkTilgangTilPerson(fnr: Fnr, sakstype: Sakstype) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentFnrForSak(sakId: UUID) = PersonerOgSakstype(Sakstype.UFØRE, emptyList())
                    },
                ),
            ).proxy()

            shouldThrow<Tilgangssjekkfeil> { proxied.sak.hentSak(UUID.randomUUID()) }
        }

        @Test
        fun `Når man gjør oppslag på søknadId`() {
            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on { hentFnrForSøknad(any()) } doReturn PersonerOgSakstype(Sakstype.UFØRE, listOf(Fnr.generer()))
                },
                services = services.copy(
                    person = object : PersonService {
                        override fun hentPerson(fnr: Fnr, sakstype: Sakstype) = Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSkjermingOgKontaktinfo(fnr: Fnr, sakstype: Sakstype) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSystembruker(fnr: Fnr, sakstype: Sakstype) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentAktørIdMedSystembruker(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, AktørId> =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun sjekkTilgangTilPerson(fnr: Fnr, sakstype: Sakstype) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentFnrForSak(sakId: UUID) = PersonerOgSakstype(Sakstype.UFØRE, emptyList())
                    },
                ),
            ).proxy()

            shouldThrow<Tilgangssjekkfeil> { proxied.søknad.hentSøknad(UUID.randomUUID()) }
        }

        @Test
        fun `Når man gjør oppslag på behandlingId`() {
            val proxied = AccessCheckProxy(
                personRepo = mock {
                    on { hentFnrForBehandling(any()) } doReturn PersonerOgSakstype(Sakstype.UFØRE, listOf(Fnr.generer()))
                },
                services = services.copy(
                    person = object : PersonService {
                        override fun hentPerson(fnr: Fnr, sakstype: Sakstype) = Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSkjermingOgKontaktinfo(fnr: Fnr, sakstype: Sakstype) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
                        override fun hentPersonMedSystembruker(fnr: Fnr, sakstype: Sakstype) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentAktørIdMedSystembruker(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, AktørId> =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun sjekkTilgangTilPerson(fnr: Fnr, sakstype: Sakstype) =
                            Either.Left(KunneIkkeHentePerson.IkkeTilgangTilPerson)

                        override fun hentFnrForSak(sakId: UUID) = PersonerOgSakstype(Sakstype.UFØRE, emptyList())
                    },
                ),
            ).proxy()

            shouldThrow<Tilgangssjekkfeil> {
                proxied.søknadsbehandling.søknadsbehandlingService.hent(
                    SøknadsbehandlingService.HentRequest(SøknadsbehandlingId.generer()),
                )
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
                            saksnummer = Saksnummer(2021),
                            opprettet = fixedTidspunkt,
                            fnr = fnr,
                            utbetalinger = Utbetalinger(),
                            type = Sakstype.UFØRE,
                            versjon = Hendelsesversjon(1),
                            uteståendeKravgrunnlag = null,
                        ),
                    )
                },
            )
            private val proxied = AccessCheckProxy(
                personRepo = object : PersonRepo {
                    override fun hentFnrOgSaktypeForSak(sakId: UUID): PersonerOgSakstype {
                        return PersonerOgSakstype(Sakstype.UFØRE, listOf(Fnr.generer()))
                    }

                    override fun hentFnrForSøknad(søknadId: UUID): PersonerOgSakstype {
                        return PersonerOgSakstype(Sakstype.UFØRE, listOf(Fnr.generer()))
                    }

                    override fun hentFnrForBehandling(behandlingId: UUID): PersonerOgSakstype {
                        return PersonerOgSakstype(Sakstype.UFØRE, listOf(Fnr.generer()))
                    }

                    override fun hentFnrForUtbetaling(utbetalingId: UUID30): PersonerOgSakstype {
                        return PersonerOgSakstype(Sakstype.UFØRE, listOf(Fnr.generer()))
                    }

                    override fun hentFnrForRevurdering(revurderingId: UUID): PersonerOgSakstype {
                        return PersonerOgSakstype(Sakstype.UFØRE, listOf(Fnr.generer()))
                    }

                    override fun hentFnrForVedtak(vedtakId: UUID): PersonerOgSakstype {
                        return PersonerOgSakstype(Sakstype.UFØRE, listOf(Fnr.generer()))
                    }

                    override fun hentFnrForKlage(klageId: UUID): PersonerOgSakstype {
                        return PersonerOgSakstype(Sakstype.UFØRE, listOf(Fnr.generer()))
                    }
                },
                services = servicesReturningSak.copy(
                    person = mock {
                        on { sjekkTilgangTilPerson(any(), any()) } doReturn Unit.right()
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
                val id = SøknadsbehandlingId.generer()
                proxied.søknadsbehandling.søknadsbehandlingService.hent(SøknadsbehandlingService.HentRequest(id))
                verify(servicesReturningSak.søknadsbehandling.søknadsbehandlingService).hent(
                    SøknadsbehandlingService.HentRequest(id),
                )
            }
        }
    }
}
