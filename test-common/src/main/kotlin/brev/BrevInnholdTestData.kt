package no.nav.su.se.bakover.test.brev

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.BrevPeriode
import no.nav.su.se.bakover.domain.brev.beregning.Fradrag
import no.nav.su.se.bakover.domain.sak.Sakstype

fun brevInnholdInnvilgetVedtak(): BrevInnhold = BrevInnhold.InnvilgetVedtak(
    personalia = BrevInnhold.Personalia(
        dato = "01.01.2020",
        fødselsnummer = Fnr("12345678901"),
        fornavn = "Tore",
        etternavn = "Strømøy",
        saksnummer = 2121,
    ),
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
