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
        månedsbeløp = 100,
        fradato = "01.01.2020",
        tildato = "01.01.2020",
        sats = "100",
        satsbeløp = 100,
        satsGrunn = Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN,
        redusertStønadStatus = true,
        harEktefelle = true,
        fradrag = emptyList(),
        fradragSum = 0,
    )
}
