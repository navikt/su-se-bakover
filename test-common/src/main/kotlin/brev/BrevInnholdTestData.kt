package no.nav.su.se.bakover.test.brev

import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.BrevPeriode
import dokument.domain.generer.bygger.FradragForBrev
import dokument.domain.generer.bygger.SatsoversiktBrev
import dokument.domain.generer.bygger.harFradrag
import dokument.domain.generer.bygger.maler.AvslagSøknadsbehandlingPdfInnhold
import dokument.domain.generer.bygger.maler.InnvilgetSøknadsbehandlingPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.brev.jsonRequest.toBrev
import no.nav.su.se.bakover.domain.brev.søknad.lukk.trukket.TrukketSøknadPdfInnhold
import vilkår.common.domain.Avslagsgrunn
import vilkår.common.domain.getDistinkteParagrafer

fun pdfInnholdInnvilgetVedtak(
    beregningsperioder: List<BeregningsperiodeBrev> = listOf(
        BeregningsperiodeBrev(
            periode = BrevPeriode("1 januar", "31 desember"),
            ytelsePerMåned = 0,
            satsbeløpPerMåned = 0,
            epsFribeløp = 0,
            fradrag = FradragForBrev(emptyList(), FradragForBrev.Eps(emptyList(), true)),
            sats = "høy",
        ),
    ),
): PdfInnhold = InnvilgetSøknadsbehandlingPdfInnhold(
    personalia = pdfInnholdPersonalia(),
    fradato = "01.01.2020",
    tildato = "01.01.2020",
    søkedato = "01.12.2019",
    forventetInntektStørreEnn0 = true,
    harEktefelle = true,
    beregningsperioder = beregningsperioder,
    saksbehandlerNavn = "Nei Josbø",
    attestantNavn = "Morge R. R. Gartin",
    fritekst = "Dette er fritekst",
    satsoversikt = SatsoversiktBrev(
        perioder = listOf(
            SatsoversiktBrev.Satsperiode(
                fraOgMed = "01.01.2020",
                tilOgMed = "31.01.2020",
                sats = "høy",
                satsBeløp = 1000,
                satsGrunn = "ENSLIG",
            ),
        ),
    ),
    sakstype = Sakstype.UFØRE,
    harFradrag = beregningsperioder.harFradrag(),
)

fun pdfInnholdAvslag(
    avslagsgrunner: List<Avslagsgrunn> = listOf(Avslagsgrunn.FLYKTNING),
) = AvslagSøknadsbehandlingPdfInnhold(
    personalia = pdfInnholdPersonalia(),
    avslagsgrunner = avslagsgrunner.map { it.toBrev() },
    avslagsparagrafer = avslagsgrunner.getDistinkteParagrafer(),
    harFlereAvslagsgrunner = avslagsgrunner.size > 1,
    harEktefelle = false,
    halvGrunnbeløp = 10,
    beregningsperioder = emptyList(),
    saksbehandlerNavn = "Sak Sakesen",
    attestantNavn = "Att Attestantsen",
    fritekst = "Fritekst til brevet",
    forventetInntektStørreEnn0 = false,
    formueVerdier = null,
    satsoversikt = SatsoversiktBrev(
        perioder = listOf(
            SatsoversiktBrev.Satsperiode(
                fraOgMed = "01.01.2020",
                tilOgMed = "31.01.2020",
                sats = "høy",
                satsBeløp = 1000,
                satsGrunn = "ENSLIG",
            ),
        ),
    ),
    sakstype = Sakstype.UFØRE,
)

fun pdfInnholdTrukketSøknad() = TrukketSøknadPdfInnhold(
    sakstype = Sakstype.UFØRE,
    personalia = pdfInnholdPersonalia(),
    datoSøknadOpprettet = "01.01.2020",
    trukketDato = "01.02.2020",
    saksbehandlerNavn = "saksbehandler",
)

fun pdfInnholdPersonalia() = PersonaliaPdfInnhold(
    dato = "01.01.2020",
    fødselsnummer = "12345678901",
    fornavn = "Tore",
    etternavn = "Strømøy",
    saksnummer = 2021,
)
