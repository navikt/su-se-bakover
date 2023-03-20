package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedStadie
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedYear
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Skatteoppslag
import no.nav.su.se.bakover.domain.skatt.SkatteoppslagFeil
import no.nav.su.se.bakover.domain.skatt.Stadie
import java.time.Clock
import java.time.Year

class SkatteClientStub(
    private val clock: Clock,
) : Skatteoppslag {
    override fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        år: Year,
    ): Either<SkatteoppslagFeil, SamletSkattegrunnlagResponseMedYear> {
        return samletYear(år).right()
    }

    override fun hentSamletSkattegrunnlagForÅrsperiode(
        fnr: Fnr,
        yearRange: YearRange,
    ): Either<SkatteoppslagFeil, List<SamletSkattegrunnlagResponseMedYear>> {
        return yearRange.map { samletYear(it) }.right()
    }

    private fun samletYear(år: Year) = SamletSkattegrunnlagResponseMedYear(
        skatteResponser = listOf(
            SamletSkattegrunnlagResponseMedStadie(
                stadie = Stadie.FASTSATT, oppslag = årsgrunnlag(år, Stadie.FASTSATT).right(),
            ),
            SamletSkattegrunnlagResponseMedStadie(
                stadie = Stadie.OPPGJØR, oppslag = årsgrunnlag(år, Stadie.OPPGJØR).right(),
            ),
            SamletSkattegrunnlagResponseMedStadie(
                stadie = Stadie.UTKAST, oppslag = årsgrunnlag(år, Stadie.UTKAST).right(),
            ),
        ),
        år = år,
    )

    private fun årsgrunnlag(
        inntektsÅr: Year,
        stadie: Stadie,
    ) = Skattegrunnlag.Årsgrunnlag(
        stadie = stadie,
        inntektsår = inntektsÅr,
        skatteoppgjørsdato = null,
        grunnlag = Skattegrunnlag.Grunnlagsliste(
            inntekt = listOf(
                Skattegrunnlag.Grunnlag.Inntekt(
                    navn = "alminneligInntektFoerSaerfradrag",
                    beløp = "1000",
                ),
            ),
            formue = listOf(
                Skattegrunnlag.Grunnlag.Formue(
                    navn = "bruttoformue",
                    beløp = "1238",
                ),
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
                Skattegrunnlag.Grunnlag.Formuesfradrag(
                    navn = "samletAnnenGjeld",
                    beløp = "6000",
                ),
            ),
            inntektsfradrag = listOf(
                Skattegrunnlag.Grunnlag.Inntektsfradrag(
                    navn = "fradragForFagforeningskontingent",
                    beløp = "4000",
                ),
            ),
        ),
    )


//    override fun hentSamletSkattegrunnlag(
//        fnr: Fnr,
//        inntektsÅr: YearRange,
//    ): Either<SkatteoppslagFeil, Skattegrunnlag> {
//        TODO("")
//    }
}
