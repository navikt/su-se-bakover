package no.nav.su.se.bakover.domain.vedtak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.common.tid.periode.september
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.vedtak.forenkletVedtakOpphør
import no.nav.su.se.bakover.test.vedtak.forenkletVedtakSøknadsbehandling
import no.nav.su.se.bakover.test.vedtak.vedtaksammendragForSak
import no.nav.su.se.bakover.test.vedtak.vedtaksammendragForSakVedtak
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class VedtaksammendragForPeriodeTest {

    @Nested
    inner class tilInnvilgetForMåned {

        @Test
        fun `ingen sammendrag`() {
            emptyList<VedtaksammendragForSak>().innvilgetForMåned(januar(2021)) shouldBe InnvilgetForMåned(
                måned = januar(2021),
                fnr = emptyList(),
            )
        }

        @Test
        fun `bare opphørt innenfor periode`() {
            listOf(forenkletVedtakOpphør()).innvilgetForMåned(januar(2021)) shouldBe InnvilgetForMåned(
                måned = januar(2021),
                fnr = emptyList(),
            )
        }

        @Test
        fun `bare opphørt utenfor periode`() {
            listOf(forenkletVedtakOpphør()).innvilgetForMåned(januar(2022)) shouldBe InnvilgetForMåned(
                måned = januar(2022),
                fnr = emptyList(),
            )
        }

        @Test
        fun `forskjellige caser av vedtakssammendag`() {
            val fnrA = Fnr("06571821087")
            val sakIdFnrA = UUID.randomUUID()
            val saksnummerFnrA = Saksnummer(2021)
            val fnrB = Fnr("81347260331")
            val sakIdFnrB = UUID.randomUUID()
            val saksnummerFnrB = Saksnummer(2022)
            val tikkendeKlokke = TikkendeKlokke()
            // Person A
            // |----------|      (S)
            //    |-----|        (R-O)
            //      ||           (R-I)
            //             |---| (S)
            val vedtakForFnrA = vedtaksammendragForSak(
                fødselsnummer = fnrA,
                sakId = sakIdFnrA,
                saksnummer = saksnummerFnrA,
                vedtaksammendragForSakVedtak(
                    opprettet = tikkendeKlokke.nextTidspunkt(),
                ),
                vedtaksammendragForSakVedtak(
                    periode = april(2021)..oktober(2021),
                    vedtakstype = Vedtakstype.REVURDERING_OPPHØR,
                    opprettet = tikkendeKlokke.nextTidspunkt(),
                ),
                vedtaksammendragForSakVedtak(
                    periode = juni(2021)..juli(2021),
                    vedtakstype = Vedtakstype.REVURDERING_INNVILGELSE,
                    opprettet = tikkendeKlokke.nextTidspunkt(),
                ),
                vedtaksammendragForSakVedtak(
                    periode = februar(2022)..juni(2022),
                    opprettet = tikkendeKlokke.nextTidspunkt(),
                ),
            )

            // Person B
            // |----------|      (S)
            //             |---| (S)
            // |---------------| (R-O)
            // |-|               (R-I))
            //              |--| (R-I))
            val vedtakForFnrB = vedtaksammendragForSak(
                fødselsnummer = fnrB,
                sakId = sakIdFnrB,
                saksnummer = saksnummerFnrB,
                vedtaksammendragForSakVedtak(
                    periode = juli(2021)..juni(2022),
                    opprettet = tikkendeKlokke.nextTidspunkt(),
                ),
                vedtaksammendragForSakVedtak(
                    periode = juli(2022)..november(2022),
                    vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                    opprettet = tikkendeKlokke.nextTidspunkt(),
                ),
                vedtaksammendragForSakVedtak(
                    periode = juli(2021)..november(2022),
                    vedtakstype = Vedtakstype.REVURDERING_OPPHØR,
                    opprettet = tikkendeKlokke.nextTidspunkt(),
                ),
                vedtaksammendragForSakVedtak(
                    periode = juli(2021)..september(2021),
                    vedtakstype = Vedtakstype.REVURDERING_INNVILGELSE,
                    opprettet = tikkendeKlokke.nextTidspunkt(),
                ),
                vedtaksammendragForSakVedtak(
                    periode = juli(2022)..november(2022),
                    vedtakstype = Vedtakstype.REVURDERING_INNVILGELSE,
                    opprettet = tikkendeKlokke.nextTidspunkt(),
                ),
            )

            val vedtak = listOf(
                vedtakForFnrA,
                vedtakForFnrB,
            ).shuffled() // Shuffler bare for å bevise at rekkefølgen ikke påvirker.

            vedtak.innvilgetForMåned(januar(2021)) shouldBe InnvilgetForMåned(januar(2021), listOf(fnrA))
            vedtak.innvilgetForMåned(februar(2021)) shouldBe InnvilgetForMåned(februar(2021), listOf(fnrA))
            vedtak.innvilgetForMåned(mars(2021)) shouldBe InnvilgetForMåned(mars(2021), listOf(fnrA))
            vedtak.innvilgetForMåned(april(2021)) shouldBe InnvilgetForMåned(april(2021), emptyList())
            vedtak.innvilgetForMåned(mai(2021)) shouldBe InnvilgetForMåned(mai(2021), emptyList())
            vedtak.innvilgetForMåned(juni(2021)) shouldBe InnvilgetForMåned(juni(2021), listOf(fnrA))
            vedtak.innvilgetForMåned(juli(2021)) shouldBe InnvilgetForMåned(juli(2021), listOf(fnrA, fnrB))
            vedtak.innvilgetForMåned(august(2021)) shouldBe InnvilgetForMåned(august(2021), listOf(fnrB))
            vedtak.innvilgetForMåned(september(2021)) shouldBe InnvilgetForMåned(september(2021), listOf(fnrB))
            vedtak.innvilgetForMåned(oktober(2021)) shouldBe InnvilgetForMåned(oktober(2021), listOf())
            vedtak.innvilgetForMåned(november(2021)) shouldBe InnvilgetForMåned(november(2021), listOf(fnrA))
            vedtak.innvilgetForMåned(desember(2021)) shouldBe InnvilgetForMåned(desember(2021), listOf(fnrA))

            vedtak.innvilgetForMåned(januar(2022)) shouldBe InnvilgetForMåned(januar(2022), listOf())
            vedtak.innvilgetForMåned(februar(2022)) shouldBe InnvilgetForMåned(februar(2022), listOf(fnrA))
            vedtak.innvilgetForMåned(mars(2022)) shouldBe InnvilgetForMåned(mars(2022), listOf(fnrA))
            vedtak.innvilgetForMåned(april(2022)) shouldBe InnvilgetForMåned(april(2022), listOf(fnrA))
            vedtak.innvilgetForMåned(mai(2022)) shouldBe InnvilgetForMåned(mai(2022), listOf(fnrA))
            vedtak.innvilgetForMåned(juni(2022)) shouldBe InnvilgetForMåned(juni(2022), listOf(fnrA))
            vedtak.innvilgetForMåned(juli(2022)) shouldBe InnvilgetForMåned(juli(2022), listOf(fnrB))
            vedtak.innvilgetForMåned(august(2022)) shouldBe InnvilgetForMåned(august(2022), listOf(fnrB))
            vedtak.innvilgetForMåned(oktober(2022)) shouldBe InnvilgetForMåned(oktober(2022), listOf(fnrB))
            vedtak.innvilgetForMåned(november(2022)) shouldBe InnvilgetForMåned(november(2022), listOf(fnrB))
        }

        @Test
        fun `Henter alle innvilgede, fra en gitt dato, til vi treffer et opphør`() {
            val fødselsnummer = Fnr.generer()
            val sakId = UUID.randomUUID()
            val testdata = VedtaksammendragForSak(
                fødselsnummer = fødselsnummer,
                sakId = sakId,
                saksnummer = Saksnummer(2021),
                vedtak = listOf(
                    vedtaksammendragForSakVedtak(),
                    vedtaksammendragForSakVedtak(
                        opprettet = fixedTidspunkt.plusUnits(1),
                        periode = desember(2021),
                        vedtakstype = Vedtakstype.REVURDERING_OPPHØR,
                    ),
                ),
            )

            testdata.innvilgetForMåned(januar(2021)) shouldBe InnvilgetForMåned(
                måned = januar(2021),
                fnr = listOf(fødselsnummer),
            )
            testdata.innvilgetForMåned(november(2021)) shouldBe InnvilgetForMåned(
                måned = november(2021),
                fnr = listOf(fødselsnummer),
            )

            testdata.innvilgetForMåned(desember(2020)) shouldBe InnvilgetForMåned(
                måned = desember(2020),
                fnr = emptyList(),
            )
            testdata.innvilgetForMåned(desember(2021)) shouldBe InnvilgetForMåned(
                måned = desember(2021),
                fnr = emptyList(),
            )
            testdata.innvilgetForMåned(januar(2022)) shouldBe InnvilgetForMåned(måned = januar(2022), fnr = emptyList())
        }
    }

    @Nested
    inner class tilInnvilgetForMånedEllerSenere {

        @Test
        fun `henter sammendrag dersom fraOgMedEllerSenere er innenfor vedtaksperioden `() {
            val fødselsnummer = Fnr.generer()
            val sammendrag = forenkletVedtakSøknadsbehandling(fødselsnummer = fødselsnummer)
            val actual = sammendrag.innvilgetForMånedEllerSenere(februar(2021))

            actual shouldBe InnvilgetForMånedEllerSenere(
                fraOgMedEllerSenere = februar(2021),
                sakInfo = listOf(
                    SakInfo(
                        sakId = sammendrag.sakId,
                        saksnummer = sammendrag.saksnummer,
                        fnr = fødselsnummer,
                        type = Sakstype.UFØRE,
                    ),
                ),
            )
        }

        @Test
        fun `Henter alle innvilgede, fra en gitt dato, til vi treffer et opphør`() {
            val fødselsnummer = Fnr.generer()
            val sakId = UUID.randomUUID()

            val actual = vedtaksammendragForSak(
                fødselsnummer = fødselsnummer,
                sakId = sakId,
                saksnummer = Saksnummer(2021),
                vedtaksammendragForSakVedtak(
                    opprettet = fixedTidspunkt,
                    vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                ),
                vedtaksammendragForSakVedtak(
                    opprettet = fixedTidspunkt.plusUnits(1),
                    vedtakstype = Vedtakstype.REVURDERING_OPPHØR,
                    periode = desember(2021),
                ),
            ).innvilgetForMånedEllerSenere(januar(2021))

            actual shouldBe InnvilgetForMånedEllerSenere(
                fraOgMedEllerSenere = januar(2021),
                sakInfo = listOf(
                    SakInfo(
                        sakId = sakId,
                        saksnummer = Saksnummer(2021),
                        fnr = fødselsnummer,
                        type = Sakstype.UFØRE,
                    ),
                ),
            )
        }
    }
}
