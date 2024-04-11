package no.nav.su.se.bakover.web.søknadsbehandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.web.SharedRegressionTestData.withTestApplicationAndEmbeddedDb
import no.nav.su.se.bakover.web.sak.SakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknad.lukkSøknad
import no.nav.su.se.bakover.web.vedtak.VedtakJson
import no.nav.su.se.bakover.web.vedtak.opprettNySøknadsbehandlingFraVedtak
import org.junit.jupiter.api.Test

class OpprettNyFraAvslagIT {

    @Test
    fun `kan opprette ny søknadsbehandling fra et avslagsvedtak (vilkår) - kan ikke opprette dersom søknaden senere blir lukket`() {
        withTestApplicationAndEmbeddedDb(clock = tikkendeFixedClock()) { appComponents ->
            val (sakId, søknadId) = opprettAvslåttSøknadsbehandlingPgaVilkår(client = this.client).let {
                Pair(BehandlingJson.hentSakId(it), BehandlingJson.hentSøknadId(it))
            }
            val sak = hentSak(sakId, client = this.client)
            // avslagsvedtaket fra opprettAvslåttSøknadsbehandling
            val vedtakId = SakJson.hentFørsteVedtak(sak).let {
                VedtakJson.hentVedtakId(it)
            }

            appComponents.opprettNySøknadsbehandlingFraVedtak(sakId, vedtakId, this.client, søknadId)
            appComponents.lukkSøknad(søknadId, this.client)
            appComponents.opprettNySøknadsbehandlingFraVedtak(
                sakId,
                vedtakId,
                this.client,
                søknadId,
                verifiserResponsVilkårAvslag = false,
                expectedHttpStatusCode = HttpStatusCode.BadRequest,
            )
        }
    }

    @Test
    fun `kan opprette ny søknadsbehandling fra et avslagsvedtak (beregning) - kan ikke opprette dersom søknaden senere blir lukket`() {
        withTestApplicationAndEmbeddedDb(clock = tikkendeFixedClock()) { appComponents ->
            val (sakId, behandlingId, søknadId) = opprettAvslåttSøknadsbehandlingPgaBeregning(client = this.client).let {
                Triple(
                    BehandlingJson.hentSakId(it),
                    BehandlingJson.hentBehandlingId(it),
                    BehandlingJson.hentSøknadId(it),
                )
            }
            val sak = hentSak(sakId, client = this.client)
            // avslagsvedtaket fra opprettAvslåttSøknadsbehandlingPgaBeregning
            val vedtakId = SakJson.hentFørsteVedtak(sak).let {
                verifiserVedtak(it, behandlingId)
                VedtakJson.hentVedtakId(it)
            }

            appComponents.opprettNySøknadsbehandlingFraVedtak(
                sakId = sakId,
                vedtakId = vedtakId,
                client = this.client,
                expectedSøknadId = søknadId,
                verifiserResponsVilkårAvslag = false,
                verifiserResponsBeregningAvslag = true,
            )
            appComponents.lukkSøknad(søknadId, this.client)
            appComponents.opprettNySøknadsbehandlingFraVedtak(
                sakId = sakId,
                vedtakId = vedtakId,
                client = this.client,
                expectedSøknadId = søknadId,
                verifiserResponsVilkårAvslag = false,
                verifiserResponsBeregningAvslag = false,
                expectedHttpStatusCode = HttpStatusCode.BadRequest,
            )
        }
    }

    @Test
    fun `kan ikke opprette ny behandling dersom det finnes en åpen behandling fra før`() {
        withTestApplicationAndEmbeddedDb(clock = tikkendeFixedClock()) { appComponents ->
            val (sakId, behandlingId, søknadId) = opprettAvslåttSøknadsbehandlingPgaBeregning(client = this.client).let {
                Triple(
                    BehandlingJson.hentSakId(it),
                    BehandlingJson.hentBehandlingId(it),
                    BehandlingJson.hentSøknadId(it),
                )
            }
            val (sak, fnr) = hentSak(sakId, client = this.client).let {
                it to SakJson.hentFnr(it)
            }
            // avslagsvedtaket fra opprettAvslåttSøknadsbehandlingPgaBeregning
            val vedtakId = SakJson.hentFørsteVedtak(sak).let {
                verifiserVedtak(it, behandlingId)
                VedtakJson.hentVedtakId(it)
            }

            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = "2022-01-01",
                tilOgMed = "2022-12-31",
                client = this.client,
                appComponents = appComponents,
                iverksett = { _, _ -> SKIP_STEP },
            )

            appComponents.opprettNySøknadsbehandlingFraVedtak(
                sakId = sakId,
                vedtakId = vedtakId,
                client = this.client,
                expectedSøknadId = søknadId,
                verifiserResponsVilkårAvslag = false,
                expectedHttpStatusCode = HttpStatusCode.BadRequest,
            )
        }
    }
}

private fun verifiserVedtak(vedtak: String, expectedBehandlingId: String) {
    //language=json
    val expected = """{
        "id":"ignored",
        "opprettet":"2021-01-01T01:02:36.456789Z",
        "behandlingId":"$expectedBehandlingId",
        "periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"},
        "saksbehandler":"Z990Lokal",
        "utbetalingId":null,
        "type":"AVSLAG",
        "dokumenttilstand":"GENERERT",
        "beregning":{
          "id":"ignored",
          "fraOgMed":"2021-01-01",
          "tilOgMed":"2021-12-31",
          "fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}],
          "opprettet":"2021-01-01T01:02:30.456789Z",
          "månedsberegninger":[{"satsbeløp":20946,"grunnbeløp":101351,"fraOgMed":"2021-01-01","epsInputFradrag":[],"fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-01-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-01-31"}}],"beløp":0,"tilOgMed":"2021-01-31","sats":"HØY","epsFribeløp":0,"merknader":[{"type":"BeløpErNull"}]},{"satsbeløp":20946,"grunnbeløp":101351,"fraOgMed":"2021-02-01","epsInputFradrag":[],"fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-02-01","tilOgMed":"2021-02-28"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-02-01","tilOgMed":"2021-02-28"}}],"beløp":0,"tilOgMed":"2021-02-28","sats":"HØY","epsFribeløp":0,"merknader":[{"type":"BeløpErNull"}]},{"satsbeløp":20946,"grunnbeløp":101351,"fraOgMed":"2021-03-01","epsInputFradrag":[],"fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-03-01","tilOgMed":"2021-03-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-03-01","tilOgMed":"2021-03-31"}}],"beløp":0,"tilOgMed":"2021-03-31","sats":"HØY","epsFribeløp":0,"merknader":[{"type":"BeløpErNull"}]},{"satsbeløp":20946,"grunnbeløp":101351,"fraOgMed":"2021-04-01","epsInputFradrag":[],"fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"}}],"beløp":0,"tilOgMed":"2021-04-30","sats":"HØY","epsFribeløp":0,"merknader":[{"type":"BeløpErNull"}]},{"satsbeløp":20946,"grunnbeløp":101351,"fraOgMed":"2021-05-01","epsInputFradrag":[],"fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}}],"beløp":0,"tilOgMed":"2021-05-31","sats":"HØY","epsFribeløp":0,"merknader":[{"type":"BeløpErNull"}]},{"satsbeløp":20946,"grunnbeløp":101351,"fraOgMed":"2021-06-01","epsInputFradrag":[],"fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}}],"beløp":0,"tilOgMed":"2021-06-30","sats":"HØY","epsFribeløp":0,"merknader":[{"type":"BeløpErNull"}]},{"satsbeløp":20946,"grunnbeløp":101351,"fraOgMed":"2021-07-01","epsInputFradrag":[],"fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}}],"beløp":0,"tilOgMed":"2021-07-31","sats":"HØY","epsFribeløp":0,"merknader":[{"type":"BeløpErNull"}]},{"satsbeløp":20946,"grunnbeløp":101351,"fraOgMed":"2021-08-01","epsInputFradrag":[],"fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}}],"beløp":0,"tilOgMed":"2021-08-31","sats":"HØY","epsFribeløp":0,"merknader":[{"type":"BeløpErNull"}]},{"satsbeløp":20946,"grunnbeløp":101351,"fraOgMed":"2021-09-01","epsInputFradrag":[],"fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}}],"beløp":0,"tilOgMed":"2021-09-30","sats":"HØY","epsFribeløp":0,"merknader":[{"type":"BeløpErNull"}]},{"satsbeløp":20946,"grunnbeløp":101351,"fraOgMed":"2021-10-01","epsInputFradrag":[],"fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}}],"beløp":0,"tilOgMed":"2021-10-31","sats":"HØY","epsFribeløp":0,"merknader":[{"type":"BeløpErNull"}]},{"satsbeløp":20946,"grunnbeløp":101351,"fraOgMed":"2021-11-01","epsInputFradrag":[],"fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}}],"beløp":0,"tilOgMed":"2021-11-30","sats":"HØY","epsFribeløp":0,"merknader":[{"type":"BeløpErNull"}]},{"satsbeløp":20946,"grunnbeløp":101351,"fraOgMed":"2021-12-01","epsInputFradrag":[],"fradrag":[{"beløp":35000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}}],"beløp":0,"tilOgMed":"2021-12-31","sats":"HØY","epsFribeløp":0,"merknader":[{"type":"BeløpErNull"}]}],
          "begrunnelse":"Beregning er kjørt automatisk av Beregn.kt"
        },
        "simulering":null,
        "attestant":"automatiskAttesteringAvSøknadsbehandling",
        "kanStarteNyBehandling":true,
        "skalSendeBrev": true
        }
    """.trimIndent()

    vedtak.shouldBeSimilarJsonTo(expected, "id", "beregning.id")
}
