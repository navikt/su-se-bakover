package no.nav.su.se.bakover.client.pdf

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.brev.PdfTemplate
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.time.LocalDate

sealed interface PdfDokument {
    val template: PdfTemplate

    fun toJson(): String = objectMapper.writeValueAsString(this)
}


data class SkattePdf private constructor(
    val hentetDato: LocalDate,

) : PdfDokument {
    override val template: PdfTemplate = PdfTemplate.Skattemelding

}

private data class SkattemeldingsPdf(
    val skattegrunnlagSøker: Skattegrunnlag,
    val skattegrunnlagEps: Skattegrunnlag?
)


/*
{
  "hentetDato": "02.05.2020",
  "fnr": "12345678901",
  "navn": {
    "fornavn": "Tore",
    "mellomnavn": "Johnas",
    "etternavn": "Strømøy"
  },
  "saksnummer": "2021",
  "søknadsbehandlingId": "6c4c1d47-a9f5-4b28-a9fb-336356992477",
  "årsgrunnlag": [
    {
      "år": "2021",
      "stadie": "Oppgjør",
      "oppgjørsdato": "01.05.2020",
      "kjøretøy": [
        {
          "beløp": "15 000",
          "registreringsnummer": "AB12345",
          "fabrikatnavn": "Volvo",
          "årForFørstegangsregistrering": "2 000",
          "formuesverdi": "18 000",
          "antattVerdiSomNytt": null,
          "antattMarkedsverdi": null
        },
        {
          "beløp": "5 000",
          "registreringsnummer": "CD54321",
          "fabrikatnavn": "Saab",
          "årForFørstegangsregistrering": "1992",
          "formuesverdi": "3 000",
          "antattVerdiSomNytt": null,
          "antattMarkedsverdi": null
        }
      ],
      "formue": [
        {
          "tekniskNavn": "formuesverdiForKjoeretoey",
          "beløp": "15 000"
        },
        {
          "tekniskNavn": "formuesverdiForTomt",
          "beløp": "150 000"
        },
        {
          "tekniskNavn": "formuesverdiForGaardsbruk",
          "beløp": "5 000"
        }
      ]
    },
    {
      "år": "2022",
      "stadie": "Utkast",
      "oppgjørsdato": null,
      "inntekt": [
        {
          "tekniskNavn": "utbytteFraAksje",
          "beløp": "15 000"
        },
        {
          "tekniskNavn": "utbytteFraVerdipapirfond",
          "beløp": "150 000"
        },
        {
          "tekniskNavn": "supplerendeStoenad",
          "beløp": "200 000"
        }
      ],
      "annet": [
        {
          "tekniskNavn": "gjeldsreduksjonForFastEiendomIUtlandetUnntattBeskatningINorgeEtterSkatteavtale",
          "beløp": "15 000",
          "kategori": "Annet"
        },
        {
          "tekniskNavn": "gjeldsreduksjonForFastEiendomUtenforSvalbardUnntattBeskatningPaaSvalbard",
          "beløp": "150 000"
        },
        {
          "tekniskNavn": "gjeldsrentereduksjonForFormueIUtlandetUnntattBeskatningINorgeEtterSkatteavtale",
          "beløp": "200 000"
        }
      ]
    }
  ]
}
 */
