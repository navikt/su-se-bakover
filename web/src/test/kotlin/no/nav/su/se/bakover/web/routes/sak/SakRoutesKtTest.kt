package no.nav.su.se.bakover.web.routes.sak

import arrow.core.right
import io.kotest.assertions.json.shouldMatchJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.finn.unleash.FakeUnleash
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.domain.person.Fnr
import no.nav.su.se.bakover.domain.sak.Sak
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.søknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.dbMetricsStub
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import java.util.UUID
import javax.sql.DataSource

internal class SakRoutesKtTest {
    private val sakFnr01 = "12345678911"
    private fun repos(datasource: DataSource) = DatabaseBuilder.build(
        embeddedDatasource = datasource,
        dbMetrics = dbMetricsStub,
        clock = fixedClock,
    )
    private fun services(reps: DatabaseRepos) = ServiceBuilder.build(
        databaseRepos = reps,
        clients = TestClientsBuilder(fixedClock, reps).build(applicationConfig),
        behandlingMetrics = mock(),
        søknadMetrics = mock(),
        clock = fixedClock,
        unleash = FakeUnleash().apply { enableAll() },
    )

    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()

    @Test
    fun `henter sak for sak id`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)

            withTestApplication(
                (
                    {
                        testSusebakover(databaseRepos = repos)
                    }
                    ),
            ) {
                SakFactory(clock = fixedClock).nySakMedNySøknad(Fnr(sakFnr01), søknadInnhold).also {
                    repos.sak.opprettSak(it)
                }
                val opprettetSakId: Sak = repos.sak.hentSak(Fnr(sakFnr01))!!

                defaultRequest(
                    Get,
                    "$sakPath/${opprettetSakId.id}",
                    listOf(Brukerrolle.Saksbehandler),
                ).apply {
                    response.status() shouldBe OK
                    response.content shouldContain """"fnr":"$sakFnr01""""
                }
            }
        }
    }

    @Test
    fun `henter sak for fødselsnummer`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)
            withTestApplication(
                (
                    {
                        testSusebakover(databaseRepos = repos)
                    }
                    ),
            ) {
                repos.sak.opprettSak(SakFactory(clock = fixedClock).nySakMedNySøknad(Fnr(sakFnr01), søknadInnhold))

                defaultRequest(HttpMethod.Post, "$sakPath/søk", listOf(Brukerrolle.Saksbehandler)) {
                    setBody("""{"fnr":"$sakFnr01"}""")
                }.apply {
                    response.status() shouldBe OK
                    response.content shouldContain """"fnr":"$sakFnr01""""
                }
            }
        }
    }

    @Nested
    inner class BegrensetSakinfo {
        @Test
        fun `gir korrekt data når person ikke har søknad`() {
            withMigratedDb { dataSource ->
                val repos = repos(dataSource)

                withTestApplication(
                    { testSusebakover(databaseRepos = repos) },
                ) {
                    defaultRequest(
                        Get,
                        "$sakPath/info/$sakFnr01",
                        listOf(Brukerrolle.Veileder),
                    ).apply {
                        response.status() shouldBe OK
                        response.content shouldMatchJson """
                            {
                                "harÅpenSøknad": false,
                                "iverksattInnvilgetStønadsperiode": null
                            }
                        """.trimIndent()
                    }
                }
            }
        }

        @Test
        fun `finner ut om bruker har åpen søknad`() {
            withMigratedDb { dataSource ->
                val repos = repos(dataSource)

                withTestApplication(
                    { testSusebakover(databaseRepos = repos) },
                ) {
                    SakFactory(clock = fixedClock).nySakMedNySøknad(Fnr(sakFnr01), søknadInnhold).also {
                        repos.sak.opprettSak(it)
                    }

                    defaultRequest(
                        Get,
                        "$sakPath/info/$sakFnr01",
                        listOf(Brukerrolle.Veileder),
                    ).apply {
                        response.status() shouldBe OK
                        response.content shouldMatchJson """
                            {
                                "harÅpenSøknad": true,
                                "iverksattInnvilgetStønadsperiode": null
                            }
                        """.trimIndent()
                    }
                }
            }
        }

        @Test
        fun `finner ut om bruker har iverksatt innvilget stønadsperiode`() {
            withMigratedDb { dataSource ->
                val stønadsperiode = stønadsperiode2021
                val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                    saksnummer = Saksnummer(133333333337),
                    stønadsperiode = stønadsperiode
                )

                val repos = repos(dataSource)
                val services = services(repos)

                val sakSpy = spy(services.sak)
                doReturn(sak.right()).`when`(sakSpy).hentSak(any<Fnr>())

                withTestApplication(
                    {
                        testSusebakover(
                            databaseRepos = repos,
                            services = services.copy(
                                sak = sakSpy
                            )
                        )
                    },
                ) {
                    defaultRequest(
                        Get,
                        "$sakPath/info/$sakFnr01",
                        listOf(Brukerrolle.Veileder),
                    ).apply {
                        response.status() shouldBe OK
                        response.content shouldMatchJson """
                            {
                                "harÅpenSøknad": false,
                                "iverksattInnvilgetStønadsperiode": {
                                    "fraOgMed": "${stønadsperiode.periode.fraOgMed}",
                                    "tilOgMed": "${stønadsperiode.periode.tilOgMed}"
                                }
                            }
                        """.trimIndent()
                    }
                }
            }
        }
    }

    @Test
    fun `error handling`() {
        withTestApplication(
            (
                {
                    testSusebakover()
                }
                ),
        ) {
            defaultRequest(
                HttpMethod.Post, "$sakPath/søk", listOf(Brukerrolle.Saksbehandler),
            ).apply {
                response.status() shouldBe BadRequest
            }

            defaultRequest(HttpMethod.Post, "$sakPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"${Fnr.generer()}"}""")
            }.apply {
                response.status() shouldBe Forbidden
            }

            defaultRequest(HttpMethod.Post, "$sakPath/søk", listOf(Brukerrolle.Saksbehandler)) {
                setBody("""{"saksnummer":"696969"}""")
            }.apply {
                response.status() shouldBe NotFound
            }

            defaultRequest(HttpMethod.Post, "$sakPath/søk", listOf(Brukerrolle.Saksbehandler)) {
                setBody("""{"saksnummer":"asdf"}""")
            }.apply {
                response.status() shouldBe BadRequest
            }

            defaultRequest(
                Get,
                "$sakPath/${UUID.randomUUID()}",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                response.status() shouldBe NotFound
            }

            defaultRequest(
                Get,
                "$sakPath/adad",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                response.status() shouldBe BadRequest
            }
        }
    }
}
