package no.nav.su.se.bakover.web.routes.historisk

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBehandlingstype
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskMånedsbeløpForVedtak
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskMånedsbeløpsperiode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskResultat
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtaksperiode
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FnrWrapper
import no.nav.su.se.bakover.service.historisk.SupstonadHistoriskService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import person.domain.KunneIkkeHentePerson
import person.domain.PersonService
import java.math.BigDecimal
import java.time.LocalDate

internal class HistoriskAlderRoutesTest {
    private val fnr = Fnr("12345678910")
    private val request = FnrWrapper(fnr = fnr)

    @Test
    fun `saksbehandler og attestant kan sjekke om historisk alderssak finnes`() {
        val forventetRespons = HarHistoriskAlderssakResponse(harHistoriskAlderssak = true)
        listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant).forEach { rolle ->
            val personService = personServiceMedTilgang()
            val supstonadHistoriskService = mock<SupstonadHistoriskService> {
                on { harHistoriskAlderssak(fnr.value) } doReturn forventetRespons.harHistoriskAlderssak
            }

            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            person = personService,
                            supstonadHistoriskService = supstonadHistoriskService,
                        ),
                    )
                }

                defaultRequest(
                    method = HttpMethod.Post,
                    uri = "$HISTORISK_ALDERSSAK_PATH/finnes",
                    roller = listOf(rolle),
                ) { setBody(serialize(request)) }.apply {
                    status shouldBe HttpStatusCode.OK
                    deserialize<HarHistoriskAlderssakResponse>(bodyAsText()) shouldBe forventetRespons
                }
            }

            verify(personService).sjekkTilgangTilPerson(fnr, Sakstype.ALDER)
            verify(supstonadHistoriskService).harHistoriskAlderssak(fnr.value)
        }
    }

    @Test
    fun `andre roller kan ikke gjøre historisk aldersoppslag`() {
        Brukerrolle.entries
            .filterNot { it == Brukerrolle.Saksbehandler || it == Brukerrolle.Attestant }
            .forEach { rolle ->
                testApplication {
                    application {
                        testSusebakoverWithMockedDb(
                            services = TestServicesBuilder.services(
                                person = personServiceMedTilgang(),
                                supstonadHistoriskService = mock(),
                            ),
                        )
                    }

                    defaultRequest(
                        method = HttpMethod.Post,
                        uri = "$HISTORISK_ALDERSSAK_PATH/finnes",
                        roller = listOf(rolle),
                    ) { setBody(serialize(request)) }.apply {
                        status shouldBe HttpStatusCode.Forbidden
                    }
                }
            }
    }

    @Test
    fun `vedtaksperioder returneres med domenetypen`() {
        val personService = personServiceMedTilgang()
        val vedtaksperiode = HistoriskVedtaksperiode(
            stønadId = HistoriskStønadId("stonad-1"),
            vedtakId = HistoriskVedtakId("vedtak-1"),
            fraOgMed = LocalDate.of(2020, 1, 1),
            tilOgMed = LocalDate.of(2020, 12, 31),
            behandlingstypeRaw = "S",
            behandlingstype = HistoriskBehandlingstype.SØKNAD,
            resultatRaw = "I",
            resultat = HistoriskResultat.INNVILGET,
            bosituasjonRaw = "EN",
            bosituasjon = HistoriskBosituasjon.ENSLIG,
            årligYtelsesbeløp = BigDecimal("120000"),
            registrertTidspunkt = "2020-01-02T10:15:30",
            gyldig = true,
        )
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on { hentHistoriskeAldersvedtaksperioder(fnr.value) } doReturn listOf(vedtaksperiode)
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        person = personService,
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "$HISTORISK_ALDERSSAK_PATH/vedtaksperioder",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) { setBody(serialize(request)) }.apply {
                status shouldBe HttpStatusCode.OK
                deserializeList<HistoriskVedtaksperiode>(bodyAsText()) shouldBe listOf(vedtaksperiode)
            }
        }

        verify(personService).sjekkTilgangTilPerson(fnr, Sakstype.ALDER)
        verify(supstonadHistoriskService).hentHistoriskeAldersvedtaksperioder(fnr.value)
    }

    @Test
    fun `månedsbeløp hentes på vedtakId uten å eksponere interne id-er`() {
        val personService = personServiceMedTilgang()
        val vedtakId = HistoriskVedtakId("vedtak-1")
        val månedsbeløp = HistoriskMånedsbeløpsperiode(
            linjeId = "linje-1",
            fraOgMed = LocalDate.of(2020, 1, 1),
            tilOgMed = LocalDate.of(2020, 3, 31),
            sats = BigDecimal("15010"),
            fradrag = BigDecimal("1000"),
        )
        val månedsbeløpRequest = HentHistoriskeAldersmånedsbeløpRequest(vedtakId.value)
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on { hentHistoriskeAldersmånedsbeløp(vedtakId) } doReturn
                HistoriskMånedsbeløpForVedtak(
                    vedtakId = vedtakId,
                    personident = fnr,
                    månedsbeløp = listOf(månedsbeløp),
                )
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        person = personService,
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "$HISTORISK_ALDERSSAK_PATH/manedsbelop",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) { setBody(serialize(månedsbeløpRequest)) }.apply {
                status shouldBe HttpStatusCode.OK
                deserializeList<HistoriskMånedsbeløpsperiode>(bodyAsText()) shouldBe listOf(månedsbeløp)
            }
        }

        verify(personService).sjekkTilgangTilPerson(fnr, Sakstype.ALDER)
        verify(supstonadHistoriskService).hentHistoriskeAldersmånedsbeløp(vedtakId)
    }

    @Test
    fun `ukjent historisk vedtak gir not found`() {
        val vedtakId = HistoriskVedtakId("finnes-ikke")
        val månedsbeløpRequest = HentHistoriskeAldersmånedsbeløpRequest(vedtakId.value)
        val personService = personServiceMedTilgang()
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on { hentHistoriskeAldersmånedsbeløp(vedtakId) } doReturn null
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        person = personService,
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "$HISTORISK_ALDERSSAK_PATH/manedsbelop",
                roller = listOf(Brukerrolle.Attestant),
            ) { setBody(serialize(månedsbeløpRequest)) }.apply {
                status shouldBe HttpStatusCode.NotFound
            }
        }

        verify(personService, never()).sjekkTilgangTilPerson(any(), any())
    }

    @Test
    fun `månedsbeløp returneres ikke uten tilgang til personen`() {
        val vedtakId = HistoriskVedtakId("vedtak-1")
        val månedsbeløpRequest = HentHistoriskeAldersmånedsbeløpRequest(vedtakId.value)
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(fnr, Sakstype.ALDER) } doReturn
                KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
        }
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on { hentHistoriskeAldersmånedsbeløp(vedtakId) } doReturn
                HistoriskMånedsbeløpForVedtak(
                    vedtakId = vedtakId,
                    personident = fnr,
                    månedsbeløp = emptyList(),
                )
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        person = personService,
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "$HISTORISK_ALDERSSAK_PATH/manedsbelop",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) { setBody(serialize(månedsbeløpRequest)) }.apply {
                status shouldBe HttpStatusCode.Forbidden
            }
        }

        verify(personService).sjekkTilgangTilPerson(fnr, Sakstype.ALDER)
        verify(supstonadHistoriskService).hentHistoriskeAldersmånedsbeløp(vedtakId)
    }

    @Test
    fun `person uten tilgang får ikke slå opp historiske aldersdata`() {
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any(), any()) } doReturn KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
        }
        val supstonadHistoriskService = mock<SupstonadHistoriskService>()

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        person = personService,
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "$HISTORISK_ALDERSSAK_PATH/vedtaksperioder",
                roller = listOf(Brukerrolle.Attestant),
            ) { setBody(serialize(request)) }.apply {
                status shouldBe HttpStatusCode.Forbidden
            }
        }

        verify(supstonadHistoriskService, never()).hentHistoriskeAldersvedtaksperioder(any())
    }

    @Test
    fun `ugyldig fødselsnummer avvises`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        person = personServiceMedTilgang(),
                        supstonadHistoriskService = mock(),
                    ),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "$HISTORISK_ALDERSSAK_PATH/finnes",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) { setBody(serialize(UvalidertFnrRequest(fnr = "ugyldig"))) }.apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    private fun personServiceMedTilgang(): PersonService = mock {
        on { sjekkTilgangTilPerson(any(), any()) } doReturn Unit.right()
    }

    private data class UvalidertFnrRequest(
        val fnr: String,
    )
}
