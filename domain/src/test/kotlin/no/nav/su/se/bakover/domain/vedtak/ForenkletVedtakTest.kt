package no.nav.su.se.bakover.domain.vedtak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.common.periode.september
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.vedtak.forenkletVedtakInnvilgetRevurdering
import no.nav.su.se.bakover.test.vedtak.forenkletVedtakOpphør
import no.nav.su.se.bakover.test.vedtak.forenkletVedtakSøknadsbehandling
import org.junit.jupiter.api.Test

class ForenkletVedtakTest {

    @Test
    fun `forskjellige caser av forenklet vedtak`() {
        val fnrA = Fnr("06571821087")
        val fnrB = Fnr("81347260331")
        val tikkendeKlokke = TikkendeKlokke()
        val vedtak = listOf(
            // Person A
            // |----------|      (S)
            //    |-----|        (R-O)
            //      ||           (R-I)
            //             |---| (S)
            forenkletVedtakSøknadsbehandling(
                fødselsnummer = fnrA,
                periode = år(2021),
                opprettet = tikkendeKlokke.nextTidspunkt(),
            ),
            forenkletVedtakOpphør(
                fødselsnummer = fnrA,
                periode = april(2021)..oktober(2021),
                opprettet = tikkendeKlokke.nextTidspunkt(),
            ),
            forenkletVedtakInnvilgetRevurdering(
                fødselsnummer = fnrA,
                periode = juni(2021)..juli(2021),
                opprettet = tikkendeKlokke.nextTidspunkt(),
            ),
            forenkletVedtakSøknadsbehandling(
                fødselsnummer = fnrA,
                // Lager et hull i A (hopper over januar)
                periode = februar(2022)..juni(2022),
                opprettet = tikkendeKlokke.nextTidspunkt(),
            ),
            // Person B
            // |----------|      (S)
            //             |---| (S)
            // |---------------| (R-O)
            // |-|               (R-I))
            //              |--| (R-I))
            forenkletVedtakSøknadsbehandling(
                fødselsnummer = fnrB,
                periode = juli(2021)..juni(2022),
                opprettet = tikkendeKlokke.nextTidspunkt(),
            ),
            forenkletVedtakSøknadsbehandling(
                fødselsnummer = fnrB,
                // Lager en kontinuerlig periode for B
                periode = juli(2022)..november(2022),
                opprettet = tikkendeKlokke.nextTidspunkt(),
            ),
            forenkletVedtakOpphør(
                fødselsnummer = fnrB,
                // Opphører alt
                periode = juli(2021)..november(2022),
                opprettet = tikkendeKlokke.nextTidspunkt(),
            ),
            forenkletVedtakInnvilgetRevurdering(
                fødselsnummer = fnrB,
                // Innvilger litt av starten
                periode = juli(2021)..september(2021),
                opprettet = tikkendeKlokke.nextTidspunkt(),
            ),
            forenkletVedtakInnvilgetRevurdering(
                fødselsnummer = fnrB,
                // Innvilger litt av slutten
                periode = juli(2022)..november(2022),
                opprettet = tikkendeKlokke.nextTidspunkt(),
            ),
        ).shuffled() // Shuffler bare for å bevise at rekkefølgen ikke påvirker.
        vedtak.tilInnvilgetForMåned(januar(2021)) shouldBe InnvilgetForMåned(januar(2021), listOf(fnrA))
        vedtak.tilInnvilgetForMåned(februar(2021)) shouldBe InnvilgetForMåned(februar(2021), listOf(fnrA))
        vedtak.tilInnvilgetForMåned(mars(2021)) shouldBe InnvilgetForMåned(mars(2021), listOf(fnrA))
        vedtak.tilInnvilgetForMåned(april(2021)) shouldBe InnvilgetForMåned(april(2021), emptyList())
        vedtak.tilInnvilgetForMåned(mai(2021)) shouldBe InnvilgetForMåned(mai(2021), emptyList())
        vedtak.tilInnvilgetForMåned(juni(2021)) shouldBe InnvilgetForMåned(juni(2021), listOf(fnrA))
        vedtak.tilInnvilgetForMåned(juli(2021)) shouldBe InnvilgetForMåned(juli(2021), listOf(fnrA, fnrB))
        vedtak.tilInnvilgetForMåned(august(2021)) shouldBe InnvilgetForMåned(august(2021), listOf(fnrB))
        vedtak.tilInnvilgetForMåned(september(2021)) shouldBe InnvilgetForMåned(september(2021), listOf(fnrB))
        vedtak.tilInnvilgetForMåned(oktober(2021)) shouldBe InnvilgetForMåned(oktober(2021), listOf())
        vedtak.tilInnvilgetForMåned(november(2021)) shouldBe InnvilgetForMåned(november(2021), listOf(fnrA))
        vedtak.tilInnvilgetForMåned(desember(2021)) shouldBe InnvilgetForMåned(desember(2021), listOf(fnrA))

        vedtak.tilInnvilgetForMåned(januar(2022)) shouldBe InnvilgetForMåned(januar(2022), listOf())
        vedtak.tilInnvilgetForMåned(februar(2022)) shouldBe InnvilgetForMåned(februar(2022), listOf(fnrA))
        vedtak.tilInnvilgetForMåned(mars(2022)) shouldBe InnvilgetForMåned(mars(2022), listOf(fnrA))
        vedtak.tilInnvilgetForMåned(april(2022)) shouldBe InnvilgetForMåned(april(2022), listOf(fnrA))
        vedtak.tilInnvilgetForMåned(mai(2022)) shouldBe InnvilgetForMåned(mai(2022), listOf(fnrA))
        vedtak.tilInnvilgetForMåned(juni(2022)) shouldBe InnvilgetForMåned(juni(2022), listOf(fnrA))
        vedtak.tilInnvilgetForMåned(juli(2022)) shouldBe InnvilgetForMåned(juli(2022), listOf(fnrB))
        vedtak.tilInnvilgetForMåned(august(2022)) shouldBe InnvilgetForMåned(august(2022), listOf(fnrB))
        vedtak.tilInnvilgetForMåned(oktober(2022)) shouldBe InnvilgetForMåned(oktober(2022), listOf(fnrB))
        vedtak.tilInnvilgetForMåned(november(2022)) shouldBe InnvilgetForMåned(november(2022), listOf(fnrB))
    }
}
