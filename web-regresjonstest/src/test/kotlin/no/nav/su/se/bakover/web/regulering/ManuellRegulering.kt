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
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo

internal fun manuellRegulering(
    reguleringsId: String,
    oppdatertUføre: String,
    oppdatertFradrag: String,
    client: HttpClient,
) {
    return runBlocking {
        val correlationId = CorrelationId.generate()
        defaultRequest(
            HttpMethod.Post,
            "/reguleringer/manuell/$reguleringsId",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
            correlationId = correlationId.toString(),
        ) { setBody("""{"fradrag": $oppdatertFradrag, "uføre": $oppdatertUføre}""") }.apply {
            withClue("manuell reglering feilet: ${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
            }
        }
    }
}

fun verifyRegulering(
    actual: String,
    expectedId: String,
    expectedSakId: String,
    expectedFnr: String,
) {
    //language=json
    val expected = """{
        "id":"$expectedId",
        "sakId":"$expectedSakId",
        "fnr":"$expectedFnr",
        "opprettet":"2021-05-21T00:00:00Z",
        "reguleringstype":"MANUELL",
        "reguleringsstatus":"IVERKSATT",
        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
        "saksnummer":2021,
        "sakstype":"uføre",
        "årsakForManuell":[{"type": "BrukerManglerSupplement","begrunnelse":"Fradraget til BRUKER: Alderspensjon påvirkes av samme sats/G-verdi endring som SU. Vi mangler supplement for dette fradraget og derfor går det til manuell regulering.","fradragTilhører":"BRUKER","fradragskategori":"Alderspensjon"}],
        "supplement":{"eps":[],"bruker":null},
        "saksbehandler":"Z990Lokal",
        "erFerdigstilt":true,
        "grunnlagsdataOgVilkårsvurderinger":{"familiegjenforening":null,"fradrag":[{"beløp":10050,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"personligOppmøte":{"vurderinger":[{"vurdering":"MøttPersonlig","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"formue":{"vurderinger":[{"grunnlag":{"søkersFormue":{"innskudd":0,"verdipapir":0,"pengerSkyldt":0,"verdiKjøretøy":0,"verdiIkkePrimærbolig":0,"depositumskonto":0,"kontanter":0,"verdiEiendommer":0},"epsFormue":null},"opprettet":"2021-01-01T01:02:03.456789Z","id":"56040bfa-7ba3-4ca6-98d5-776ed5ec0067","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"formuegrenser":[{"beløp":53200,"gyldigFra":"2021-05-01"},{"beløp":50676,"gyldigFra":"2020-05-01"}],"resultat":"VilkårOppfylt"},"fastOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"flyktning":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"utenlandsopphold":{"vurderinger":[{"status":"SkalHoldeSegINorge","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"status":"SkalHoldeSegINorge"},"lovligOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"opplysningsplikt":{"vurderinger":[{"beskrivelse":"TilstrekkeligDokumentasjon","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}]},"bosituasjon":[{"delerBolig":false,"sats":"HØY","fnr":null,"ektemakeEllerSamboerUførFlyktning":null,"type":"ENSLIG","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"uføre":{"vurderinger":[{"grunnlag":{"forventetInntekt":25,"opprettet":"2021-05-21T00:00:00Z","uføregrad":100,"id":"7a7a5ee6-036d-49e9-bdc3-3e2be893de06","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},"opprettet":"2021-05-21T00:00:00Z","id":"1aa3c58d-450d-4c21-bd8d-e14c84e6a19c","resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},"pensjon":null,"institusjonsopphold":{"vurderingsperioder":[{"vurdering":"VilkårOppfylt","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"}},
        "beregning":{"begrunnelse":null,"fraOgMed":"2021-05-01","fradrag":[{"beløp":10050,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}},{"beløp":2.0833333333333335,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}],"opprettet":"2021-05-21T00:00:00Z","tilOgMed":"2021-12-31","id":"6f1304f0-a5d2-40f3-a6c1-cbfff5d8b786","månedsberegninger":[{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-05-01","epsInputFradrag":[],"fradrag":[{"beløp":10050,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}},{"beløp":2.0833333333333335,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"}}],"beløp":11937,"tilOgMed":"2021-05-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-06-01","epsInputFradrag":[],"fradrag":[{"beløp":10050,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}},{"beløp":2.0833333333333335,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"}}],"beløp":11937,"tilOgMed":"2021-06-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-07-01","epsInputFradrag":[],"fradrag":[{"beløp":10050,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}},{"beløp":2.0833333333333335,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}}],"beløp":11937,"tilOgMed":"2021-07-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-08-01","epsInputFradrag":[],"fradrag":[{"beløp":10050,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}},{"beløp":2.0833333333333335,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"}}],"beløp":11937,"tilOgMed":"2021-08-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-09-01","epsInputFradrag":[],"fradrag":[{"beløp":10050,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}},{"beløp":2.0833333333333335,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"}}],"beløp":11937,"tilOgMed":"2021-09-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-10-01","epsInputFradrag":[],"fradrag":[{"beløp":10050,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}},{"beløp":2.0833333333333335,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"}}],"beløp":11937,"tilOgMed":"2021-10-31","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-11-01","epsInputFradrag":[],"fradrag":[{"beløp":10050,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}},{"beløp":2.0833333333333335,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"}}],"beløp":11937,"tilOgMed":"2021-11-30","sats":"HØY","epsFribeløp":0,"merknader":[]},{"satsbeløp":21989,"grunnbeløp":106399,"fraOgMed":"2021-12-01","epsInputFradrag":[],"fradrag":[{"beløp":10050,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}},{"beløp":2.0833333333333335,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"ForventetInntekt","beskrivelse":null,"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}}],"beløp":11937,"tilOgMed":"2021-12-31","sats":"HØY","epsFribeløp":0,"merknader":[]}]},
        "simulering":{"totalOppsummering":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":95496,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":95496,"sumTilUtbetaling":95496,"sumReduksjonFeilkonto":0},"periodeOppsummering":[{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11937,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11937,"sumTilUtbetaling":11937,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11937,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11937,"sumTilUtbetaling":11937,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11937,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11937,"sumTilUtbetaling":11937,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11937,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11937,"sumTilUtbetaling":11937,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11937,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11937,"sumTilUtbetaling":11937,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11937,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11937,"sumTilUtbetaling":11937,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11937,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11937,"sumTilUtbetaling":11937,"sumReduksjonFeilkonto":0},{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31","sumEtterbetaling":0,"sumFeilutbetaling":0,"sumFramtidigUtbetaling":11937,"sumTidligereUtbetalt":0,"sumTotalUtbetaling":11937,"sumTilUtbetaling":11937,"sumReduksjonFeilkonto":0}]},
        "avsluttet":null
        }
    """.trimIndent()

    actual.shouldBeSimilarJsonTo(expected, "beregning.id", "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].id", "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].grunnlag.id", "grunnlagsdataOgVilkårsvurderinger.formue.vurderinger[*].id")
}
