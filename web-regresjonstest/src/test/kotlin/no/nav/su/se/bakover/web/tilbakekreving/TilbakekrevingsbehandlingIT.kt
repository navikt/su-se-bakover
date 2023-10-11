package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.services.tilbakekreving.lagreRåttKravgrunnlagForUtbetalingerSomMangler
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

internal class TilbakekrevingsbehandlingIT {

    @Test
    fun `kan opprette tilbakekrevingsbehandling`() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            clock = clock,
        ) { appComponents ->
            val stønadStart = 1.januar(2021)
            val stønadSlutt = 31.januar(2021)
            val fnr = Fnr.generer().toString()

            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = stønadStart.toString(),
                tilOgMed = stønadSlutt.toString(),
                client = this.client,
                appComponents = appComponents,
            ).let { søknadsbehandlingJson ->

                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

                @Suppress("UNUSED_VARIABLE")
                val revurderingId = opprettIverksattRevurdering(
                    sakid = sakId,
                    fraogmed = 1.januar(2021).toString(),
                    tilogmed = 31.januar(2021).toString(),
                    client = this.client,
                    appComponents = appComponents,
                    skalUtsetteTilbakekreving = true,
                ).let {
                    RevurderingJson.hentRevurderingId(it)
                }
                // Emulerer at det kommer et kravgrunnlag på køen som matcher revurderingen sin simulering.
                lagreRåttKravgrunnlagForUtbetalingerSomMangler(
                    sessionFactory = appComponents.databaseRepos.sessionFactory,
                    service = appComponents.tilbakekrevingskomponenter.services.råttKravgrunnlagService,
                    clock = clock,
                )
                verifiserKravgrunnlagPåSak(sakId, client, false, 1)
                // Siden vi ikke kjører jobbene i test-miljøet må vi også kjøre denne konsumenten.
                appComponents.tilbakekrevingskomponenter.services.knyttKravgrunnlagTilSakOgUtbetalingKonsument.knyttKravgrunnlagTilSakOgUtbetaling(
                    // Denne får versjon 2
                    correlationId = CorrelationId.generate(),
                )
                verifiserKravgrunnlagPåSak(sakId, client, true, 2)
                opprett(
                    // Denne vil få versjon 3 (vi bekrefter at siste versjonen er 2)
                    sakId = sakId,
                    // Må økes etter hvert som vi får flere hendelser.
                    versjon = 2,
                    client = this.client,
                )
                // Det vil også opprettes en oppgave asynkront, men de jobbene kjøres ikke automatisk i regresjonstestene.
                verifiserKravgrunnlagPåSak(sakId, client, true, 3)
            }
        }
    }

    private fun opprett(
        sakId: String,
        versjon: Long = 1,
        @Suppress("UNUSED_PARAMETER") nesteVersjon: Long = versjon + 1,
        client: HttpClient,
        expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    ) {
        val actual = opprettTilbakekrevingsbehandling(
            sakId = sakId,
            saksversjon = versjon,
            expectedHttpStatusCode = expectedHttpStatusCode,
            client = client,
        )
        val expected = """
{
  "id":"ignore-me",
  "sakId":"$sakId",
  "opprettet":"2021-02-01T01:03:44.456789Z",
  "opprettetAv":"Z990Lokal",
  "kravgrunnlag":{
    "eksternKravgrunnlagsId":"123456",
    "eksternVedtakId":"654321",
    "kontrollfelt":"2021-02-01-02.03.39.456789",
    "status":"NY",
    "grunnlagsperiode":[
      {
        "periode":{
          "fraOgMed":"2021-01-01",
          "tilOgMed":"2021-01-31"
        },
        "beløpSkattMnd":"4395",
        "feilutbetaling": {
            "kode":"KL_KODE_FEIL_INNT",
            "beløpTidligereUtbetaling":"0",
            "beløpNyUtbetaling":"12383",
            "beløpSkalTilbakekreves":"0",
            "beløpSkalIkkeTilbakekreves":"0",
          },
          "ytelse": {
            "kode":"SUUFORE",
            "beløpTidligereUtbetaling":"20946",
            "beløpNyUtbetaling":"8563",
            "beløpSkalTilbakekreves":"12383",
            "beløpSkalIkkeTilbakekreves":"0",
            "skatteProsent":"43.9983"
          },
      }
    ]
  },
  "status":"OPPRETTET",
  "månedsvurderinger":[],
  "fritekst":null
}"""
        JSONAssert.assertEquals(
            expected,
            actual,
            CustomComparator(
                JSONCompareMode.STRICT,
                Customization(
                    "id",
                ) { _, _ -> true },
            ),
        )
    }
}
private fun verifiserKravgrunnlagPåSak(
    sakId: String,
    client: HttpClient,
    forventerKravgrunnlag: Boolean,
    versjon: Int,
) {
    hentSak(sakId, client = client).also { sakJson ->
        // Kravgrunnlaget vil være utestående så lenge vi ikke har iverksatt tilbakekrevingsbehandlingen.
        JSONObject(sakJson).isNull("uteståendeKravgrunnlag") shouldBe !forventerKravgrunnlag
        JSONObject(sakJson).getInt("versjon") shouldBe versjon
    }
}
