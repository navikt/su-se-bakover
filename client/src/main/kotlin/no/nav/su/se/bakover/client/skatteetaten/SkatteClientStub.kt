package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Stadie
import java.time.Clock
import java.time.Year

class SkatteClientStub(
    private val clock: Clock,
) : Skatteoppslag {
    override fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        inntektsÅr: Year,
    ): List<SamletSkattegrunnlagResponseMedStadie> {
        return listOf(
            SamletSkattegrunnlagResponseMedStadie(
                stadie = Stadie.FASTSATT,
                oppslag = Skattegrunnlag(
                    fnr = Fnr(fnr = "04900148157"),
                    hentetTidspunkt = Tidspunkt.now(clock),
                    stadie = Stadie.FASTSATT,
                    årsgrunnlag = Skattegrunnlag.Årsgrunnlag(
                        inntektsår = Year.of(2021),
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
                    ),
                ).right(),
            ),
        )
    }

//    override fun hentSamletSkattegrunnlag(
//        fnr: Fnr,
//        inntektsÅr: YearRange,
//    ): Either<SkatteoppslagFeil, Skattegrunnlag> {
//        TODO("")
//    }
}
