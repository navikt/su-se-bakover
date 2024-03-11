package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.extensions.fixedClock
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KontrollsimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeSimulereGjenopptakAvYtelse
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.simulertGjenopptakAvYtelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import vedtak.domain.VedtakSomKanRevurderes
import java.util.UUID

internal class GjenopptaUtbetalingRouteKtTest {

    @Test
    fun `svarer med 201 ved påbegynt gjenopptak av utbetaling`() {
        val enRevurdering = simulertGjenopptakAvYtelseFraVedtakStansAvYtelse().second
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        gjenopptakAvYtelseService = mock {
                            on { gjenopptaYtelse(any()) } doReturn Pair(enRevurdering, null).right()
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "saker/${enRevurdering.sakId}/revurderinger/gjenoppta",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fraOgMed": "2021-05-01",
                          "årsak": "MOTTATT_KONTROLLERKLÆRING",
                          "begrunnelse": "huffda"
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.Created
            }
        }
    }

    @Test
    fun `svarer med 400 ved forsøk å iverksetting av ugyldig revurdering`() {
        val enRevurdering = beregnetRevurdering(
            clock = tikkendeFixedClock(),
        ).second
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        gjenopptakAvYtelseService = mock {
                            on {
                                iverksettGjenopptakAvYtelse(
                                    any(),
                                    any(),
                                )
                            } doReturn KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.UgyldigTilstand(
                                enRevurdering::class,
                            ).left()
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "saker/${enRevurdering.sakId}/revurderinger/gjenoppta/${enRevurdering.id}/iverksett",
                listOf(Brukerrolle.Attestant),
            ).apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "kunne_ikke_iverksette_gjenopptak_ugyldig_tilstand"
            }
        }
    }

    @Test
    fun `svarer med 500 hvis utbetaling feiler`() {
        val enRevurdering = beregnetRevurdering(
            clock = tikkendeFixedClock(),
        ).second
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        gjenopptakAvYtelseService = mock {
                            on {
                                iverksettGjenopptakAvYtelse(
                                    any(),
                                    any(),
                                )
                            } doReturn KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KontrollsimuleringFeilet(
                                KontrollsimuleringFeilet.Forskjeller(
                                    KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UliktBeløp,
                                ),
                            ).left()
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "saker/${enRevurdering.sakId}/revurderinger/gjenoppta/${enRevurdering.id}/iverksett",
                listOf(Brukerrolle.Attestant),
            ).apply {
                status shouldBe HttpStatusCode.InternalServerError
                bodyAsText() shouldContain "kontrollsimulering_ulik_saksbehandlers_simulering"
            }
        }
    }

    @Test
    fun `svarer med 200 ved oppdatering av eksisterende revurdering`() {
        val eksisterende = simulertGjenopptakAvYtelseFraVedtakStansAvYtelse(
            clock = TikkendeKlokke(1.juli(2021).fixedClock()),
        )
        val simulertRevurdering = eksisterende.second
        val sisteVedtak = eksisterende.first.vedtakListe.last() as VedtakSomKanRevurderes

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        gjenopptakAvYtelseService = mock {
                            doAnswer {
                                val args = (it.arguments[0] as GjenopptaYtelseRequest.Oppdater)
                                (
                                    simulertRevurdering.copy(
                                        periode = Periode.create(
                                            sisteVedtak.periode.fraOgMed,
                                            simulertRevurdering.periode.tilOgMed,
                                        ),
                                        revurderingsårsak = args.revurderingsårsak,
                                    ) to null
                                    ).right()
                            }.whenever(mock).gjenopptaYtelse(any())
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Patch,
                "saker/${simulertRevurdering.sakId}/revurderinger/gjenoppta/${simulertRevurdering.id}",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fraOgMed": "2021-08-01",
                          "årsak": "MOTTATT_KONTROLLERKLÆRING",
                          "begrunnelse": "kebabeluba"
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldContain "2021-08-01"
                bodyAsText() shouldContain "kebabeluba"
            }
        }
    }

    @Test
    fun `svarer med 400 ved ugyldig input`() {
        val enRevurdering = simulertGjenopptakAvYtelseFraVedtakStansAvYtelse()
            .second
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        gjenopptakAvYtelseService = mock {
                            on { gjenopptaYtelse(any()) } doReturn Pair(enRevurdering, null).right()
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "saker/${enRevurdering.sakId}/revurderinger/gjenoppta",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fraOgMed": "2021-05-01",
                          "årsak": "KJEKS",
                          "begrunnelse": "huffda"
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain """"code":"revurderingsårsak_ugyldig_årsak""""
            }
        }
    }

    @Test
    fun `svarer med 500 ved forsøk på gjenopptak av opphørt periode`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        gjenopptakAvYtelseService = mock {
                            on { gjenopptaYtelse(any()) } doReturn KunneIkkeSimulereGjenopptakAvYtelse.SisteVedtakErIkkeStans.left()
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "saker/${UUID.randomUUID()}/revurderinger/gjenoppta",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fraOgMed": "2021-05-01",
                          "årsak": "MOTTATT_KONTROLLERKLÆRING",
                          "begrunnelse": "huffda"
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.InternalServerError
                bodyAsText() shouldContain """"code":"siste_vedtak_ikke_stans""""
            }
        }
    }
}
