package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.Satsgrunn

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
        beregning = BrevInnhold.Beregning(
            ytelsePerMåned = 0,
            satsbeløpPerMåned = 0.0,
            epsFribeløp = 0.0,
            fradrag = null
        )
    )
}
