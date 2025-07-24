package no.nav.su.se.bakover.web.vedtak

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.vedtak.application.NySøknadCommand
import no.nav.su.se.bakover.web.komponenttest.AppComponents

internal fun AppComponents.opprettNySøknadsbehandlingFraVedtak(
    sakId: String,
    vedtakId: String,
    client: HttpClient,
    expectedSøknadId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    verifiserResponsVilkårAvslag: Boolean = true,
    verifiserResponsBeregningAvslag: Boolean = false,
    postbody: NySøknadCommand? = null,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/vedtak/$vedtakId/nySoknadsbehandling",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
            body = postbody?.let { serialize(it) },
        ).apply {
            withClue("Kunne opprette ny søknadsbehandling fra vedtak: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().let {
            if (verifiserResponsVilkårAvslag) {
                verifiserOpprettetNySøknadsbehandlingFraVedtakAvslagVilkår(sakId, expectedSøknadId, it, postbody)
            }
            if (verifiserResponsBeregningAvslag) {
                verifiserOpprettetNySøknadsbehandlingFraVedtakAvslagBeregning(sakId, expectedSøknadId, it, postbody)
            }
            it
        }
    }
}

private fun verifiserOpprettetNySøknadsbehandlingFraVedtakAvslagVilkår(
    expectedSakId: String,
    expectedSøknadId: String,
    actual: String,
    postbody: NySøknadCommand? = null,
) {
    //language=json
    val expected =
        """{
            "id":"ignored",
            "søknad":{
              "id":"$expectedSøknadId",
              "sakId":"$expectedSakId",
              "søknadInnhold":{"type":"uføre","uførevedtak":{"harUførevedtak":true},"flyktningsstatus":{"registrertFlyktning":true},"personopplysninger":{"fnr":"ignored"},"boforhold":{"borOgOppholderSegINorge":true,"delerBoligMedVoksne":true,"delerBoligMed":"VOKSNE_BARN","ektefellePartnerSamboer":null,"innlagtPåInstitusjon":{"datoForInnleggelse":"2020-01-01","datoForUtskrivelse":"2020-01-31","fortsattInnlagt":false},"borPåAdresse":null,"ingenAdresseGrunn":"HAR_IKKE_FAST_BOSTED"},"utenlandsopphold":{"registrertePerioder":[{"utreisedato":"2020-01-01","innreisedato":"2020-01-31"},{"utreisedato":"2020-02-01","innreisedato":"2020-02-05"}],"planlagtePerioder":[{"utreisedato":"2020-07-01","innreisedato":"2020-07-31"}]},"oppholdstillatelse":{"erNorskStatsborger":false,"harOppholdstillatelse":true,"typeOppholdstillatelse":"midlertidig","statsborgerskapAndreLand":false,"statsborgerskapAndreLandFritekst":null},"inntektOgPensjon":{"forventetInntekt":2500,"andreYtelserINav":"sosialstønad","andreYtelserINavBeløp":33,"søktAndreYtelserIkkeBehandletBegrunnelse":"uføre","trygdeytelserIUtlandet":[{"beløp":200,"type":"trygd","valuta":"En valuta"},{"beløp":500,"type":"Annen trygd","valuta":"En annen valuta"}],"pensjon":[{"ordning":"KLP","beløp":2000.0},{"ordning":"SPK","beløp":5000.0}]},"formue":{"eierBolig":true,"borIBolig":true,"verdiPåBolig":600000,"boligBrukesTil":"Mine barn bor der","depositumsBeløp":1000.0,"verdiPåEiendom":3,"eiendomBrukesTil":"","kjøretøy":[{"verdiPåKjøretøy":2500,"kjøretøyDeEier":"bil"}],"innskuddsBeløp":3500,"verdipapirBeløp":4500,"skylderNoenMegPengerBeløp":1200,"kontanterBeløp":1300},"forNav":{"type":"DigitalSøknad","harFullmektigEllerVerge":null},"ektefelle":{"formue":{"eierBolig":true,"borIBolig":false,"verdiPåBolig":0,"boligBrukesTil":"","depositumsBeløp":0,"verdiPåEiendom":0,"eiendomBrukesTil":"","kjøretøy":[],"innskuddsBeløp":0,"verdipapirBeløp":0,"skylderNoenMegPengerBeløp":0,"kontanterBeløp":0},"inntektOgPensjon":{"forventetInntekt":null,"andreYtelserINav":null,"andreYtelserINavBeløp":null,"søktAndreYtelserIkkeBehandletBegrunnelse":null,"trygdeytelserIUtlandet":null,"pensjon":null}}},
              "opprettet":"2021-01-01T01:02:06.456789Z",
              "lukket":null
            },
            "beregning":null,
            "status":"VILKÅRSVURDERT_AVSLAG",
            "simulering":null,
            "opprettet":"2021-01-01T01:02:28.456789Z",
            "attesteringer":[],
            "saksbehandler":"Z990Lokal",
            "fritekstTilBrev":"Send til attestering er kjørt automatisk av SendTilAttestering.kt",
            "sakId":"$expectedSakId",
            "stønadsperiode":{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}},
            "grunnlagsdataOgVilkårsvurderinger":{
              "uføre":{"vurderinger":[{"id":"f6d5d8e5-e695-4ffe-b816-11aebebf56c5","opprettet":"2021-01-01T01:02:16.456789Z","resultat":"VilkårIkkeOppfylt","grunnlag":null,"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårIkkeOppfylt"},
              "lovligOpphold":null,
              "fradrag":[],
              "bosituasjon":[],
              "formue":{"vurderinger":[],"resultat":null,"formuegrenser":[{"gyldigFra":"2020-05-01","beløp":50676}]},
              "utenlandsopphold":null,
              "opplysningsplikt":{"vurderinger":[{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"},"beskrivelse":"TilstrekkeligDokumentasjon"}]},
              "pensjon":null,
              "familiegjenforening":null,
              "flyktning":{"vurderinger":[{"resultat":"VilkårIkkeOppfylt","periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårIkkeOppfylt"},
              "fastOpphold":null,
              "personligOppmøte":null,
              "institusjonsopphold":null
            },
            "erLukket":false,
            "sakstype":"uføre",
            "aldersvurdering":{"harSaksbehandlerAvgjort":false,"maskinellVurderingsresultat":"RETT_PÅ_UFØRE"},
            "eksterneGrunnlag":{"skatt":null},
            "omgjøringsårsak": "${postbody?.omgjøringsårsak}",
            "omgjøringsgrunn": "${postbody?.omgjøringsgrunn}"
      }
        """.trimIndent()

    actual.shouldBeSimilarJsonTo(expected, "id", "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].id", "søknad.søknadInnhold.personopplysninger.fnr")
}

private fun verifiserOpprettetNySøknadsbehandlingFraVedtakAvslagBeregning(
    expectedSakId: String,
    expectedSøknadId: String,
    actual: String,
    postbody: NySøknadCommand? = null,
) {
    //language=json
    val expected =
        """{
            "id":"eaf7118a-cfdd-40da-9966-3a51244e8fdc",
            "søknad":{
              "id":"$expectedSøknadId",
              "sakId":"$expectedSakId",
              "søknadInnhold":{"type":"uføre","uførevedtak":{"harUførevedtak":true},"flyktningsstatus":{"registrertFlyktning":true},"personopplysninger":{"fnr":"02153759077"},"boforhold":{"borOgOppholderSegINorge":true,"delerBoligMedVoksne":true,"delerBoligMed":"VOKSNE_BARN","ektefellePartnerSamboer":null,"innlagtPåInstitusjon":{"datoForInnleggelse":"2020-01-01","datoForUtskrivelse":"2020-01-31","fortsattInnlagt":false},"borPåAdresse":null,"ingenAdresseGrunn":"HAR_IKKE_FAST_BOSTED"},"utenlandsopphold":{"registrertePerioder":[{"utreisedato":"2020-01-01","innreisedato":"2020-01-31"},{"utreisedato":"2020-02-01","innreisedato":"2020-02-05"}],"planlagtePerioder":[{"utreisedato":"2020-07-01","innreisedato":"2020-07-31"}]},"oppholdstillatelse":{"erNorskStatsborger":false,"harOppholdstillatelse":true,"typeOppholdstillatelse":"midlertidig","statsborgerskapAndreLand":false,"statsborgerskapAndreLandFritekst":null},"inntektOgPensjon":{"forventetInntekt":2500,"andreYtelserINav":"sosialstønad","andreYtelserINavBeløp":33,"søktAndreYtelserIkkeBehandletBegrunnelse":"uføre","trygdeytelserIUtlandet":[{"beløp":200,"type":"trygd","valuta":"En valuta"},{"beløp":500,"type":"Annen trygd","valuta":"En annen valuta"}],"pensjon":[{"ordning":"KLP","beløp":2000.0},{"ordning":"SPK","beløp":5000.0}]},"formue":{"eierBolig":true,"borIBolig":true,"verdiPåBolig":600000,"boligBrukesTil":"Mine barn bor der","depositumsBeløp":1000.0,"verdiPåEiendom":3,"eiendomBrukesTil":"","kjøretøy":[{"verdiPåKjøretøy":2500,"kjøretøyDeEier":"bil"}],"innskuddsBeløp":3500,"verdipapirBeløp":4500,"skylderNoenMegPengerBeløp":1200,"kontanterBeløp":1300},"forNav":{"type":"DigitalSøknad","harFullmektigEllerVerge":null},"ektefelle":{"formue":{"eierBolig":true,"borIBolig":false,"verdiPåBolig":0,"boligBrukesTil":"","depositumsBeløp":0,"verdiPåEiendom":0,"eiendomBrukesTil":"","kjøretøy":[],"innskuddsBeløp":0,"verdipapirBeløp":0,"skylderNoenMegPengerBeløp":0,"kontanterBeløp":0},"inntektOgPensjon":{"forventetInntekt":null,"andreYtelserINav":null,"andreYtelserINavBeløp":null,"søktAndreYtelserIkkeBehandletBegrunnelse":null,"trygdeytelserIUtlandet":null,"pensjon":null}}},
              "opprettet":"2021-01-01T01:02:06.456789Z",
              "lukket":null
            },
            "beregning":{
              "id":"ignored",
              "opprettet":"2021-01-01T01:02:30.456789Z",
              "fraOgMed":"2021-01-01",
              "tilOgMed":"2021-12-31",
              "månedsberegninger":[{"fraOgMed":"2021-01-01","tilOgMed":"2021-01-31","sats":"HØY","grunnbeløp":101351,"beløp":0,"fradrag":[{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-01-31"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-01-31"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],"satsbeløp":20946,"epsFribeløp":0.0,"epsInputFradrag":[],"merknader":[{"type":"BeløpErNull"}]},{"fraOgMed":"2021-02-01","tilOgMed":"2021-02-28","sats":"HØY","grunnbeløp":101351,"beløp":0,"fradrag":[{"periode":{"fraOgMed":"2021-02-01","tilOgMed":"2021-02-28"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-02-01","tilOgMed":"2021-02-28"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],"satsbeløp":20946,"epsFribeløp":0.0,"epsInputFradrag":[],"merknader":[{"type":"BeløpErNull"}]},{"fraOgMed":"2021-03-01","tilOgMed":"2021-03-31","sats":"HØY","grunnbeløp":101351,"beløp":0,"fradrag":[{"periode":{"fraOgMed":"2021-03-01","tilOgMed":"2021-03-31"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-03-01","tilOgMed":"2021-03-31"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],"satsbeløp":20946,"epsFribeløp":0.0,"epsInputFradrag":[],"merknader":[{"type":"BeløpErNull"}]},{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","sats":"HØY","grunnbeløp":101351,"beløp":0,"fradrag":[{"periode":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],"satsbeløp":20946,"epsFribeløp":0.0,"epsInputFradrag":[],"merknader":[{"type":"BeløpErNull"}]},{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31","sats":"HØY","grunnbeløp":101351,"beløp":0,"fradrag":[{"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],"satsbeløp":20946,"epsFribeløp":0.0,"epsInputFradrag":[],"merknader":[{"type":"BeløpErNull"}]},{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30","sats":"HØY","grunnbeløp":101351,"beløp":0,"fradrag":[{"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-06-30"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],"satsbeløp":20946,"epsFribeløp":0.0,"epsInputFradrag":[],"merknader":[{"type":"BeløpErNull"}]},{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31","sats":"HØY","grunnbeløp":101351,"beløp":0,"fradrag":[{"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],"satsbeløp":20946,"epsFribeløp":0.0,"epsInputFradrag":[],"merknader":[{"type":"BeløpErNull"}]},{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31","sats":"HØY","grunnbeløp":101351,"beløp":0,"fradrag":[{"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-08-01","tilOgMed":"2021-08-31"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],"satsbeløp":20946,"epsFribeløp":0.0,"epsInputFradrag":[],"merknader":[{"type":"BeløpErNull"}]},{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30","sats":"HØY","grunnbeløp":101351,"beløp":0,"fradrag":[{"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-09-01","tilOgMed":"2021-09-30"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],"satsbeløp":20946,"epsFribeløp":0.0,"epsInputFradrag":[],"merknader":[{"type":"BeløpErNull"}]},{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31","sats":"HØY","grunnbeløp":101351,"beløp":0,"fradrag":[{"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-10-01","tilOgMed":"2021-10-31"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],"satsbeløp":20946,"epsFribeløp":0.0,"epsInputFradrag":[],"merknader":[{"type":"BeløpErNull"}]},{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30","sats":"HØY","grunnbeløp":101351,"beløp":0,"fradrag":[{"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-11-01","tilOgMed":"2021-11-30"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],"satsbeløp":20946,"epsFribeløp":0.0,"epsInputFradrag":[],"merknader":[{"type":"BeløpErNull"}]},{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31","sats":"HØY","grunnbeløp":101351,"beløp":0,"fradrag":[{"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],"satsbeløp":20946,"epsFribeløp":0.0,"epsInputFradrag":[],"merknader":[{"type":"BeløpErNull"}]}],"fradrag":[{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"},{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"},"type":"ForventetInntekt","beskrivelse":null,"beløp":0.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],
              "begrunnelse":"Beregning er kjørt automatisk av Beregn.kt"
            },
            "status":"BEREGNET_AVSLAG",
            "simulering":null,
            "opprettet":"2021-01-01T01:02:41.456789Z",
            "attesteringer":[],
            "saksbehandler":"Z990Lokal",
            "fritekstTilBrev":"Send til attestering er kjørt automatisk av SendTilAttestering.kt",
            "sakId":"$expectedSakId",
            "stønadsperiode":{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}},
            "grunnlagsdataOgVilkårsvurderinger":{
              "uføre":{"vurderinger":[{"id":"46ce7524-d013-43e6-8e30-988baf05cbdc","opprettet":"2021-01-01T01:02:17.456789Z","resultat":"VilkårOppfylt","grunnlag":{"id":"7dce901f-d434-43da-883e-7afa53a6067b","opprettet":"2021-01-01T01:02:16.456789Z","periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"},"uføregrad":100,"forventetInntekt":0},"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
              "lovligOpphold":{"vurderinger":[{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"},"resultat":"VilkårOppfylt"}],"resultat":"VilkårOppfylt"},
              "fradrag":[{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"},"type":"PrivatPensjon","beskrivelse":null,"beløp":35000.0,"utenlandskInntekt":null,"tilhører":"BRUKER"}],
              "bosituasjon":[{"type":"ENSLIG","fnr":null,"delerBolig":false,"ektemakeEllerSamboerUførFlyktning":null,"sats":"HØY","periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}],
              "formue":{"vurderinger":[{"id":"ef87a525-4cb7-4614-849a-cf5db44b9fcc","opprettet":"2021-01-01T01:02:25.456789Z","resultat":"VilkårOppfylt","grunnlag":{"epsFormue":null,"søkersFormue":{"verdiIkkePrimærbolig":0,"verdiEiendommer":0,"verdiKjøretøy":0,"innskudd":0,"verdipapir":0,"pengerSkyldt":0,"kontanter":0,"depositumskonto":0}},"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt","formuegrenser":[{"gyldigFra":"2020-05-01","beløp":50676}]},
              "utenlandsopphold":{"vurderinger":[{"status":"SkalHoldeSegINorge","periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}],"status":"SkalHoldeSegINorge"},
              "opplysningsplikt":{"vurderinger":[{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"},"beskrivelse":"TilstrekkeligDokumentasjon"}]},
              "pensjon":null,
              "familiegjenforening":null,
              "flyktning":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
              "fastOpphold":{"vurderinger":[{"resultat":"VilkårOppfylt","periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
              "personligOppmøte":{"vurderinger":[{"resultat":"VilkårOppfylt","vurdering":"MøttPersonlig","periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}],"resultat":"VilkårOppfylt"},
              "institusjonsopphold":{"resultat":"VilkårOppfylt","vurderingsperioder":[{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"},"vurdering":"VilkårOppfylt"}]}
            },
            "erLukket":false,
            "sakstype":"uføre",
            "aldersvurdering":{"harSaksbehandlerAvgjort":false,
            "maskinellVurderingsresultat":"RETT_PÅ_UFØRE"},
            "eksterneGrunnlag":{"skatt":null},
            "omgjøringsårsak": "${postbody?.omgjøringsårsak}",
            "omgjøringsgrunn": "${postbody?.omgjøringsgrunn}"
        }
        """.trimIndent()

    actual.shouldBeSimilarJsonTo(expected, "id", "beregning.id", "grunnlagsdataOgVilkårsvurderinger.formue.vurderinger[*].id", "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].id", "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].grunnlag.id", "søknad.søknadInnhold.personopplysninger.fnr")
}
