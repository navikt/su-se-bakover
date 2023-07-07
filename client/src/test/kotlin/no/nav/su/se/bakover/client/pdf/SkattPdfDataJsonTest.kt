package no.nav.su.se.bakover.client.pdf

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.pdf.SamletÅrsgrunnlagPdfJson.Companion.tilPdfJson
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.extensions.trimWhitespace
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.skatt.nySamletSkattegrunnlagForÅrOgStadieOppgjør
import no.nav.su.se.bakover.test.skatt.nySamletSkattegrunnlagForÅrOgStadieOppgjørMedFeilIÅrsgrunnlag
import org.junit.jupiter.api.Test

internal class SkattPdfDataJsonTest {

    private val harIkkeSkattegrunnlag = SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie(
        år = 2021,
        stadie = ÅrsgrunnlagStadie.Oppgjør,
        grunn = SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie.HarIkkeSkattegrunnlagFordi.FINNES_IKKE,
    )
    private val harSkattegrunnlag = SamletÅrsgrunnlagPdfJson.HarSkattegrunnlagForÅrOgStadie(
        år = 2021,
        stadie = ÅrsgrunnlagStadie.Oppgjør,
        oppgjørsdato = null,
        formue = listOf(
            SkattegrunnlagPdfJson.Formue("bruttoformue", "1238"),
            SkattegrunnlagPdfJson.Formue(
                tekniskNavn = "formuesverdiForKjoeretoey",
                beløp = "20000",
            ),
        ),
        inntekt = listOf(SkattegrunnlagPdfJson.Inntekt("alminneligInntektFoerSaerfradrag", "1000")),
        inntektsfradrag = listOf(SkattegrunnlagPdfJson.Inntektsfradrag("fradragForFagforeningskontingent", "4000")),
        formuesfradrag = listOf(SkattegrunnlagPdfJson.Formuesfradrag("samletAnnenGjeld", "6000")),
        verdsettingsrabattSomGirGjeldsreduksjon = listOf(
            SkattegrunnlagPdfJson.VerdsettingsrabattSomGirGjeldsreduksjon(
                "fradragForFagforeningskontingent",
                "4000",
            ),
        ),
        oppjusteringAvEierinntekter = listOf(
            SkattegrunnlagPdfJson.OppjusteringAvEierinntekter("fradragForFagforeningskontingent", "4000"),
        ),
        manglerKategori = listOf(SkattegrunnlagPdfJson.ManglerKategori("fradragForFagforeningskontingent", "4000")),
        annet = listOf(SkattegrunnlagPdfJson.Annet("fradragForFagforeningskontingent", "4000")),
        kjøretøy = listOf(
            SpesifiseringPdfJson.KjøretøyJson("15000", "AB12345", "Troll", "1957", "15000", null, null),
            SpesifiseringPdfJson.KjøretøyJson("5000", "BC67890", "Think", "2003", "5000", null, null),
        ),
    )

    @Test
    fun `mapper årsgrunnlag`() {
        nySamletSkattegrunnlagForÅrOgStadieOppgjør().tilPdfJson() shouldBe harSkattegrunnlag
    }

    @Test
    fun `serialiserer og deserialiserer HarSkattegrunnlagForÅrOgStadie`() {
        val harÅrsgrunnlagJson = serialize(harSkattegrunnlag)

        harÅrsgrunnlagJson shouldBe
            //language=json
            """{
                "type":"HarSkattegrunnlag",
                "år":2021,
                "stadie":"Oppgjør",
                "oppgjørsdato":null,
                "formue":[
                  {"tekniskNavn":"bruttoformue","beløp":"1238","spesifisering":[]},
                  {"tekniskNavn":"formuesverdiForKjoeretoey","beløp":"20000", "spesifisering":[]}
                ],
                "inntekt":[{"tekniskNavn":"alminneligInntektFoerSaerfradrag","beløp":"1000","spesifisering":[]}],
                "inntektsfradrag":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                "formuesfradrag":[{"tekniskNavn":"samletAnnenGjeld","beløp":"6000","spesifisering":[]}],
                "verdsettingsrabattSomGirGjeldsreduksjon":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                "oppjusteringAvEierinntekter":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                "manglerKategori":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                "annet":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                "kjøretøy": [
                      {"beløp":"15000","registreringsnummer":"AB12345","fabrikatnavn":"Troll","årForFørstegangsregistrering":"1957","formuesverdi":"15000","antattVerdiSomNytt":null,"antattMarkedsverdi":null},
                      {"beløp":"5000","registreringsnummer":"BC67890","fabrikatnavn":"Think","årForFørstegangsregistrering":"2003","formuesverdi":"5000","antattVerdiSomNytt":null,"antattMarkedsverdi":null}
                    ]
            }
            """.trimIndent().trimWhitespace()

        deserialize<SamletÅrsgrunnlagPdfJson.HarSkattegrunnlagForÅrOgStadie>(harÅrsgrunnlagJson) shouldBe harSkattegrunnlag
    }

    @Test
    fun `kaster hvis oppslaget til skatt er left`() {
        nySamletSkattegrunnlagForÅrOgStadieOppgjørMedFeilIÅrsgrunnlag().tilPdfJson() shouldBe harIkkeSkattegrunnlag
    }

    @Test
    fun `serialiserer og deserialiserer HarIkkeSkattegrunnlagForÅrOgStadie`() {
        val harIkkeÅrsgrunnlagJson = serialize(harIkkeSkattegrunnlag)
        harIkkeÅrsgrunnlagJson shouldBe """{"type":"HarIkkeSkattegrunnlag","år":2021,"stadie":"Oppgjør","grunn":"FINNES_IKKE"}"""
        deserialize<SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie>(harIkkeÅrsgrunnlagJson) shouldBe harIkkeSkattegrunnlag
    }
}
