package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.BrevPeriode
import dokument.domain.generer.bygger.FradragForBrev
import dokument.domain.generer.bygger.MånedsfradragForBrev
import dokument.domain.generer.bygger.SatsoversiktBrev
import dokument.domain.generer.bygger.maler.AvslagSøknadsbehandlingPdfInnhold
import dokument.domain.generer.bygger.maler.AvslagsgrunnBrev
import dokument.domain.generer.bygger.maler.FormueForBrev
import dokument.domain.generer.bygger.maler.FormueVerdierForBrev
import no.nav.su.se.bakover.common.domain.sak.Sakstype

val vedtakAvslagPdf: PdfInnhold = AvslagSøknadsbehandlingPdfInnhold(
    personalia = PersonaliaPdfInnhold(
        dato = "01.01.2020",
        fødselsnummer = "12345678901",
        fornavn = "Leeroy",
        etternavn = "Jenkins",
        saksnummer = 2021,
    ),
    avslagsgrunner = listOf(AvslagsgrunnBrev.FORMUE),
    avslagsparagrafer = listOf(1, 2, 5, 6, 7, 18),
    harFlereAvslagsgrunner = false,
    harEktefelle = true,
    halvGrunnbeløp = 10,
    beregningsperioder = listOf(
        BeregningsperiodeBrev(
            periode = BrevPeriode(
                fraOgMed = "januar 2021",
                tilOgMed = "desember 2021",
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
                        type = "UtenlandskInntekt",
                        beløp = 1000,
                        utenlandskInntekt = null,
                    ),
                ),
                eps = FradragForBrev.Eps(
                    fradrag = listOf(
                        MånedsfradragForBrev(
                            type = "Arbeidsinntekt",
                            beløp = 1500,
                            utenlandskInntekt = null,
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
    fritekst = "Dette er noe fritekst som saksbehandler har lagt inn.",
    forventetInntektStørreEnn0 = null,
    formueVerdier = FormueForBrev(
        søkersFormue = FormueVerdierForBrev(
            verdiSekundærBoliger = 10,
            verdiSekundærKjøretøyer = 10,
            pengerIBanken = 10,
            depositumskonto = 10,
            pengerIKontanter = 10,
            aksjerOgVerdiPapir = 10,
            pengerSøkerSkyldes = 10,
        ),
        epsFormue = FormueVerdierForBrev(
            verdiSekundærBoliger = 10,
            verdiSekundærKjøretøyer = 10,
            pengerIBanken = 10,
            depositumskonto = 10,
            pengerIKontanter = 10,
            aksjerOgVerdiPapir = 10,
            pengerSøkerSkyldes = 10,
        ),
        totalt = 120,
    ),
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
)
