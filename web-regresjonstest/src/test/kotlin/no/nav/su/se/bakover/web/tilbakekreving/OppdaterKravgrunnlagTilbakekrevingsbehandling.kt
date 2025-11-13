package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.assertions.withClue
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingStatus

object OppdaterKravgrunnlagTilbakekrevingsbehandling {
    internal fun oppdaterKravgrunnlag(
        sakId: String,
        tilbakekrevingsbehandlingId: String,
        expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
        client: HttpClient,
        verifiserRespons: Boolean = true,
        saksversjon: Long,
    ): OppdaterKravgrunnlagTilbakekrevingsbehandlingRespons {
        // Dette kallet fører ikke til sideeffekter
        val sakFørKallJson = hentSak(sakId, client)
        return runBlocking {
            no.nav.su.se.bakover.test.application.defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/oppdaterKravgrunnlag",
                listOf(Brukerrolle.Saksbehandler),
                client = client,
            ) {
                setBody(
                    """{"versjon": $saksversjon}""",
                )
            }.apply {
                withClue("Kunne ikke oppdatere kravgrunnlag på tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                    status shouldBe expectedHttpStatusCode
                }
            }.bodyAsText().let { responseJson ->
                val sakEtterKallJson = hentSak(sakId, client)
                val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")
                val tilbakekrevingRespons = deserialize<TilbakekrevingsbehandlingJson>(responseJson)
                if (verifiserRespons) {
                    sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
                    val tilbakekrevingerResponseFromSak =
                        JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").let {
                            it.length() shouldBe 1
                            it.getJSONObject(0).toString()
                        }
                    responseJson.shouldBeSimilarJsonTo(tilbakekrevingerResponseFromSak)
                    listOf(
                        tilbakekrevingRespons,
                        deserialize<TilbakekrevingsbehandlingJson>(tilbakekrevingerResponseFromSak),
                    ).forEach {
                        it.shouldBeEqualToIgnoringFields(
                            lagOpprettTilbakekrevingRespons(
                                sakId,
                                Tidspunkt.now(fixedClock),
                                saksversjon + 1,
                                status = TilbakekrevingsbehandlingStatus.FORHÅNDSVARSLET,
                            ),
                            it::id,
                            it::opprettet,
                            it::kravgrunnlag,
                        )

                        it.kravgrunnlag!!.shouldBeEqualToIgnoringFields(
                            lagKravgrunnlagRespons(
                                summertBetaltSkattForYtelsesgruppen = "3025",
                                summertBruttoFeilutbetaling = 6050,
                                summertBruttoNyUtbetaling = 2513,
                                summertBruttoTidligereUtbetalt = 8563,
                                summertNettoFeilutbetaling = 3025,
                                summertSkattFeilutbetaling = 3025,
                            ),
                            it.kravgrunnlag!!::hendelseId,
                            it.kravgrunnlag!!::kontrollfelt,
                        )
                    }
                }
                OppdaterKravgrunnlagTilbakekrevingsbehandlingRespons(
                    responsJson = tilbakekrevingRespons,
                    saksversjon = saksversjonEtter,
                )
            }
        }
    }
}

internal data class OppdaterKravgrunnlagTilbakekrevingsbehandlingRespons(
    val responsJson: TilbakekrevingsbehandlingJson,
    val saksversjon: Long,
)
