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
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerGjenopptakFeil
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalGjenopptakFeil
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeGjenopptaYtelse
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeIverksetteGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

internal class GjenopptaUtbetalingRouteKtTest {

    @Test
    fun `svarer med 201 ved påbegynt gjenopptak av utbetaling`() {
        val enRevurdering = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse().second
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(
                        revurdering = mock {
                            on { gjenopptaYtelse(any()) } doReturn enRevurdering.right()
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
        val enRevurdering = beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(
                        revurdering = mock {
                            on {
                                iverksettGjenopptakAvYtelse(
                                    any(),
                                    any(),
                                )
                            } doReturn KunneIkkeIverksetteGjenopptakAvYtelse.UgyldigTilstand(
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
        val enRevurdering = beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(
                        revurdering = mock {
                            on {
                                iverksettGjenopptakAvYtelse(
                                    any(),
                                    any(),
                                )
                            } doReturn KunneIkkeIverksetteGjenopptakAvYtelse.KunneIkkeUtbetale(
                                UtbetalGjenopptakFeil.KunneIkkeUtbetale(
                                    UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte(
                                        KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UliktBeløp,
                                    ),
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
        val eksisterende = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
            clock = TikkendeKlokke(1.juli(2021).fixedClock()),
        )
        val simulertRevurdering = eksisterende.second
        val sisteVedtak = eksisterende.first.vedtakListe.last() as VedtakSomKanRevurderes

        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(
                        revurdering = mock {
                            doAnswer {
                                val args = (it.arguments[0] as GjenopptaYtelseRequest.Oppdater)
                                simulertRevurdering.copy(
                                    periode = Periode.create(
                                        sisteVedtak.periode.fraOgMed,
                                        simulertRevurdering.periode.tilOgMed,
                                    ),
                                    revurderingsårsak = args.revurderingsårsak,
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
        val enRevurdering = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse()
            .second
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(
                        revurdering = mock {
                            on { gjenopptaYtelse(any()) } doReturn enRevurdering.right()
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
                testSusebakover(
                    services = TestServicesBuilder.services(
                        revurdering = mock {
                            on { gjenopptaYtelse(any()) } doReturn KunneIkkeGjenopptaYtelse.KunneIkkeSimulere(
                                SimulerGjenopptakFeil.KunneIkkeGenerereUtbetaling(
                                    Utbetalingsstrategi.Gjenoppta.Feil.KanIkkeGjenopptaOpphørtePeriode,
                                ),
                            ).left()
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
                bodyAsText() shouldContain """"code":"kan_ikke_gjenoppta_opphørte_utbetalinger""""
            }
        }
    }
}
