package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.BrevPeriode
import dokument.domain.generer.bygger.FradragForBrev
import dokument.domain.generer.bygger.MånedsfradragForBrev
import dokument.domain.generer.bygger.SatsoversiktBrev
import dokument.domain.generer.bygger.maler.RevurderingAvInntektPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype

private const val BREV_DATO = "01.01.2020"
private const val HOY_SATS = "høy"
private const val HOY_SATSBELOP = 20637
private const val ARBEIDSINNTEKTSTYPE = "Arbeidsinntekt"
private const val ARBEIDSINNTEKT = 1500

// Syntetisk renderingstilfelle fra eksisterende testgrunnlag.
val revurderingAvInntektPdf = RevurderingAvInntektPdfInnhold(
    sakstype = Sakstype.UFØRE,
    personalia = PersonaliaPdfInnhold(
        dato = BREV_DATO,
        fødselsnummer = "12345678901",
        fornavn = "Leeroy",
        etternavn = "Jenkins",
        saksnummer = 2021L,
    ),
    fritekst = "Lorem Ipsum is simply dummy text of the printing and typesetting industry. " +
        "Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a " +
        "galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also " +
        "the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the " +
        "release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software " +
        "like Aldus PageMaker including versions of Lorem Ipsum",
    saksbehandlerNavn = "Malenia, blade of Miquella",
    attestantNavn = "James Eugene Raynor",
    beregningsperioder = listOf(
        BeregningsperiodeBrev(
            periode = BrevPeriode(
                fraOgMed = "januar 2020",
                tilOgMed = "desember 2020",
            ),
            ytelsePerMåned = 12000,
            satsbeløpPerMåned = HOY_SATSBELOP,
            epsFribeløp = 12000,
            fradrag = FradragForBrev(
                bruker = listOf(
                    MånedsfradragForBrev(type = "Pensjon", beløp = 3000, utenlandskInntekt = null),
                    MånedsfradragForBrev(type = ARBEIDSINNTEKTSTYPE, beløp = ARBEIDSINNTEKT, utenlandskInntekt = null),
                    MånedsfradragForBrev(type = "UtenlandskInntekt", beløp = 1000, utenlandskInntekt = null),
                ),
                eps = FradragForBrev.Eps(
                    fradrag = listOf(
                        MånedsfradragForBrev(type = ARBEIDSINNTEKTSTYPE, beløp = ARBEIDSINNTEKT, utenlandskInntekt = null),
                    ),
                    harFradragMedSumSomErLavereEnnFribeløp = false,
                ),
            ),
            sats = HOY_SATS,
        ),
    ),
    harEktefelle = true,
    harFradrag = true,
    forventetInntektStørreEnn0 = false,
    satsoversikt = SatsoversiktBrev(
        perioder = listOf(
            SatsoversiktBrev.Satsperiode(
                fraOgMed = BREV_DATO,
                tilOgMed = "30.04.2020",
                sats = HOY_SATS,
                satsBeløp = HOY_SATSBELOP,
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
