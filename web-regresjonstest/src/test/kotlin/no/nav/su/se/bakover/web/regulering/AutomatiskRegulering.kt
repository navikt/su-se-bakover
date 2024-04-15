package no.nav.su.se.bakover.web.regulering

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo

internal fun regulerAutomatisk(
    fraOgMed: Måned,
    client: HttpClient,
) {
    return runBlocking {
        val correlationId = CorrelationId.generate()
        defaultRequest(
            HttpMethod.Post,
            "/reguleringer/automatisk",
            listOf(Brukerrolle.Drift),
            client = client,
            correlationId = correlationId.toString(),
        ) { setBody("""{"fraOgMedMåned": "$fraOgMed"}""") }.apply {
            withClue("automatiskReguler feilet: ${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
            }
        }
    }
}

fun verifyIverksattReguleringFraAutomatisk(
    actual: String,
    expectedFnr: String,
) {
    //language=json
    val expected = """{
        "id":"dd2e42a5-791c-464a-ac58-019de6dabf58",
        "sakId":"c5f18140-6144-438f-9df1-f95e5e451678",
        "opprettet":"2021-05-21T01:02:03.456789Z",
        "fnr":"$expectedFnr",
        "reguleringstype":"AUTOMATISK",
        "saksnummer":2021,
        "sakstype":"uføre",
        "reguleringsstatus":"IVERKSATT",
        "saksbehandler":"srvsupstonad",
        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
        "årsakForManuell":null,
        "erFerdigstilt":true,
        "beregning":{
          "id":"2950b023-9590-4993-8b6a-a4ff80e0addb",
          "fraOgMed":"2021-05-01",
          "tilOgMed":"2021-12-31",
          "opprettet":"2021-05-21T01:02:03.456789Z",
          "fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],
          "månedsberegninger":[{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-05-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}}],"beløp":11989,"tilOgMed":"2021-05-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-06-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}}],"beløp":11989,"tilOgMed":"2021-06-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-07-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}}],"beløp":11989,"tilOgMed":"2021-07-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-08-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}}],"beløp":11989,"tilOgMed":"2021-08-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-09-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}}],"beløp":11989,"tilOgMed":"2021-09-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-10-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}}],"beløp":11989,"tilOgMed":"2021-10-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-11-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}}],"beløp":11989,"tilOgMed":"2021-11-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-12-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}}],"beløp":11989,"tilOgMed":"2021-12-31","sats":"HØY","epsFribeløp":0,"merknader":[]}],
          "begrunnelse":null
        },
        "grunnlagsdataOgVilkårsvurderinger":{
          "uføre":{"vurderinger":[{"grunnlag":{"forventetInntekt":0,"opprettet":"2021-01-01T01:02:03.456789Z","uføregrad":100,"id":"46a95a77-6731-431d-b6e9-f0a050f85bd7","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},"opprettet":"2021-01-01T01:02:03.456789Z","id":"b3e234ce-1e84-4d0c-8254-826581e3becc","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
          "flyktning":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
          "lovligOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
          "fastOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
          "institusjonsopphold":{"vurderingsperioder":[{"vurdering":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
          "utenlandsopphold":{"vurderinger":[{"status":"SkalHoldeSegINorge","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"status":"SkalHoldeSegINorge"},
          "bosituasjon":[{"delerBolig":false,"sats":"HØY","fnr":null,"ektemakeEllerSamboerUførFlyktning":null,"type":"ENSLIG","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],
          "formue":{"vurderinger":[{"grunnlag":{"søkersFormue":{"innskudd":0,"verdipapir":0,"pengerSkyldt":0,"verdiKjøretøy":0,"verdiIkkePrimærbolig":0,"depositumskonto":0,"kontanter":0,"verdiEiendommer":0},"epsFormue":null},"opprettet":"2021-01-01T01:02:03.456789Z","id":"7a3c23ff-e2cb-4b75-95a1-56483d90fbe0","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"formuegrenser":[{"beløp":53200,"gyldigFra":"2021-05-01"},{"beløp":50676,"gyldigFra":"2020-05-01"}],"resultat":"VilkårOppfylt"},
          "personligOppmøte":{"vurderinger":[{"vurdering":"MøttPersonlig","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
          "opplysningsplikt":{"vurderinger":[{"beskrivelse":"TilstrekkeligDokumentasjon","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}]},
          "fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],
          "familiegjenforening":null,
          "pensjon":null
          },
        "simulering":{"totalOppsummering":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":95912,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":95912,"sumTilUtbetaling":95912,"sumReduksjonFeilkonto":0},"periodeOppsummering":[{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11989,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11989,"sumTilUtbetaling":11989,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11989,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11989,"sumTilUtbetaling":11989,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11989,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11989,"sumTilUtbetaling":11989,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11989,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11989,"sumTilUtbetaling":11989,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11989,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11989,"sumTilUtbetaling":11989,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11989,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11989,"sumTilUtbetaling":11989,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11989,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11989,"sumTilUtbetaling":11989,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11989,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11989,"sumTilUtbetaling":11989,"sumReduksjonFeilkonto":0}]},
        "avsluttet":null
      }
    """.trimIndent()

    actual.shouldBeSimilarJsonTo(expected, "id", "sakId", "beregning.id", "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].id", "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].grunnlag.id", "grunnlagsdataOgVilkårsvurderinger.formue.vurderinger[*].id")
}
