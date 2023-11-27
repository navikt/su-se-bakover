package no.nav.su.se.bakover.domain.satser

import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
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
import org.junit.jupiter.api.Test
import sats.Månedssats
import sats.Månedssatser
import sats.RåSats
import sats.RåSatser
import sats.periodisert

internal class Periode_periodisert_Test {

    @Test
    fun `må være sortert i stigende rekkefølge`() {
        shouldThrow<IllegalArgumentException> {
            RåSatser(
                nonEmptyListOf(
                    RåSats(1.mai(2019), "a"),
                    RåSats(1.mai(2018), "b"),
                ),
            ).periodisert(mai(2019))
        }.message shouldBe "Datoene må være sortert i stigende rekkefølge og uten duplikater: [2019-05-01, 2018-05-01]"
    }

    @Test
    fun `kan ikke inneholde duplikater`() {
        shouldThrow<IllegalArgumentException> {
            RåSatser(
                nonEmptyListOf(
                    RåSats(1.mai(2019), "a"),
                    RåSats(1.mai(2019), "b"),
                ),
            ).periodisert(mai(2019))
        }.message shouldBe "Datoene må være sortert i stigende rekkefølge og uten duplikater: [2019-05-01, 2019-05-01]"
    }

    @Test
    fun `et element eldre enn tidligsteTilgjengeligeMåned gir exception`() {
        // I dette tilfellet vil vi mangle data for april 2018. Det er ikke ønskelig.
        shouldThrow<IllegalStateException> {
            RåSatser(RåSats(1.mai(2018), "a")).periodisert(april(2018))
        }.message shouldBe "Kan ikke periodisere siden vi mangler data for første ønsket måned: 2018-04. Tidligste måned tilgjengelig er 2018-05"
    }

    @Test
    fun `to elementer eldre enn tidligsteTilgjengeligeMåned gir exception`() {
        // I dette tilfellet vil vi mange verdier for april 2018. Det er ikke noe vi ønsker å støtte.
        shouldThrow<IllegalStateException> {
            RåSatser(
                nonEmptyListOf(
                    RåSats(1.mai(2018), "a"),
                    RåSats(1.mai(2019), "b"),
                ),
            ).periodisert(april(2018))
        }.message shouldBe "Kan ikke periodisere siden vi mangler data for første ønsket måned: 2018-04. Tidligste måned tilgjengelig er 2018-05"
    }

    @Test
    fun `tre elementer eldre enn tidligsteTilgjengeligeMåned gir exception`() {
        // I dette tilfellet vil vi mange verdier for april 2018. Det er ikke noe vi ønsker å støtte.
        shouldThrow<IllegalStateException> {
            RåSatser(
                nonEmptyListOf(
                    RåSats(1.mai(2018), "a"),
                    RåSats(1.mai(2019), "b"),
                    RåSats(1.mai(2020), "c"),
                ),
            ).periodisert(april(2018))
        }.message shouldBe "Kan ikke periodisere siden vi mangler data for første ønsket måned: 2018-04. Tidligste måned tilgjengelig er 2018-05"
    }

    @Test
    fun `et element likt som tidligsteTilgjengeligeMåned`() {
        RåSatser(
            nonEmptyListOf(
                RåSats(1.mai(2019), "a"),
            ),
        ).periodisert(mai(2019)) shouldBe Månedssatser(
            nonEmptyListOf(
                Månedssats(1.mai(2019), mai(2019), "a"),
            ),
        )
    }

    @Test
    fun `et element yngre enn tidligsteTilgjengeligeMåned`() {
        RåSatser(
            nonEmptyListOf(
                RåSats(1.mai(2019), "a"),
            ),
        ).periodisert(juni(2019)) shouldBe Månedssatser(
            nonEmptyListOf(Månedssats(1.mai(2019), juni(2019), "a")),
        )
    }

    @Test
    fun `to elementer yngre enn tidligsteTilgjengeligeMåned`() {
        RåSatser(
            nonEmptyListOf(
                RåSats(1.mai(2018), "a"),
                RåSats(1.mai(2019), "b"),
            ),
        ).periodisert(juni(2019)) shouldBe Månedssatser(
            nonEmptyListOf(
                Månedssats(1.mai(2019), juni(2019), "b"),
            ),
        )
    }

    @Test
    fun `et element yngre og et likt som tidligsteTilgjengeligeMåned`() {
        RåSatser(
            nonEmptyListOf(
                RåSats(1.mai(2018), "a"),
                RåSats(1.mai(2019), "b"),
            ),
        ).periodisert(mai(2019)) shouldBe Månedssatser(
            nonEmptyListOf(
                Månedssats(1.mai(2019), mai(2019), "b"),
            ),
        )
    }

    @Test
    fun `et element likt og et eldre enn tidligsteTilgjengeligeMåned`() {
        RåSatser(
            nonEmptyListOf(
                RåSats(1.juni(2018), "a"),
                RåSats(1.juli(2018), "b"),
            ),
        ).periodisert(juli(2018)) shouldBe Månedssatser(
            nonEmptyListOf(
                Månedssats(1.juli(2018), juli(2018), "b"),
            ),
        )
    }

    @Test
    fun `et element yngre og et eldre enn tidligsteTilgjengeligeMåned`() {
        RåSatser(
            nonEmptyListOf(
                RåSats(1.mai(2018), "a"),
                RåSats(1.juli(2018), "b"),
            ),
        ).periodisert(juni(2018)) shouldBe Månedssatser(
            nonEmptyListOf(
                Månedssats(1.mai(2018), juni(2018), "a"),
                Månedssats(1.juli(2018), juli(2018), "b"),
            ),
        )
    }

    @Test
    fun `et element yngre og et likt og et eldre enn tidligsteTilgjengeligeMåned`() {
        RåSatser(
            nonEmptyListOf(
                RåSats(1.april(2018), "a"),
                RåSats(1.juni(2018), "b"),
                RåSats(1.august(2018), "c"),
            ),
        ).periodisert(juni(2018)) shouldBe Månedssatser(
            nonEmptyListOf(
                Månedssats(1.juni(2018), juni(2018), "b"),
                Månedssats(1.juni(2018), juli(2018), "b"),
                Månedssats(1.august(2018), august(2018), "c"),
            ),
        )
    }

    @Test
    fun `er sortert over 3 år`() {
        RåSatser(
            nonEmptyListOf(
                RåSats(1.desember(2018), "a"),
                RåSats(1.mai(2019), "b"),
                RåSats(1.januar(2020), "c"),
            ),
        ).periodisert(desember(2018)) shouldBe Månedssatser(
            nonEmptyListOf(
                Månedssats(1.desember(2018), desember(2018), "a"),
                Månedssats(1.desember(2018), januar(2019), "a"),
                Månedssats(1.desember(2018), februar(2019), "a"),
                Månedssats(1.desember(2018), mars(2019), "a"),
                Månedssats(1.desember(2018), april(2019), "a"),
                Månedssats(1.mai(2019), mai(2019), "b"),
                Månedssats(1.mai(2019), juni(2019), "b"),
                Månedssats(1.mai(2019), juli(2019), "b"),
                Månedssats(1.mai(2019), august(2019), "b"),
                Månedssats(1.mai(2019), september(2019), "b"),
                Månedssats(1.mai(2019), oktober(2019), "b"),
                Månedssats(1.mai(2019), november(2019), "b"),
                Månedssats(1.mai(2019), desember(2019), "b"),
                Månedssats(1.januar(2020), januar(2020), "c"),
            ),
        )
    }
}
