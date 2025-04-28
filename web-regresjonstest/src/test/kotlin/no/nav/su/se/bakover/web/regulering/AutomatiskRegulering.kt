package no.nav.su.se.bakover.web.regulering

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.test.application.formdataRequest
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.web.komponenttest.AppComponents

internal fun regulerAutomatisk(
    fraOgMed: Måned,
    client: HttpClient,
    body: String = """{"fraOgMedMåned": "$fraOgMed"}""",
) {
    return runBlocking {
        val correlationId = CorrelationId.generate()
        defaultRequest(
            HttpMethod.Post,
            "/reguleringer/automatisk",
            listOf(Brukerrolle.Drift),
            client = client,
            correlationId = correlationId.toString(),
        ) { setBody(body) }.apply {
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
                append(
                    "csvFile",
                    supplement,
                    Headers.build {
                        append(HttpHeaders.ContentType, "text/csv")
                        append(HttpHeaders.ContentDisposition, "filename=file.csv")
                    },
                )
            },
            correlationId = correlationId.toString(),
            client = client,
        ).apply {
            withClue("automatisk regulering (multipart request) feilet: ${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
            }
        }
    }
}

internal fun ettersendSupplement(
    fraOgMed: Måned,
    supplement: String,
    client: HttpClient,
) {
    return runBlocking {
        val correlationId = CorrelationId.generate()
        formdataRequest(
            method = HttpMethod.Post,
            uri = "/reguleringer/supplement",
            roller = listOf(Brukerrolle.Drift),
            formData = formData {
                append("fraOgMedMåned", "$fraOgMed")
                append(
                    "csvFile",
                    supplement,
                    Headers.build {
                        append(HttpHeaders.ContentType, "text/csv")
                        append(HttpHeaders.ContentDisposition, "filename=file.csv")
                    },
                )
            },
            correlationId = correlationId.toString(),
            client = client,
        ).apply {
            withClue("ettersend av supplement for regulering (multipart request) feilet: ${this.bodyAsText()}") {
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
        "årsakForManuell":[],
        "supplement":{"eps":[],"bruker":null},
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
        "årsakForManuell":[],
        "supplement":{"eps":[],"bruker":{"eksterneVedtaksdata":[{"ytelseskomponenttype":"UT_ORDINER","vedtakstype":"ENDRING","fraOgMed":"01.04.2024","nettoYtelse":"10000","nettoYtelseskomponent":"10000","sakstype":"UFOREP","tilOgMed":"30.04.2024","bruttoYtelse":"10000","fnr":"$expectedFnr","bruttoYtelseskomponent":"10000"},{"ytelseskomponenttype":"UT_GJT","vedtakstype":"REGULERING","fraOgMed":"01.05.2024","nettoYtelse":"10500","nettoYtelseskomponent":"10500","sakstype":"UFOREP","tilOgMed":null,"bruttoYtelse":"10500","fnr":"$expectedFnr","bruttoYtelseskomponent":"10500"}],"fnr":"$expectedFnr","fradragsperioder":[{"fradragstype":"Uføretrygd","vedtaksperiodeRegulering":[{"beløp":10500,"periode":{"fraOgMed":"2024-05-01","tilOgMed":null}}],"vedtaksperiodeEndring":{"beløp":10000,"periode":{"fraOgMed":"2024-04-01","tilOgMed":"2024-04-30"}}}]}},
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
        "årsakForManuell":[{"type": "SupplementInneholderIkkeFradraget","begrunnelse":"Vi fant et supplement for BRUKER, men ikke for Alderspensjon.","fradragTilhører":"BRUKER","fradragskategori":"Alderspensjon"}],
        "supplement":{"eps":[],"bruker":{"eksterneVedtaksdata":[{"ytelseskomponenttype":"UT_ORDINER","vedtakstype":"REGULERING","fraOgMed":"01.05.2024","nettoYtelse":"10900","nettoYtelseskomponent":"10900","sakstype":"UFOREP","tilOgMed":null,"bruttoYtelse":"10900","fnr":"$expectedFnr","bruttoYtelseskomponent":"10900"},{"ytelseskomponenttype":"UT_TSB","vedtakstype":"ENDRING","fraOgMed":"01.04.2024","nettoYtelse":"10000","nettoYtelseskomponent":"10000","sakstype":"UFOREP","tilOgMed":"30.04.2024","bruttoYtelse":"10000","fnr":"$expectedFnr","bruttoYtelseskomponent":"10000"}],"fnr":"$expectedFnr","fradragsperioder":[{"fradragstype":"Uføretrygd","vedtaksperiodeRegulering":[{"beløp":10900,"periode":{"fraOgMed":"2024-05-01","tilOgMed":null}}],"vedtaksperiodeEndring":{"beløp":10000,"periode":{"fraOgMed":"2024-04-01","tilOgMed":"2024-04-30"}}}]}},
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

fun verifyAutomatiskRegulertForEPS(
    actual: String,
    expectedSøkersFnr: String,
    expectedEpsFnr: String,
) {
    //language=json
    val expected = """{
        "id":"ignored",
        "sakId":"ignored",
        "saksnummer":2021,
        "fnr":"$expectedSøkersFnr",
        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
        "sakstype":"uføre",
        "reguleringstype":"AUTOMATISK",
        "saksbehandler":"srvsupstonad",
        "reguleringsstatus":"IVERKSATT",
        "opprettet":"2021-05-21T01:02:03.456789Z",
        "årsakForManuell":[],
        "supplement":{"eps":[{"eksterneVedtaksdata":[{"ytelseskomponenttype":"UT_ORDINER","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","nettoYtelse":"10500","nettoYtelseskomponent":"10500","sakstype":"UFOREP","tilOgMed":null,"bruttoYtelse":"10500","fnr":"$expectedEpsFnr","bruttoYtelseskomponent":"10500"},{"ytelseskomponenttype":"UT_ORDINER","vedtakstype":"ENDRING","fraOgMed":"01.04.2021","nettoYtelse":"10000","nettoYtelseskomponent":"10000","sakstype":"UFOREP","tilOgMed":"30.04.2021","bruttoYtelse":"10000","fnr":"$expectedEpsFnr","bruttoYtelseskomponent":"10000"}],"fnr":"$expectedEpsFnr","fradragsperioder":[{"fradragstype":"Uføretrygd","vedtaksperiodeRegulering":[{"beløp":10500,"periode":{"fraOgMed":"2021-05-01","tilOgMed":null}}],"vedtaksperiodeEndring":{"beløp":10000,"periode":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"}}}]}],"bruker":null},
        "grunnlagsdataOgVilkårsvurderinger":{"familiegjenforening":null,"fradrag":[{"beløp":10500,"tilhører":"EPS","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"personligOppmøte":{"vurderinger":[{"vurdering":"MøttPersonlig","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"formue":{"vurderinger":[{"grunnlag":{"søkersFormue":{"innskudd":0,"verdipapir":0,"pengerSkyldt":0,"verdiKjøretøy":0,"verdiIkkePrimærbolig":0,"depositumskonto":0,"kontanter":0,"verdiEiendommer":0},"epsFormue":{"innskudd":0,"verdipapir":0,"pengerSkyldt":0,"verdiKjøretøy":0,"verdiIkkePrimærbolig":0,"depositumskonto":0,"kontanter":0,"verdiEiendommer":0}},"opprettet":"2021-01-01T01:02:03.456789Z","id":"ignored","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"formuegrenser":[{"beløp":53200,"gyldigFra":"2021-05-01"},{"beløp":50676,"gyldigFra":"2020-05-01"}],"resultat":"VilkårOppfylt"},"fastOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"flyktning":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"utenlandsopphold":{"vurderinger":[{"status":"SkalHoldeSegINorge","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"status":"SkalHoldeSegINorge"},"lovligOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"opplysningsplikt":{"vurderinger":[{"beskrivelse":"TilstrekkeligDokumentasjon","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}]},"bosituasjon":[{"delerBolig":null,"sats":"ORDINÆR","fnr":"$expectedEpsFnr","ektemakeEllerSamboerUførFlyktning":true,"type":"EPS_UFØR_FLYKTNING","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"uføre":{"vurderinger":[{"grunnlag":{"forventetInntekt":0,"opprettet":"2021-01-01T01:02:03.456789Z","uføregrad":100,"id":"ignored","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},"opprettet":"2021-01-01T01:02:03.456789Z","id":"ignored","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"pensjon":null,"institusjonsopphold":{"vurderingsperioder":[{"vurdering":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"}},
        "beregning":{"begrunnelse":null,"fraOgMed":"2021-05-01","fradrag":[{"beløp":10500,"tilhører":"EPS","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"opprettet":"2021-05-21T01:02:03.456789Z","tilOgMed":"2021-12-31","id":"ignored","månedsberegninger":[{"satsbeløp":20216,"grunnbeløp":106399,"fraOgMed":"2021-05-01","epsInputFradrag":[{"beløp":10500,"tilhører":"EPS","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}}],"fradrag":[{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}}],"beløp":20216,"tilOgMed":"2021-05-31","sats":"ORDINÆR","epsFribeløp":20215.81,"merknader":[]},{"satsbeløp":20216,"grunnbeløp":106399,"fraOgMed":"2021-06-01","epsInputFradrag":[{"beløp":10500,"tilhører":"EPS","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}}],"fradrag":[{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}}],"beløp":20216,"tilOgMed":"2021-06-30","sats":"ORDINÆR","epsFribeløp":20215.81,"merknader":[]},{"satsbeløp":20216,"grunnbeløp":106399,"fraOgMed":"2021-07-01","epsInputFradrag":[{"beløp":10500,"tilhører":"EPS","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}}],"fradrag":[{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}}],"beløp":20216,"tilOgMed":"2021-07-31","sats":"ORDINÆR","epsFribeløp":20215.81,"merknader":[]},{"satsbeløp":20216,"grunnbeløp":106399,"fraOgMed":"2021-08-01","epsInputFradrag":[{"beløp":10500,"tilhører":"EPS","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}}],"fradrag":[{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}}],"beløp":20216,"tilOgMed":"2021-08-31","sats":"ORDINÆR","epsFribeløp":20215.81,"merknader":[]},{"satsbeløp":20216,"grunnbeløp":106399,"fraOgMed":"2021-09-01","epsInputFradrag":[{"beløp":10500,"tilhører":"EPS","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}}],"fradrag":[{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}}],"beløp":20216,"tilOgMed":"2021-09-30","sats":"ORDINÆR","epsFribeløp":20215.81,"merknader":[]},{"satsbeløp":20216,"grunnbeløp":106399,"fraOgMed":"2021-10-01","epsInputFradrag":[{"beløp":10500,"tilhører":"EPS","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}}],"fradrag":[{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}}],"beløp":20216,"tilOgMed":"2021-10-31","sats":"ORDINÆR","epsFribeløp":20215.81,"merknader":[]},{"satsbeløp":20216,"grunnbeløp":106399,"fraOgMed":"2021-11-01","epsInputFradrag":[{"beløp":10500,"tilhører":"EPS","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}}],"fradrag":[{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}}],"beløp":20216,"tilOgMed":"2021-11-30","sats":"ORDINÆR","epsFribeløp":20215.81,"merknader":[]},{"satsbeløp":20216,"grunnbeløp":106399,"fraOgMed":"2021-12-01","epsInputFradrag":[{"beløp":10500,"tilhører":"EPS","utenlandskInntekt":null,"type":"Uføretrygd","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}}],"fradrag":[{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}}],"beløp":20216,"tilOgMed":"2021-12-31","sats":"ORDINÆR","epsFribeløp":20215.81,"merknader":[]}]},
        "simulering":{"totalOppsummering":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":161728,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":161728,"sumTilUtbetaling":161728,"sumReduksjonFeilkonto":0},"periodeOppsummering":[{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":20216,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":20216,"sumTilUtbetaling":20216,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":20216,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":20216,"sumTilUtbetaling":20216,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":20216,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":20216,"sumTilUtbetaling":20216,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":20216,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":20216,"sumTilUtbetaling":20216,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":20216,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":20216,"sumTilUtbetaling":20216,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":20216,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":20216,"sumTilUtbetaling":20216,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":20216,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":20216,"sumTilUtbetaling":20216,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":20216,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":20216,"sumTilUtbetaling":20216,"sumReduksjonFeilkonto":0}]},
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

fun verifyReguleringFørEttersendelse(
    actual: String,
    expectedFnr: String,
) {
    //language=json
    val expected = """{
        "id":"ignored",
        "sakId":"ignored",
        "fnr":"$expectedFnr",
        "saksnummer":2021,
        "sakstype":"uføre",
        "opprettet":"2021-05-21T01:02:03.456789Z",
        "saksbehandler":"srvsupstonad",
        "reguleringstype":"MANUELL",
        "reguleringsstatus":"OPPRETTET",
        "årsakForManuell":[{"type": "BrukerManglerSupplement","begrunnelse":"Fradraget til BRUKER: Alderspensjon påvirkes av samme sats/G-verdi endring som SU. Vi mangler supplement for dette fradraget og derfor går det til manuell regulering.","fradragTilhører":"BRUKER","fradragskategori":"Alderspensjon"}],
        "supplement":{"eps":[],"bruker":null},
        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
        "grunnlagsdataOgVilkårsvurderinger":{"familiegjenforening":null,"fradrag":[{"beløp":10000,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"personligOppmøte":{"vurderinger":[{"vurdering":"MøttPersonlig","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"formue":{"vurderinger":[{"grunnlag":{"søkersFormue":{"innskudd":0,"verdipapir":0,"pengerSkyldt":0,"verdiKjøretøy":0,"verdiIkkePrimærbolig":0,"depositumskonto":0,"kontanter":0,"verdiEiendommer":0},"epsFormue":null},"opprettet":"2021-01-01T01:02:03.456789Z","id":"4f1ae83d-5b5b-481d-8969-9b376c66e596","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"formuegrenser":[{"beløp":53200,"gyldigFra":"2021-05-01"},{"beløp":50676,"gyldigFra":"2020-05-01"}],"resultat":"VilkårOppfylt"},"fastOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"flyktning":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"utenlandsopphold":{"vurderinger":[{"status":"SkalHoldeSegINorge","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"status":"SkalHoldeSegINorge"},"lovligOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"opplysningsplikt":{"vurderinger":[{"beskrivelse":"TilstrekkeligDokumentasjon","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}]},"bosituasjon":[{"delerBolig":false,"sats":"HØY","fnr":null,"ektemakeEllerSamboerUførFlyktning":null,"type":"ENSLIG","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"uføre":{"vurderinger":[{"grunnlag":{"forventetInntekt":0,"opprettet":"2021-01-01T01:02:03.456789Z","uføregrad":100,"id":"ca09ddd5-a85c-4329-ba74-123bb92f1243","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},"opprettet":"2021-01-01T01:02:03.456789Z","id":"7138c0bd-e450-43a4-b17b-dd004a4e2ee7","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"pensjon":null,"institusjonsopphold":{"vurderingsperioder":[{"vurdering":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"}},
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

fun verifyReguleringEtterEttersendelse(
    actual: String,
    expectedFnr: String,
) {
    //language=json
    val expected = """{
        "id":"ignored",
        "sakId":"ignored",
        "fnr":"$expectedFnr",
        "saksnummer":2021,
        "saksbehandler":"srvsupstonad",
        "sakstype":"uføre",
        "opprettet":"2021-05-21T01:02:03.456789Z",
        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
        "reguleringstype":"AUTOMATISK",
        "reguleringsstatus":"IVERKSATT",
        "årsakForManuell":[],
        "supplement":{"eps":[],"bruker":{"eksterneVedtaksdata":[{"ytelseskomponenttype":"UT_ORDINER","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","nettoYtelse":"10500","nettoYtelseskomponent":"10500","sakstype":"ALDER","tilOgMed":null,"bruttoYtelse":"10500","fnr":"$expectedFnr","bruttoYtelseskomponent":"10500"},{"ytelseskomponenttype":"UT_ORDINER","vedtakstype":"ENDRING","fraOgMed":"01.04.2021","nettoYtelse":"10000","nettoYtelseskomponent":"10000","sakstype":"ALDER","tilOgMed":"30.04.2021","bruttoYtelse":"10000","fnr":"$expectedFnr","bruttoYtelseskomponent":"10000"}],"fnr":"$expectedFnr","fradragsperioder":[{"fradragstype":"Alderspensjon","vedtaksperiodeRegulering":[{"beløp":10500,"periode":{"fraOgMed":"2021-05-01","tilOgMed":null}}],"vedtaksperiodeEndring":{"beløp":10000,"periode":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"}}}]}},
        "grunnlagsdataOgVilkårsvurderinger":{"familiegjenforening":null,"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"personligOppmøte":{"vurderinger":[{"vurdering":"MøttPersonlig","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"formue":{"vurderinger":[{"grunnlag":{"søkersFormue":{"innskudd":0,"verdipapir":0,"pengerSkyldt":0,"verdiKjøretøy":0,"verdiIkkePrimærbolig":0,"depositumskonto":0,"kontanter":0,"verdiEiendommer":0},"epsFormue":null},"opprettet":"2021-01-01T01:02:03.456789Z","id":"4eee850d-ea27-4aa7-b605-418012011900","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"formuegrenser":[{"beløp":53200,"gyldigFra":"2021-05-01"},{"beløp":50676,"gyldigFra":"2020-05-01"}],"resultat":"VilkårOppfylt"},"fastOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"flyktning":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"utenlandsopphold":{"vurderinger":[{"status":"SkalHoldeSegINorge","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"status":"SkalHoldeSegINorge"},"lovligOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"opplysningsplikt":{"vurderinger":[{"beskrivelse":"TilstrekkeligDokumentasjon","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}]},"bosituasjon":[{"delerBolig":false,"sats":"HØY","fnr":null,"ektemakeEllerSamboerUførFlyktning":null,"type":"ENSLIG","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"uføre":{"vurderinger":[{"grunnlag":{"forventetInntekt":0,"opprettet":"2021-01-01T01:02:03.456789Z","uføregrad":100,"id":"3299b6f6-2322-45cc-9d75-9b7e45eb481d","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},"opprettet":"2021-01-01T01:02:03.456789Z","id":"2b887d13-7803-432b-bd9a-47d57e60ee75","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"pensjon":null,"institusjonsopphold":{"vurderingsperioder":[{"vurdering":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"}},
        "beregning":{"begrunnelse":null,"fraOgMed":"2021-05-01","fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"opprettet":"2021-05-21T01:02:03.456789Z","tilOgMed":"2021-12-31","id":"e16545e5-697b-425f-99b4-a1c014143053","månedsberegninger":[{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-05-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}}],"beløp":11489,"tilOgMed":"2021-05-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-06-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}}],"beløp":11489,"tilOgMed":"2021-06-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-07-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}}],"beløp":11489,"tilOgMed":"2021-07-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-08-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}}],"beløp":11489,"tilOgMed":"2021-08-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-09-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}}],"beløp":11489,"tilOgMed":"2021-09-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-10-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}}],"beløp":11489,"tilOgMed":"2021-10-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-11-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}}],"beløp":11489,"tilOgMed":"2021-11-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-12-01","epsInputFradrag":[],"fradrag":[{"beløp":10500,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}},{"beløp":0,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}}],"beløp":11489,"tilOgMed":"2021-12-31","sats":"HØY","epsFribeløp":0,"merknader":[]}]},
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

fun AppComponents.verifySupplement(expectedFnr: String) {
    this.databaseRepos.reguleringRepo.let {
        it.defaultSessionContext().let {
            it.withSession {
                """select * from reguleringssupplement""".hentListe(emptyMap(), it) {
                    it.string("supplement")
                }.let {
                    it.size shouldBe 2
                    // vi kjører først en vanlig automatisk regulering uten supplemenent. denne blir lagret tom i basen
                    it.first().shouldBeSimilarJsonTo("""[]""")
                    // ettersendelsen lagrer supplementet - med innholdet i forventer fra reguleringen sin json
                    //language=json
                    it.last().shouldBeSimilarJsonTo(
                        """[{
                              "fnr": "$expectedFnr","perType": [{"vedtak": [{"type": "regulering", "beløp": 10500, "fradrag": [{"beløp": 10500, "fraOgMed": "2021-05-01", "tilOgMed": null, "eksterndata": {"fnr": "$expectedFnr", "fraOgMed": "01.05.2021", "sakstype": "ALDER", "tilOgMed": null, "nettoYtelse": "10500", "vedtakstype": "REGULERING", "bruttoYtelse": "10500", "ytelseskomponenttype": "UT_ORDINER", "nettoYtelseskomponent": "10500", "bruttoYtelseskomponent": "10500"}, "vedtakstype": "Regulering"}], "periodeOptionalTilOgMed": {"fraOgMed": "2021-05-01", "tilOgMed": null}}, {"type": "endring", "beløp": 10000, "periode": {"fraOgMed": "2021-04-01", "tilOgMed": "2021-04-30"}, "fradrag": [{"beløp": 10000, "fraOgMed": "2021-04-01", "tilOgMed": "2021-04-30", "eksterndata": {"fnr": "$expectedFnr", "fraOgMed": "01.04.2021", "sakstype": "ALDER", "tilOgMed": "30.04.2021", "nettoYtelse": "10000", "vedtakstype": "ENDRING", "bruttoYtelse": "10000", "ytelseskomponenttype": "UT_ORDINER", "nettoYtelseskomponent": "10000", "bruttoYtelseskomponent": "10000"}, "vedtakstype": "Endring"}]}], "fradragskategori": "Alderspensjon"}]
                          }]
                        """.trimIndent(),
                    )
                }
            }
        }
    }
}
