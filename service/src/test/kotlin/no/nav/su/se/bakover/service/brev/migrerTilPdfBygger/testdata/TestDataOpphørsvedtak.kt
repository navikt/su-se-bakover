package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.SatsoversiktBrev
import dokument.domain.generer.bygger.maler.OpphørsgrunnBrev
import dokument.domain.generer.bygger.maler.OpphørsvedtakPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype

val opphørsvedtakPdf = OpphørsvedtakPdfInnhold(
    sakstype = Sakstype.UFØRE,
    personalia = PersonaliaPdfInnhold(
        dato = "01.01.2020",
        fødselsnummer = "12345678901",
        fornavn = "Leeroy",
        etternavn = "Jenkins",
        saksnummer = 2021L,
    ),
    opphørsgrunner = listOf(OpphørsgrunnBrev.FOR_HØY_INNTEKT),
    avslagsparagrafer = listOf(5, 6, 7),
    harEktefelle = true,
    harFradrag = true,
    beregningsperioder = listOf(
        BeregningsperiodeBrev(
            periode = dokument.domain.generer.bygger.BrevPeriode(
                fraOgMed = "januar 2021",
                tilOgMed = "desember 2021",
            ),
            ytelsePerMåned = 12000,
            satsbeløpPerMåned = 20637,
            epsFribeløp = 12000,
            fradrag = dokument.domain.generer.bygger.FradragForBrev(
                bruker = listOf(
                    dokument.domain.generer.bygger.MånedsfradragForBrev(
                        utenlandskInntekt = null,
                        type = "Pensjon",
                        beløp = 3000,
                    ),
                    dokument.domain.generer.bygger.MånedsfradragForBrev(
                        utenlandskInntekt = null,
                        type = "Arbeidsinntekt",
                        beløp = 1500,
                    ),
                    dokument.domain.generer.bygger.MånedsfradragForBrev(
                        utenlandskInntekt = null,
                        type = "UtenlandskInntekt",
                        beløp = 1000,
                    ),
                ),
                eps = dokument.domain.generer.bygger.FradragForBrev.Eps(
                    fradrag = listOf(
                        dokument.domain.generer.bygger.MånedsfradragForBrev(
                            utenlandskInntekt = null,
                            type = "Arbeidsinntekt",
                            beløp = 1500,
                        ),
                    ),
                    harFradragMedSumSomErLavereEnnFribeløp = false,
                ),
            ),
            sats = "høy",
        ),
    ),

    saksbehandlerNavn = "Sak Saksbehandlersen",
    attestantNavn = "Attestant Attestantsen",
    fritekst = "Her er friteksten jeg la til.....",
    forventetInntektStørreEnn0 = false,
    halvGrunnbeløp = 50000,
    opphørsperiode = dokument.domain.generer.bygger.BrevPeriode(
        fraOgMed = "01.12.2022",
        tilOgMed = "31.12.2022",
    ),
    satsoversikt = SatsoversiktBrev(
        perioder = listOf(
            SatsoversiktBrev.Satsperiode(
                fraOgMed = "01.01.2020",
                tilOgMed = "30.04.2020",
                sats = "høy",
                satsBeløp = 20637,
                satsGrunn = "ENSLIG",
            ),
            SatsoversiktBrev.Satsperiode(
                fraOgMed = "01.05.2020",
                tilOgMed = "31.12.2020",
                sats = "ordinær",
                satsBeløp = 19250,
                satsGrunn = "DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67",
            ),
        ),
    ),
)
