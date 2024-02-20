package no.nav.su.se.bakover.web.vedtak

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.web.komponenttest.AppComponents

internal fun AppComponents.opprettNySøknadsbehandlingFraVedtak(
    sakId: String,
    vedtakId: String,
    client: HttpClient,
    expectedSøknadId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    verifiserRespons: Boolean = true,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/vedtak/$vedtakId/nySoknadsbehandling",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ).apply {
            withClue("Kunne opprette ny søknadsbehandling fra vedtak: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().let {
            if (verifiserRespons) {
                verifiserOpprettetNySøknadsbehandlingFraVedtak(sakId, expectedSøknadId, it)
            }
            it
        }
    }
}

private fun verifiserOpprettetNySøknadsbehandlingFraVedtak(
    expectedSakId: String,
    expectedSøknadId: String,
    actual: String,
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
            "eksterneGrunnlag":{"skatt":null}
      }
        """.trimIndent()

    actual.shouldBeSimilarJsonTo(expected, "id", "grunnlagsdataOgVilkårsvurderinger.uføre.vurderinger[*].id", "søknad.søknadInnhold.personopplysninger.fnr")
}
