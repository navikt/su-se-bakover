package no.nav.su.se.bakover.web.tilbakekreving

import common.presentation.attestering.AttesteringJson
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
import tilbakekreving.presentation.api.common.ForhåndsvarselMetaInfoJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingStatus
import tilbakekreving.presentation.api.common.VurderingerMedKravJson

object VurderTilbakekrevingsbehandling {
    internal fun vurderTilbakekrevingsbehandling(
        sakId: String,
        tilbakekrevingsbehandlingId: String,
        expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
        client: HttpClient,
        verifiserRespons: Boolean = true,
        saksversjon: Long,
        verifiserForhåndsvarselDokumenter: List<ForhåndsvarselMetaInfoJson> = emptyList(),
        tilstand: String = "VURDERT",
        vurderingerRequest: String = """
        [
            {
                "periode": {
                    "fraOgMed": "2021-01-01",
                    "tilOgMed": "2021-01-31"
                },
                "vurdering": "SkalTilbakekreve"
            }
        ]
        """.trimIndent(),
        expectedVurderinger: VurderingerMedKravJson? = lagVurderingerMedKravJson(),
        expectedFritekst: String? = null,
        expectedAttesteringer: List<AttesteringJson> = emptyList(),
        expectedNotat: String? = null,
    ): VurderTilbakekrevingsbehandlingRespons {
        val sakFørKallJson = hentSak(sakId, client)
        return runBlocking {
            no.nav.su.se.bakover.test.application.defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/vurder",
                listOf(Brukerrolle.Saksbehandler),
                client = client,
            ) {
                setBody(
                    """
            {
                "versjon": $saksversjon,
                "perioder": $vurderingerRequest
            }
                    """.trimIndent(),
                )
            }.apply {
                withClue("Kunne ikke forhåndsvarsle tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                    status shouldBe expectedHttpStatusCode
                }
            }.bodyAsText().let { responseJson ->
                // Dette kallet har ingen side-effekter.
                val sakEtterKallJson = hentSak(sakId, client)
                val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")
                val tilbakekrevingRespons = deserialize<TilbakekrevingsbehandlingJson>(responseJson)
                if (verifiserRespons) {
                    sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
                    saksversjonEtter shouldBe saksversjon + 1
                    listOf(
                        tilbakekrevingRespons,
                        deserialize<TilbakekrevingsbehandlingJson>(
                            JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString(),
                        ),
                    ).forEach {
                        it.shouldBeEqualToIgnoringFields(
                            lagOpprettTilbakekrevingRespons(
                                sakId,
                                Tidspunkt.now(fixedClock),
                                saksversjon + 1,
                                TilbakekrevingsbehandlingStatus.valueOf(tilstand),
                                fritekst = expectedFritekst,
                                notat = expectedNotat,
                            ),
                            it::id,
                            it::opprettet,
                            it::kravgrunnlag,
                            it::forhåndsvarselsInfo,
                            it::vurderinger,
                            it::attesteringer,
                        )

                        it.forhåndsvarselsInfo shouldBe verifiserForhåndsvarselDokumenter

                        it.kravgrunnlag!!.shouldBeEqualToIgnoringFields(
                            lagKravgrunnlagRespons(),
                            it.kravgrunnlag!!::hendelseId,
                            it.kravgrunnlag!!::kontrollfelt,

                        )
                        it.vurderinger!!.shouldBeEqualToIgnoringFields(
                            expectedVurderinger!!,
                            it.kravgrunnlag!!::kontrollfelt,
                            it.vurderinger!!::eksternKontrollfelt,
                        )

                        it.attesteringer shouldBe expectedAttesteringer
                    }
                }
                VurderTilbakekrevingsbehandlingRespons(
                    saksversjon = saksversjonEtter,
                    vurderinger = tilbakekrevingRespons.vurderinger!!,
                )
            }
        }
    }
}

internal data class VurderTilbakekrevingsbehandlingRespons(
    val vurderinger: VurderingerMedKravJson,
    val saksversjon: Long,
)
