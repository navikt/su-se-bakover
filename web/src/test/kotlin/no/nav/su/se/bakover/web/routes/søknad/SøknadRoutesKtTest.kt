package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder.build
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.søknad.AvslåManglendeDokumentasjonRequest
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonService
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknad.lukk.LukketJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdUføreJson.Companion.toSøknadsinnholdUføreJson
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID
import javax.sql.DataSource
import kotlin.random.Random

internal class SøknadRoutesKtTest {

    private fun søknadInnhold(fnr: Fnr): SøknadsinnholdUføre = build(
        personopplysninger = SøknadInnholdTestdataBuilder.personopplysninger(
            fnr = fnr.toString(),
        ),
    )

    private val sakId: UUID = UUID.randomUUID()
    private val saksnummer = Random.nextLong(2021, Long.MAX_VALUE)
    private val tidspunkt = Tidspunkt.EPOCH

    private val sak = Sak(
        id = sakId,
        saksnummer = Saksnummer(saksnummer),
        opprettet = tidspunkt,
        fnr = Fnr.generer(),
        utbetalinger = emptyList(),
        type = Sakstype.UFØRE,
        uteståendeAvkorting = Avkortingsvarsel.Ingen,
    )
    private val søknadId = UUID.randomUUID()

    private fun databaseRepos(dataSource: DataSource) = DatabaseBuilder.build(
        embeddedDatasource = dataSource,
        dbMetrics = dbMetricsStub,
        clock = fixedClock,
        satsFactory = satsFactoryTest,
    )

    private val trekkSøknadRequest = trekkSøknad(
        søknadId = søknadId,
        saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z990Lokal"),
        trukketDato = 1.januar(2020),
    )

    private val mockServices = TestServicesBuilder.services()

    @Test
    fun `skal opprette journalpost og oppgave ved opprettelse av søknad`() {
        val fnr = Fnr.generer()
        val søknadInnhold: SøknadsinnholdUføre = søknadInnhold(fnr)
        val soknadJson: String = objectMapper.writeValueAsString(søknadInnhold.toSøknadsinnholdUføreJson())

        val pdfGenerator: PdfGenerator = mock {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn "pdf innhold".toByteArray().right()
        }
        val dokArkiv: DokArkiv = mock {
            on { opprettJournalpost(any<Journalpost.Søknadspost>()) } doReturn JournalpostId("9").right()
        }

        val personOppslag: PersonOppslag = mock {
            on { person(any()) } doReturn PersonOppslagStub.person(fnr)
            on { aktørIdMedSystembruker(any()) } doReturn PersonOppslagStub.aktørId(fnr)
            on { sjekkTilgangTilPerson(any()) } doReturn PersonOppslagStub.sjekkTilgangTilPerson(fnr)
        }
        val oppgaveClient: OppgaveClient = mock {
            on { opprettOppgave(any<OppgaveConfig.Søknad>()) } doReturn OppgaveId("11").right()
        }
        withMigratedDb { dataSource ->
            val repos = DatabaseBuilder.build(
                embeddedDatasource = dataSource,
                dbMetrics = dbMetricsStub,
                clock = fixedClock,
                satsFactory = satsFactoryTest,
            )

            val clients = TestClientsBuilder(fixedClock, repos).build(applicationConfig()).copy(
                pdfGenerator = pdfGenerator,
                dokArkiv = dokArkiv,
                personOppslag = personOppslag,
                oppgaveClient = oppgaveClient,
            )

            val services = ServiceBuilder.build(
                databaseRepos = repos,
                clients = clients,
                behandlingMetrics = mock(),
                søknadMetrics = mock(),
                clock = fixedClock,
                unleash = mock(),
                satsFactory = satsFactoryTestPåDato(),
                applicationConfig = applicationConfig(),
            )

            testApplication {
                application {
                    testSusebakover(
                        databaseRepos = repos,
                        clients = clients,
                        services = services,
                    )
                }
                defaultRequest(
                    Post,
                    "/soknad/ufore",
                    listOf(Brukerrolle.Veileder),
                ) {
                    header(ContentType, Json.toString())
                    setBody(soknadJson)
                }.apply {
                    status shouldBe Created
                    verify(pdfGenerator).genererPdf(any<SøknadPdfInnhold>())
                    verify(dokArkiv).opprettJournalpost(any())
                    // Kalles én gang i AccessCheckProxy og én gang eksplisitt i søknadService
                    verify(personOppslag).person(argThat { it shouldBe fnr })
                    verify(personOppslag).aktørIdMedSystembruker(argThat { it shouldBe fnr })
                    verify(oppgaveClient).opprettOppgave(any())
                }
            }
        }
    }

    @Test
    fun `lager en søknad, så trekker søknaden`() {
        val lukkSøknadServiceMock = mock<LukkSøknadService> {
            on { lukkSøknad(any()) } doReturn sak
        }
        testApplication {
            application {
                testSusebakover(services = mockServices.copy(lukkSøknad = lukkSøknadServiceMock))
            }
            defaultRequest(
                method = Post,
                uri = "soknad/$søknadId/lukk",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                header(ContentType, Json.toString())
                setBody(
                    objectMapper.writeValueAsString(
                        LukketJson.TrukketJson(
                            datoSøkerTrakkSøknad = 1.januar(2020),
                            type = LukketJson.LukketType.TRUKKET,
                        ),
                    ),
                )
            }.apply {
                status shouldBe OK
                verify(lukkSøknadServiceMock).lukkSøknad(
                    argThat { it shouldBe trekkSøknadRequest },
                )
            }
        }
    }

    @Test
    fun `Krever fritekst`() {
        val lukkSøknadServiceMock = mock<LukkSøknadService> {
            on { lukkSøknad(any()) } doThrow IllegalArgumentException("")
        }
        testApplication {
            application { testSusebakover(services = mockServices.copy(lukkSøknad = lukkSøknadServiceMock)) }
            defaultRequest(
                method = Post,
                uri = "soknad/$søknadId/lukk",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                header(ContentType, Json.toString())
                setBody(
                    objectMapper.writeValueAsString(
                        LukketJson.AvvistJson(
                            type = LukketJson.LukketType.AVVIST,
                            brevConfig = LukketJson.AvvistJson.BrevConfigJson(
                                brevtype = LukketJson.BrevType.FRITEKST,
                                null,
                            ),
                        ),
                    ),
                )
            }.apply {
                status shouldBe BadRequest
                verifyNoMoreInteractions(lukkSøknadServiceMock)
            }
        }
    }

    @Test
    fun `en søknad med ugyldig json gir badrequest og gjør ingen kall`() {
        val lukkSøknadServiceMock = mock<LukkSøknadService> {
            on { lukkSøknad(any()) } doThrow IllegalArgumentException("")
        }
        testApplication {
            application { testSusebakover(services = mockServices.copy(lukkSøknad = lukkSøknadServiceMock)) }
            defaultRequest(
                method = Post,
                uri = "soknad/$søknadId/lukk",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                header(ContentType, Json.toString())
                setBody(
                    """
                        {
                        "type" : "ugyldigtype",
                        "datoSøkerTrakkSøknad" : "2020-01-01"
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe BadRequest
                verifyNoMoreInteractions(lukkSøknadServiceMock)
            }
        }
    }

    @Test
    fun `kan lage brevutkast av trukket søknad`() {
        val pdf = "".toByteArray()
        val lukkSøknadServiceMock = mock<LukkSøknadService> {
            on { lagBrevutkast(any()) } doReturn Pair(fnr, pdf)
        }
        testApplication {
            application {
                testSusebakover(services = mockServices.copy(lukkSøknad = lukkSøknadServiceMock))
            }
            defaultRequest(
                method = Post,
                uri = "soknad/$søknadId/lukk/brevutkast",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    objectMapper.writeValueAsString(
                        LukketJson.TrukketJson(
                            datoSøkerTrakkSøknad = 1.januar(2020),
                            type = LukketJson.LukketType.TRUKKET,
                        ),
                    ),
                )
            }.apply {
                status shouldBe OK
                verify(lukkSøknadServiceMock).lagBrevutkast(
                    argThat { it shouldBe trekkSøknadRequest },
                )
            }
        }
    }

    @Test
    fun `kan avslå søknad pga manglende dokumentasjon`() {
        val (sak, _) = søknadsbehandlingIverksattAvslagUtenBeregning()
        val service = mock<AvslåSøknadManglendeDokumentasjonService> {
            on { avslå(any()) } doReturn sak.right()
        }
        testApplication {
            application {
                testSusebakover(services = mockServices.copy(avslåSøknadManglendeDokumentasjonService = service))
            }
            defaultRequest(
                method = Post,
                uri = "soknad/$søknadId/avslag",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "fritekst" : "coco jambo"
                    }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe OK

                verify(service).avslå(
                    argThat {
                        it shouldBe AvslåManglendeDokumentasjonRequest(
                            søknadId = søknadId,
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z990Lokal"),
                            fritekstTilBrev = "coco jambo",
                        )
                    },
                )

                bodyAsText() shouldBe serialize(sak.toJson(fixedClock, satsFactoryTestPåDato()))
            }
        }
    }
}
