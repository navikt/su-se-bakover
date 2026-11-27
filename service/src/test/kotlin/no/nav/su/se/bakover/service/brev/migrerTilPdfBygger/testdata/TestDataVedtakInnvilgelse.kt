package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.BrevPeriode
import dokument.domain.generer.bygger.FradragForBrev
import dokument.domain.generer.bygger.MånedsfradragForBrev
import dokument.domain.generer.bygger.SatsoversiktBrev
import dokument.domain.generer.bygger.harFradrag
import dokument.domain.generer.bygger.maler.InnvilgetSøknadsbehandlingPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt

val vedtakInnvilgelsePdf = InnvilgetSøknadsbehandlingPdfInnhold(
    personalia = PersonaliaPdfInnhold(
        dato = "01.01.2021",
        fødselsnummer = "12345678901",
        fornavn = "Planet",
        etternavn = "Planetus",
        saksnummer = 2021,
    ),
    fradato = "januar 2021",
    tildato = "desember 2021",
    søkedato = "november 2021",
    forventetInntektStørreEnn0 = true,
    harEktefelle = true,
    beregningsperioder = listOf(
        BeregningsperiodeBrev(
            periode = BrevPeriode(
                fraOgMed = "januar 2021",
                tilOgMed = "mai 2021",
            ),
            ytelsePerMåned = 12000,
            satsbeløpPerMåned = 20637,
            epsFribeløp = 12000,
            fradrag = FradragForBrev(
                bruker = listOf(
                    MånedsfradragForBrev(
                        type = "Pensjon",
                        beløp = 3000,
                        utenlandskInntekt = null,
                    ),
                    MånedsfradragForBrev(
                        type = "Arbeidsinntekt",
                        beløp = 1500,
                        utenlandskInntekt = null,
                    ),
                    MånedsfradragForBrev(
                        type = "Bidrag etter ekteskapsloven — fra utlandet",
                        beløp = 50,
                        utenlandskInntekt = UtenlandskInntekt.create(
                            beløpIUtenlandskValuta = 100,
                            valuta = "SEK",
                            kurs = 0.5,
                        ),
                    ),
                    MånedsfradragForBrev(
                        type = "Arbeidsinntekt — fra utlandet",
                        beløp = 50,
                        utenlandskInntekt = UtenlandskInntekt.create(
                            beløpIUtenlandskValuta = 100,
                            valuta = "SEK",
                            kurs = 0.5,
                        ),
                    ),
                    MånedsfradragForBrev(
                        type = "UtenlandskInntekt",
                        beløp = 1000,
                        utenlandskInntekt = null,
                    ),
                ),
                eps = FradragForBrev.Eps(
                    fradrag = emptyList(),
                    harFradragMedSumSomErLavereEnnFribeløp = true,
                ),
            ),
            sats = "høy",
        ),
        BeregningsperiodeBrev(
            periode = BrevPeriode(
                fraOgMed = "juni 2021",
                tilOgMed = "desember 2021",
            ),
            ytelsePerMåned = 20500,
            satsbeløpPerMåned = 20637,
            epsFribeløp = 15000,
            fradrag = FradragForBrev(
                bruker = listOf(
                    MånedsfradragForBrev(
                        type = "Arbeidsinntekt",
                        beløp = 1500,
                        utenlandskInntekt = null,
                    ),
                ),
                // Mangler i json-en, men er påkrevd i FradragForBrev.
                eps = FradragForBrev.Eps(
                    fradrag = emptyList(),
                    harFradragMedSumSomErLavereEnnFribeløp = false,
                ),
            ),
            sats = "høy",
        ),
    ),
    saksbehandlerNavn = "Sak Saksbehandlersen",
    attestantNavn = "Attestant Attestantsen",
    fritekst = "Dette er noe fritekst som saksbehandler har lagt inn.",
    satsoversikt = SatsoversiktBrev(
        perioder = listOf(
            SatsoversiktBrev.Satsperiode(
                fraOgMed = "01.01.2020",
                tilOgMed = "31.04.2020",
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
    sakstype = Sakstype.UFØRE,
    harFradrag = true,
)
