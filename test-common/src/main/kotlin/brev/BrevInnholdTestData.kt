package no.nav.su.se.bakover.test.brev

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.BrevPeriode
import no.nav.su.se.bakover.domain.brev.beregning.Fradrag
import no.nav.su.se.bakover.domain.brev.jsonRequest.AvslagSøknadsbehandlingPdfInnhold
import no.nav.su.se.bakover.domain.brev.jsonRequest.InnvilgetSøknadsbehandlingPdfInnhold
import no.nav.su.se.bakover.domain.brev.søknad.lukk.trukket.TrukketSøknadPdfInnhold

fun pdfInnholdInnvilgetVedtak(): PdfInnhold = InnvilgetSøknadsbehandlingPdfInnhold(
    personalia = pdfInnholdPersonalia(),
    fradato = "01.01.2020",
    tildato = "01.01.2020",
    forventetInntektStørreEnn0 = true,
    harEktefelle = true,
    beregningsperioder = listOf(
        Beregningsperiode(
            periode = BrevPeriode("1 januar", "31 desember"),
            ytelsePerMåned = 0,
            satsbeløpPerMåned = 0,
            epsFribeløp = 0,
            fradrag = Fradrag(emptyList(), Fradrag.Eps(emptyList(), true)),
            sats = "høy",
        ),
    ),
    saksbehandlerNavn = "Nei Josbø",
    attestantNavn = "Morge R. R. Gartin",
    fritekst = "Dette er fritekst",
    satsoversikt = Satsoversikt(
        perioder = listOf(
            Satsoversikt.Satsperiode(
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

fun pdfInnholdAvslag() = AvslagSøknadsbehandlingPdfInnhold(
    personalia = pdfInnholdPersonalia(),
    avslagsgrunner = listOf(Avslagsgrunn.FLYKTNING),
    harEktefelle = false,
    halvGrunnbeløp = 10,
    beregningsperioder = emptyList(),
    saksbehandlerNavn = "Sak Sakesen",
    attestantNavn = "Att Attestantsen",
    fritekst = "Fritekst til brevet",
    forventetInntektStørreEnn0 = false,
    formueVerdier = null,
    satsoversikt = Satsoversikt(
        perioder = listOf(
            Satsoversikt.Satsperiode(
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
