package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.NonEmptyList
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagForÅr
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagForÅrOgStadie
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Skatteoppslag
import java.time.Year

class SkatteClientStub() : Skatteoppslag {
    override fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        år: Year,
    ): SamletSkattegrunnlagForÅr {
        return samletYear(år)
//        return SamletSkattegrunnlagForÅr(
//            utkast = SamletSkattegrunnlagForStadie.Utkast(
//                oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
//                inntektsår = år,
//            ),
//            oppgjør = SamletSkattegrunnlagForStadie.Oppgjør(
//                oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
//                inntektsår = år,
//            ),
//            år = år,
//        )
    }

    override fun hentSamletSkattegrunnlagForÅrsperiode(
        fnr: Fnr,
        yearRange: YearRange,
    ): NonEmptyList<SamletSkattegrunnlagForÅr> {
        return yearRange.map { samletYear(it) }.toNonEmptyList()
//        return yearRange.map {
//            SamletSkattegrunnlagForÅr(
//                utkast = SamletSkattegrunnlagForStadie.Utkast(
//                    oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
//                    inntektsår = it,
//                ),
//                oppgjør = SamletSkattegrunnlagForStadie.Oppgjør(
//                    oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
//                    inntektsår = it,
//                ),
//                år = it,
//            )
//        }
    }

    private fun samletYear(år: Year) = SamletSkattegrunnlagForÅr(
        år = år,
        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
            oppslag = årsgrunnlag().right(),
            inntektsår = år,
        ),
        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
            oppslag = årsgrunnlag().right(),
            inntektsår = år,
        ),
    )

    private fun årsgrunnlag() = Skattegrunnlag.SkattegrunnlagForÅr(
        oppgjørsdato = null,
        inntekt = listOf(
            Skattegrunnlag.Grunnlag.Inntekt(navn = "alminneligInntektFoerSaerfradrag", beløp = "1000"),
        ),
        formue = listOf(
            Skattegrunnlag.Grunnlag.Formue(navn = "bruttoformue", beløp = "1238"),
            Skattegrunnlag.Grunnlag.Formue(
                navn = "formuesverdiForKjoeretoey",
                beløp = "20000",
                spesifisering = listOf(
                    Skattegrunnlag.Spesifisering.Kjøretøy(
                        beløp = "15000",
                        registreringsnummer = "AB12345",
                        fabrikatnavn = "Troll",
                        årForFørstegangsregistrering = "1957",
                        formuesverdi = "15000",
                    ),
                    Skattegrunnlag.Spesifisering.Kjøretøy(
                        beløp = "5000",
                        registreringsnummer = "BC67890",
                        fabrikatnavn = "Think",
                        årForFørstegangsregistrering = "2003",
                        formuesverdi = "5000",
                    ),
                ),
            ),
        ),
        formuesfradrag = listOf(
            Skattegrunnlag.Grunnlag.Formuesfradrag(navn = "samletAnnenGjeld", beløp = "6000"),
        ),
        inntektsfradrag = listOf(
            Skattegrunnlag.Grunnlag.Inntektsfradrag(navn = "fradragForFagforeningskontingent", beløp = "4000"),
        ),
    )
}
