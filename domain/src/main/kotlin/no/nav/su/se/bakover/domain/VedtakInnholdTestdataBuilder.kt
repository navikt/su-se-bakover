package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
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
        ),
        fradato = "01.01.2020",
        tildato = "01.01.2020",
        sats = "100",
        satsGrunn = Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN,
        harEktefelle = true,
        beregningsperioder = listOf(
            Beregningsperiode(
                periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                ytelsePerMåned = 0,
                satsbeløpPerMåned = 0,
                epsFribeløp = 0.0,
                fradrag = Fradrag(emptyList(), emptyList())
            )
        ),
        saksbehandlerNavn = "Nei Josbø",
        attestantNavn = "Morge R. R. Gartin"
    )
}
