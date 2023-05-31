package no.nav.su.se.bakover.domain.brev.skatt

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.skatt.SkattPdfData.ÅrsgrunnlagPdfJson.Companion.tilPdfJson
import no.nav.su.se.bakover.test.skatt.nySamletSkattegrunnlagForÅrOgStadieOppgjør
import no.nav.su.se.bakover.test.skatt.nySamletSkattegrunnlagForÅrOgStadieOppgjørMedFeilIÅrsgrunnlag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class SkattPdfJsonTest {
    @Test
    fun `mapper årsgrunnlag`() {
        val årsgrunnlag = nySamletSkattegrunnlagForÅrOgStadieOppgjør()

        årsgrunnlag.tilPdfJson(UUID.randomUUID()) shouldBe SkattPdfData.ÅrsgrunnlagPdfJson(
            år = 2021,
            stadie = SkattPdfData.ÅrsgrunnlagPdfJson.ÅrsgrunnlagStadie.Oppgjør,
            oppgjørsdato = null,
            formue = listOf(
                SkattegrunnlagPdfJson.Formue("bruttoformue", "1238"),
                SkattegrunnlagPdfJson.Formue(
                    tekniskNavn = "formuesverdiForKjoeretoey",
                    beløp = "20000",
                    spesifisering = listOf(
                        SpesifiseringPdfJson.KjøretøyJson("15000", "AB12345", "Troll", "1957", "15000", null, null),
                        SpesifiseringPdfJson.KjøretøyJson("5000", "BC67890", "Think", "2003", "5000", null, null),
                    ),
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
        )
    }

    @Test
    fun `kaster hvis oppslaget til skatt er left`() {
        assertThrows<IllegalStateException> {
            nySamletSkattegrunnlagForÅrOgStadieOppgjørMedFeilIÅrsgrunnlag().tilPdfJson(UUID.randomUUID())
        }
    }
}
