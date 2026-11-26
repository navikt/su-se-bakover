package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import arrow.core.nonEmptyListOf
import dokument.domain.generer.bygger.maler.skattegrunnlag.BehandlingstypeForSkattemelding
import dokument.domain.generer.bygger.maler.skattegrunnlag.SamletÅrsgrunnlagPdfJson
import dokument.domain.generer.bygger.maler.skattegrunnlag.SkattPdfDataJson
import dokument.domain.generer.bygger.maler.skattegrunnlag.SkattegrunnlagPdfJson
import dokument.domain.generer.bygger.maler.skattegrunnlag.SkattegrunnlagsPdfInnhold
import dokument.domain.generer.bygger.maler.skattegrunnlag.SpesifiseringPdfJson
import dokument.domain.generer.bygger.maler.skattegrunnlag.ÅrsgrunnlagStadie
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.toTidspunkt
import person.domain.Person
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

val skattegrunnlagPdf = SkattegrunnlagsPdfInnhold(
    saksnummer = "2021",
    behandlingstype = BehandlingstypeForSkattemelding.Søknadsbehandling,
    // Mangler i json-en, men er påkrevd.
    sakstype = Sakstype.UFØRE,
    behandlingsId = UUID.fromString("6c4c1d47-a9f5-4b28-a9fb-336356992477"),
    vedtaksId = UUID.fromString("2539cd71-0019-4989-899f-7821681a23d0"),
    hentet = LocalDateTime.of(2020, 5, 2, 12, 0).toTidspunkt(zoneIdOslo),
    opprettet = LocalDateTime.of(2020, 5, 22, 15, 46).toTidspunkt(zoneIdOslo),
    søkers = SkattPdfDataJson(
        fnr = Fnr("12345678901"),
        navn = Person.Navn(
            fornavn = "Leeroy",
            mellomnavn = "Johnas",
            etternavn = "Jenkins",
        ),
        årsgrunnlag = nonEmptyListOf(
            SamletÅrsgrunnlagPdfJson.HarSkattegrunnlagForÅrOgStadie(
                år = 2021,
                stadie = ÅrsgrunnlagStadie.Oppgjør,
                oppgjørsdato = LocalDate.of(2020, 5, 1),
                kjøretøy = listOf(
                    SpesifiseringPdfJson.KjøretøyJson(
                        beløp = "15 000",
                        registreringsnummer = "AB12345",
                        fabrikatnavn = "Volvo",
                        årForFørstegangsregistrering = "2 000",
                        formuesverdi = "18 000",
                        antattVerdiSomNytt = null,
                        antattMarkedsverdi = null,
                    ),
                    SpesifiseringPdfJson.KjøretøyJson(
                        beløp = "5 000",
                        registreringsnummer = "CD54321",
                        fabrikatnavn = "Saab",
                        årForFørstegangsregistrering = "1992",
                        formuesverdi = "3 000",
                        antattVerdiSomNytt = null,
                        antattMarkedsverdi = null,
                    ),
                ),
                formue = listOf(
                    SkattegrunnlagPdfJson.Formue(tekniskNavn = "formuesverdiForKjoeretoey", beløp = "15 000"),
                    SkattegrunnlagPdfJson.Formue(tekniskNavn = "formuesverdiForTomt", beløp = "150 000"),
                    SkattegrunnlagPdfJson.Formue(tekniskNavn = "formuesverdiForGaardsbruk", beløp = "5 000"),
                ),
            ),
            SamletÅrsgrunnlagPdfJson.HarSkattegrunnlagForÅrOgStadie(
                år = 2022,
                stadie = ÅrsgrunnlagStadie.Utkast,
                oppgjørsdato = null,
                inntekt = listOf(
                    SkattegrunnlagPdfJson.Inntekt(tekniskNavn = "utbytteFraAksje", beløp = "15 000"),
                    SkattegrunnlagPdfJson.Inntekt(tekniskNavn = "utbytteFraVerdipapirfond", beløp = "150 000"),
                    SkattegrunnlagPdfJson.Inntekt(tekniskNavn = "supplerendeStoenad", beløp = "200 000"),
                ),
                annet = listOf(
                    SkattegrunnlagPdfJson.Annet(
                        tekniskNavn = "gjeldsreduksjonForFastEiendomIUtlandetUnntattBeskatningINorgeEtterSkatteavtale",
                        beløp = "15 000",
                    ),
                    SkattegrunnlagPdfJson.Annet(
                        tekniskNavn = "gjeldsreduksjonForFastEiendomUtenforSvalbardUnntattBeskatningPaaSvalbard",
                        beløp = "150 000",
                    ),
                    SkattegrunnlagPdfJson.Annet(
                        tekniskNavn = "gjeldsrentereduksjonForFormueIUtlandetUnntattBeskatningINorgeEtterSkatteavtale",
                        beløp = "200 000",
                    ),
                ),
                // Mangler i json-en, men er påkrevd.
                kjøretøy = emptyList(),
            ),
        ),
    ),
    eps = SkattPdfDataJson(
        fnr = Fnr("12345678901"),
        navn = Person.Navn(
            fornavn = "Torill",
            mellomnavn = "Johnny",
            etternavn = "Elektrisitetsøy",
        ),
        årsgrunnlag = nonEmptyListOf(
            SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie(
                år = 2021,
                stadie = ÅrsgrunnlagStadie.Oppgjør,
                grunn = SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie.HarIkkeSkattegrunnlagFordi.FINNES_IKKE,
            ),
            SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie(
                år = 2022,
                stadie = ÅrsgrunnlagStadie.Utkast,
                grunn = SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie.HarIkkeSkattegrunnlagFordi.NETTVERKSFEIL,
            ),
        ),
    ),
    begrunnelse = "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum",
)
