package no.nav.su.se.bakover.client.stubs.inntekt

import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.inntekt.InntektOppslag
import no.nav.su.se.bakover.domain.Fnr

object InntektOppslagStub : InntektOppslag {
    override fun inntekt(ident: Fnr, innloggetSaksbehandlerToken: String, fraOgMedDato: String, tilOgMedDato: String) =
        ClientResponse(
            200, //language=json
            """
            {
                "arbeidsInntektMaaned": [
                {
                    "aarMaaned": "$fraOgMedDato",
                    "arbeidsInntektInformasjon": {
                    "inntektListe": [
                    {
                        "inntektType": "LOENNSINNTEKT",
                        "beloep": 25000,
                        "fordel": "kontantytelse",
                        "inntektskilde": "A-ordningen",
                        "inntektsperiodetype": "Maaned",
                        "inntektsstatus": "LoependeInnrapportert",
                        "leveringstidspunkt": "$fraOgMedDato",
                        "utbetaltIMaaned": "$fraOgMedDato",
                        "opplysningspliktig": {
                        "identifikator": "873152362",
                        "aktoerType": "ORGANISASJON"
                    },
                        "virksomhet": {
                        "identifikator": "873152362",
                        "aktoerType": "ORGANISASJON"
                    },
                        "inntektsmottaker": {
                        "identifikator": "$ident",
                        "aktoerType": "NATURLIG_IDENT"
                    },
                        "inngaarIGrunnlagForTrekk": true,
                        "utloeserArbeidsgiveravgift": true,
                        "informasjonsstatus": "InngaarAlltid",
                        "beskrivelse": "fastloenn"
                    },
                    {
                        "inntektType": "LOENNSINNTEKT",
                        "beloep": 2000,
                        "fordel": "kontantytelse",
                        "inntektskilde": "A-ordningen",
                        "inntektsperiodetype": "Maaned",
                        "inntektsstatus": "LoependeInnrapportert",
                        "leveringstidspunkt": "$fraOgMedDato",
                        "utbetaltIMaaned": "$fraOgMedDato",
                        "opplysningspliktig": {
                        "identifikator": "873152362",
                        "aktoerType": "ORGANISASJON"
                    },
                        "virksomhet": {
                        "identifikator": "873152362",
                        "aktoerType": "ORGANISASJON"
                    },
                        "inntektsmottaker": {
                        "identifikator": "$ident",
                        "aktoerType": "NATURLIG_IDENT"
                    },
                        "inngaarIGrunnlagForTrekk": true,
                        "utloeserArbeidsgiveravgift": true,
                        "informasjonsstatus": "InngaarAlltid",
                        "beskrivelse": "kapitalInntekt"
                    },
                    {
                        "inntektType": "YTELSE_FRA_OFFENTLIGE",
                        "beloep": 5000,
                        "fordel": "kontantytelse",
                        "inntektskilde": "A-ordningen",
                        "inntektsperiodetype": "Maaned",
                        "inntektsstatus": "LoependeInnrapportert",
                        "leveringstidspunkt": "$fraOgMedDato",
                        "utbetaltIMaaned": "$fraOgMedDato",
                        "opplysningspliktig": {
                        "identifikator": "873152362",
                        "aktoerType": "ORGANISASJON"
                    },
                        "virksomhet": {
                        "identifikator": "873152362",
                        "aktoerType": "ORGANISASJON"
                    },
                        "inntektsmottaker": {
                        "identifikator": "$ident",
                        "aktoerType": "NATURLIG_IDENT"
                    },
                        "inngaarIGrunnlagForTrekk": true,
                        "utloeserArbeidsgiveravgift": true,
                        "informasjonsstatus": "InngaarAlltid",
                        "beskrivelse": "ufoeretrygd"
                    }
                    ]
                }
                }
                ],
                "ident": {
                "identifikator": "$ident",
                "aktoerType": "NATURLIG_IDENT"
            }
            }
            """
        )
}
