package no.nav.su.se.bakover.web.regulering

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.test.application.formdataRequest
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

internal fun regulerAutomatiskMultipart(
    fraOgMed: Måned,
    client: HttpClient,
    supplement: String,
) {
    return runBlocking {
        val correlationId = CorrelationId.generate()
        formdataRequest(
            method = HttpMethod.Post,
            uri = "/reguleringer/automatisk",
            roller = listOf(Brukerrolle.Drift),
            formData = formData {
                append("fraOgMedMåned", "$fraOgMed")
                append("csvFile", "file.csv") {
                    this.append(supplement)
                }
            },
            correlationId = correlationId.toString(),
            client = client,
        ).apply {
            withClue("automatisk regulering multipart request feilet: ${this.bodyAsText()}") {
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
        "id":"ignored",
        "sakId":"ignored",
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
          "id":"ignored",
          "fraOgMed":"2021-05-01",
          "tilOgMed":"2021-12-31",
          "opprettet":"2021-05-21T01:02:03.456789Z",
          "fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],
          "månedsberegninger":[{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-05-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}}],"beløp":11989,"tilOgMed":"2021-05-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-06-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}}],"beløp":11989,"tilOgMed":"2021-06-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-07-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}}],"beløp":11989,"tilOgMed":"2021-07-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-08-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}}],"beløp":11989,"tilOgMed":"2021-08-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-09-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}}],"beløp":11989,"tilOgMed":"2021-09-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-10-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}}],"beløp":11989,"tilOgMed":"2021-10-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-11-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}}],"beløp":11989,"tilOgMed":"2021-11-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-12-01","epsInputFradrag":[],"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"PrivatPensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}}],"beløp":11989,"tilOgMed":"2021-12-31","sats":"HØY","epsFribeløp":0,"merknader":[]}],
          "begrunnelse":null
        },
        "grunnlagsdataOgVilkårsvurderinger":{
          "uføre":{"vurderinger":[{"grunnlag":{"forventetInntekt":0,"opprettet":"2021-01-01T01:02:03.456789Z","uføregrad":100,"id":"ignored","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},"opprettet":"2021-01-01T01:02:03.456789Z","id":"ignored","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
          "flyktning":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
          "lovligOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
          "fastOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
          "institusjonsopphold":{"vurderingsperioder":[{"vurdering":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
          "utenlandsopphold":{"vurderinger":[{"status":"SkalHoldeSegINorge","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"status":"SkalHoldeSegINorge"},
          "bosituasjon":[{"delerBolig":false,"sats":"HØY","fnr":null,"ektemakeEllerSamboerUførFlyktning":null,"type":"ENSLIG","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],
          "formue":{"vurderinger":[{"grunnlag":{"søkersFormue":{"innskudd":0,"verdipapir":0,"pengerSkyldt":0,"verdiKjøretøy":0,"verdiIkkePrimærbolig":0,"depositumskonto":0,"kontanter":0,"verdiEiendommer":0},"epsFormue":null},"opprettet":"2021-01-01T01:02:03.456789Z","id":"ignored","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"formuegrenser":[{"beløp":53200,"gyldigFra":"2021-05-01"},{"beløp":50676,"gyldigFra":"2020-05-01"}],"resultat":"VilkårOppfylt"},
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

    actual.shouldBeSimilarJsonTo(
        expected,
        "id",
        "sakId",
        "beregning.id",
        "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].id",
        "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].grunnlag.id",
        "grunnlagsdataOgVilkårsvurderinger.formue.vurderinger[*].id",
    )
}

fun verifyAutomatiskRegulertMedSupplement(
    actual: String,
    expectedFnr: String,
) {
    //language=json
    val expected = """{
        "id":"ignored",
        "sakId":"ignored",
        "opprettet":"2021-05-21T01:02:03.456789Z",
        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
        "sakstype":"uføre",
        "saksnummer":2021,
        "fnr":"$expectedFnr",
        "reguleringsstatus":"IVERKSATT",
        "reguleringstype":"AUTOMATISK",
        "saksbehandler":"srvsupstonad",
        "årsakForManuell":null,
        "grunnlagsdataOgVilkårsvurderinger":{"familiegjenforening":null,"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"personligOppmøte":{"vurderinger":[{"vurdering":"MøttPersonlig","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"formue":{"vurderinger":[{"grunnlag":{"søkersFormue":{"innskudd":0,"verdipapir":0,"pengerSkyldt":0,"verdiKjøretøy":0,"verdiIkkePrimærbolig":0,"depositumskonto":0,"kontanter":0,"verdiEiendommer":0},"epsFormue":null},"opprettet":"2021-01-01T01:02:03.456789Z","id":"ignored","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"formuegrenser":[{"beløp":53200,"gyldigFra":"2021-05-01"},{"beløp":50676,"gyldigFra":"2020-05-01"}],"resultat":"VilkårOppfylt"},"fastOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"flyktning":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"utenlandsopphold":{"vurderinger":[{"status":"SkalHoldeSegINorge","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"status":"SkalHoldeSegINorge"},"lovligOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"opplysningsplikt":{"vurderinger":[{"beskrivelse":"TilstrekkeligDokumentasjon","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}]},"bosituasjon":[{"delerBolig":false,"sats":"HØY","fnr":null,"ektemakeEllerSamboerUførFlyktning":null,"type":"ENSLIG","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"uføre":{"vurderinger":[{"grunnlag":{"forventetInntekt":0,"opprettet":"2021-01-01T01:02:03.456789Z","uføregrad":100,"id":"ignored","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},"opprettet":"2021-01-01T01:02:03.456789Z","id":"ignored","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"pensjon":null,"institusjonsopphold":{"vurderingsperioder":[{"vurdering":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"}},
        "beregning":{"begrunnelse":null,"fraOgMed":"2021-05-01","fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"opprettet":"2021-05-21T01:02:03.456789Z","tilOgMed":"2021-12-31","id":"ignored","månedsberegninger":[{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-05-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}}],"beløp":11489,"tilOgMed":"2021-05-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-06-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}}],"beløp":11489,"tilOgMed":"2021-06-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-07-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}}],"beløp":11489,"tilOgMed":"2021-07-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-08-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}}],"beløp":11489,"tilOgMed":"2021-08-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-09-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}}],"beløp":11489,"tilOgMed":"2021-09-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-10-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}}],"beløp":11489,"tilOgMed":"2021-10-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-11-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}}],"beløp":11489,"tilOgMed":"2021-11-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-12-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}}],"beløp":11489,"tilOgMed":"2021-12-31","sats":"HØY","epsFribeløp":0,"merknader":[]}]},
        "simulering":{"totalOppsummering":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":91912,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":91912,"sumTilUtbetaling":91912,"sumReduksjonFeilkonto":0},"periodeOppsummering":[{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11489,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11489,"sumTilUtbetaling":11489,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11489,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11489,"sumTilUtbetaling":11489,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11489,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11489,"sumTilUtbetaling":11489,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11489,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11489,"sumTilUtbetaling":11489,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11489,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11489,"sumTilUtbetaling":11489,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11489,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11489,"sumTilUtbetaling":11489,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11489,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11489,"sumTilUtbetaling":11489,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11489,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11489,"sumTilUtbetaling":11489,"sumReduksjonFeilkonto":0}]},
        "erFerdigstilt":true,
        "avsluttet":null
    }
    """.trimIndent()

    actual.shouldBeSimilarJsonTo(
        expected,
        "id",
        "sakId",
        "beregning.id",
        "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].id",
        "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].grunnlag.id",
        "grunnlagsdataOgVilkårsvurderinger.formue.vurderinger[*].id",
    )
}

fun verifyManuellReguleringMedSupplement(
    actual: String,
    expectedFnr: String,
) {
    println(expectedFnr)
    //language=json
    val expected = """{
        "id":"ignored",
        "sakId":"ignored",
        "fnr":"$expectedFnr",
        "saksnummer":2022,
        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
        "saksbehandler":"srvsupstonad",
        "reguleringstype":"MANUELL",
        "opprettet":"2021-05-21T01:02:03.456789Z",
        "årsakForManuell":["FradragMåHåndteresManuelt"],
        "sakstype":"uføre",
        "reguleringsstatus":"OPPRETTET",
        "grunnlagsdataOgVilkårsvurderinger":{"familiegjenforening":null,"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"personligOppmøte":{"vurderinger":[{"vurdering":"MøttPersonlig","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"formue":{"vurderinger":[{"grunnlag":{"søkersFormue":{"innskudd":0,"verdipapir":0,"pengerSkyldt":0,"verdiKjøretøy":0,"verdiIkkePrimærbolig":0,"depositumskonto":0,"kontanter":0,"verdiEiendommer":0},"epsFormue":null},"opprettet":"2021-01-01T01:02:03.456789Z","id":"ignored","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"formuegrenser":[{"beløp":53200,"gyldigFra":"2021-05-01"},{"beløp":50676,"gyldigFra":"2020-05-01"}],"resultat":"VilkårOppfylt"},"fastOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"flyktning":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"utenlandsopphold":{"vurderinger":[{"status":"SkalHoldeSegINorge","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"status":"SkalHoldeSegINorge"},"lovligOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"opplysningsplikt":{"vurderinger":[{"beskrivelse":"TilstrekkeligDokumentasjon","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}]},"bosituasjon":[{"delerBolig":false,"sats":"HØY","fnr":null,"ektemakeEllerSamboerUførFlyktning":null,"type":"ENSLIG","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"uføre":{"vurderinger":[{"grunnlag":{"forventetInntekt":0,"opprettet":"2021-01-01T01:02:03.456789Z","uføregrad":100,"id":"ignored","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},"opprettet":"2021-01-01T01:02:03.456789Z","id":"ignored","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"pensjon":null,"institusjonsopphold":{"vurderingsperioder":[{"vurdering":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"}},
        "beregning":null,
        "simulering":null,
        "erFerdigstilt":false,
        "avsluttet":null
    }
    """.trimIndent()

    actual.shouldBeSimilarJsonTo(
        expected,
        "id",
        "sakId",
        "beregning.id",
        "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].id",
        "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].grunnlag.id",
        "grunnlagsdataOgVilkårsvurderinger.formue.vurderinger[*].id",
    )
}
