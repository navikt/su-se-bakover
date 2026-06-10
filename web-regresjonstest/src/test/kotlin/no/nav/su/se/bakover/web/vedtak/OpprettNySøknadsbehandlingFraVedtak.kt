package no.nav.su.se.bakover.web.vedtak

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LovligOppholdVilkårStatus
import no.nav.su.se.bakover.domain.vilkår.uføre.UførevilkårStatus
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.test.jwt.DEFAULT_IDENT
import no.nav.su.se.bakover.vedtak.application.NySøknadCommandOmgjøring
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.routes.grunnlag.FormuevilkårStatus
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SøknadsbehandlingJson
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.VurderingInstitusjonsoppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.OpplysningspliktBeskrivelseJson
import java.util.UUID

internal fun AppComponents.opprettNySøknadsbehandlingFraVedtak(
    sakId: String,
    vedtakId: String,
    client: HttpClient,
    expectedSøknadId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    verifiserResponsVilkårAvslag: Boolean = true,
    verifiserResponsBeregningAvslag: Boolean = false,
    postbody: NySøknadCommandOmgjøring? = null,
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
    responseBodyJson: String,
    postbody: NySøknadCommandOmgjøring? = null,
) {
    val responseBody = deserialize<SøknadsbehandlingJson>(responseBodyJson)
    responseBody.status shouldBe "VILKÅRSVURDERT_AVSLAG"
    responseBody.beregning shouldBe null
    responseBody.simulering shouldBe null
    responseBody.sakId shouldBe UUID.fromString(expectedSakId)
    responseBody.saksbehandler shouldBe DEFAULT_IDENT
    responseBody.attesteringer shouldBe emptyList()
    responseBody.erLukket shouldBe false
    responseBody.sakstype shouldBe "uføre"
    responseBody.omgjøringsårsak shouldBe postbody?.omgjøringsårsak
    responseBody.omgjøringsgrunn shouldBe postbody?.omgjøringsgrunn
    responseBody.søknad.id shouldBe expectedSøknadId
    responseBody.søknad.sakId shouldBe expectedSakId
    responseBody.søknad.innsendtAv shouldBe DEFAULT_IDENT
    responseBody.søknad.lukket shouldBe null
    responseBody.brevvalg.valg shouldBe "SEND"
    responseBody.brevvalg.begrunnelse shouldBe null
    responseBody.brevvalg.bestemtAv shouldBe "Z990Lokal"
    responseBody.grunnlagsdataOgVilkårsvurderinger.uføre?.resultat shouldBe UførevilkårStatus.VilkårIkkeOppfylt
    responseBody.grunnlagsdataOgVilkårsvurderinger.flyktning?.resultat shouldBe "VilkårIkkeOppfylt"
    responseBody.grunnlagsdataOgVilkårsvurderinger.opplysningsplikt?.resultat shouldBe OpplysningspliktBeskrivelseJson.TilstrekkeligDokumentasjon
    responseBody.grunnlagsdataOgVilkårsvurderinger.fradrag shouldBe emptyList()
    responseBody.grunnlagsdataOgVilkårsvurderinger.bosituasjon shouldBe emptyList()
    responseBody.grunnlagsdataOgVilkårsvurderinger.lovligOpphold shouldBe null
    responseBody.grunnlagsdataOgVilkårsvurderinger.utenlandsopphold shouldBe null
    responseBody.grunnlagsdataOgVilkårsvurderinger.pensjon shouldBe null
    responseBody.grunnlagsdataOgVilkårsvurderinger.familiegjenforening shouldBe null
    responseBody.grunnlagsdataOgVilkårsvurderinger.fastOpphold shouldBe null
    responseBody.grunnlagsdataOgVilkårsvurderinger.personligOppmøte shouldBe null
    responseBody.grunnlagsdataOgVilkårsvurderinger.institusjonsopphold shouldBe null
}

// TODO: bruke strukturerte data
private fun verifiserOpprettetNySøknadsbehandlingFraVedtakAvslagBeregning(
    expectedSakId: String,
    expectedSøknadId: String,
    responseBodyJson: String,
    postbody: NySøknadCommandOmgjøring? = null,
) {
    val responseBody = deserialize<SøknadsbehandlingJson>(responseBodyJson)
    responseBody.status shouldBe "BEREGNET_AVSLAG"
    responseBody.simulering shouldBe null
    responseBody.sakId shouldBe UUID.fromString(expectedSakId)
    responseBody.saksbehandler shouldBe DEFAULT_IDENT
    responseBody.attesteringer shouldBe emptyList()
    responseBody.erLukket shouldBe false
    responseBody.sakstype shouldBe "uføre"
    responseBody.omgjøringsårsak shouldBe postbody?.omgjøringsårsak
    responseBody.omgjøringsgrunn shouldBe postbody?.omgjøringsgrunn
    responseBody.søknad.id shouldBe expectedSøknadId
    responseBody.søknad.sakId shouldBe expectedSakId
    responseBody.søknad.innsendtAv shouldBe DEFAULT_IDENT
    responseBody.søknad.lukket shouldBe null
    responseBody.brevvalg.valg shouldBe "SEND"
    responseBody.brevvalg.begrunnelse shouldBe null
    responseBody.brevvalg.bestemtAv shouldBe "srvsupstonad"

    // Beregning
    val beregning = responseBody.beregning!!
    beregning.fraOgMed shouldBe "2021-01-01"
    beregning.tilOgMed shouldBe "2021-12-31"
    beregning.månedsberegninger.size shouldBe 12
    beregning.begrunnelse shouldBe "Beregning er kjørt automatisk av Beregn.kt"

    // Grunnlagsdata og vilkårsvurderinger
    responseBody.grunnlagsdataOgVilkårsvurderinger.uføre?.resultat shouldBe UførevilkårStatus.VilkårOppfylt
    responseBody.grunnlagsdataOgVilkårsvurderinger.lovligOpphold?.resultat shouldBe LovligOppholdVilkårStatus.VilkårOppfylt
    responseBody.grunnlagsdataOgVilkårsvurderinger.fradrag.size shouldBe 1
    responseBody.grunnlagsdataOgVilkårsvurderinger.bosituasjon.size shouldBe 1
    responseBody.grunnlagsdataOgVilkårsvurderinger.formue?.resultat shouldBe FormuevilkårStatus.VilkårOppfylt
    responseBody.grunnlagsdataOgVilkårsvurderinger.utenlandsopphold?.status shouldBe UtenlandsoppholdStatus.SkalHoldeSegINorge
    responseBody.grunnlagsdataOgVilkårsvurderinger.flyktning?.resultat shouldBe "VilkårOppfylt"
    responseBody.grunnlagsdataOgVilkårsvurderinger.fastOpphold?.resultat shouldBe "VilkårOppfylt"
    responseBody.grunnlagsdataOgVilkårsvurderinger.personligOppmøte?.resultat shouldBe "VilkårOppfylt"
    responseBody.grunnlagsdataOgVilkårsvurderinger.institusjonsopphold?.resultat shouldBe VurderingInstitusjonsoppholdJson.VilkårOppfylt
    responseBody.grunnlagsdataOgVilkårsvurderinger.pensjon shouldBe null
    responseBody.grunnlagsdataOgVilkårsvurderinger.familiegjenforening shouldBe null
    responseBody.grunnlagsdataOgVilkårsvurderinger.opplysningsplikt?.resultat shouldBe OpplysningspliktBeskrivelseJson.TilstrekkeligDokumentasjon
}
