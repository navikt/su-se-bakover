package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.BrevPeriode
import no.nav.su.se.bakover.domain.brev.beregning.Fradrag

/**
 * TODO John Andre Hestad: Det skal være mulig å bygge en testJar og importere denne fra gradle.
 */
object VedtakInnholdTestdataBuilder {

    fun build(): BrevInnhold = BrevInnhold.InnvilgetVedtak(
        personalia = BrevInnhold.Personalia(
            dato = "01.01.2020",
            fødselsnummer = Fnr("12345678901"),
            fornavn = "Tore",
            etternavn = "Strømøy",
            saksnummer = 2121,
        ),
        fradato = "01.01.2020",
        tildato = "01.01.2020",
        sats = "100",
        satsGrunn = Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN,
        satsBeløp = 100,
        satsGjeldendeFraDato = "01.05.2020",
        forventetInntektStørreEnn0 = true,
        harEktefelle = true,
        beregningsperioder = listOf(
            Beregningsperiode(
                periode = BrevPeriode("1 januar", "31 desember"),
                ytelsePerMåned = 0,
                satsbeløpPerMåned = 0,
                epsFribeløp = 0,
                fradrag = Fradrag(emptyList(), Fradrag.Eps(emptyList(), true)),
            ),
        ),
        saksbehandlerNavn = "Nei Josbø",
        attestantNavn = "Morge R. R. Gartin",
        fritekst = "Dette er fritekst",
    )
}
