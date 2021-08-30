package no.nav.su.se.bakover.web.routes.sak

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.dbMetricsStub
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.fixedClock
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

internal class SakRoutesKtTest {

    private val sakFnr01 = "12345678911"
    private fun repos(datasource: DataSource) = DatabaseBuilder.build(
        embeddedDatasource = datasource,
        dbMetrics = dbMetricsStub,
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
                HttpMethod.Post, "$sakPath/søk", listOf(Brukerrolle.Veileder),
            ).apply {
                response.status() shouldBe BadRequest
            }

            defaultRequest(HttpMethod.Post, "$sakPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"${Fnr.generer()}"}""")
            }.apply {
                response.status() shouldBe NotFound
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
