package no.nav.su.se.bakover.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import org.junit.jupiter.api.Test

internal class GarantipensjonsnivåTest {
    @Test
    fun `ordinært garantipensjonsnivå før sept 2019 skal være udefinert`() {
        shouldThrow<RuntimeException> {
            Garantipensjonsnivå.Ordinær
                .forDato(1.januar(2019))
        }
    }

    @Test
    fun `ordinært garantipensjonsnivå mellom sept 2019 og mai 2020 skal være 176 099 kr`() {
        Garantipensjonsnivå.Ordinær
            .forDato(1.januar(2020)) shouldBe 176099

        Garantipensjonsnivå.Ordinær
            .forDato(1.september(2019)) shouldBe 176099

        Garantipensjonsnivå.Ordinær
            .forDato(30.april(2020)) shouldBe 176099
    }

    @Test
    fun `ordinært garantipensjonsnivå etter mai 2020 skal være 177 724 kr`() {
        Garantipensjonsnivå.Ordinær
            .forDato(1.mai(2020)) shouldBe 177724

        Garantipensjonsnivå.Ordinær
            .forDato(1.juli(2020)) shouldBe 177724
    }

    @Test
    fun `garantipensjonsnivå etter mai 2021 skal være 179 123 kr`() {
        Garantipensjonsnivå.Ordinær
            .forDato(1.mai(2021)) shouldBe 186263

        Garantipensjonsnivå.Ordinær
            .forDato(1.juli(2021)) shouldBe 186263
    }

    @Test
    fun `periodiserer garantipensjonsnivå`() {
        val januar = Periode.create(1.januar(2020), 31.januar(2020))
        Garantipensjonsnivå.Ordinær.periodiser(januar) shouldBe mapOf(januar to 14674.916666666666667)
        val desember = Periode.create(1.desember(2020), 31.desember(2020))
        Garantipensjonsnivå.Ordinær.periodiser(desember) shouldBe mapOf(desember to 14810.333333333333333)

        val heleÅret = Periode.create(1.januar(2020), 31.desember(2020))
        Garantipensjonsnivå.Ordinær.periodiser(heleÅret).let {
            it.size shouldBe 12
            it[januar] shouldBe 14674.916666666666667
            it[desember] shouldBe 14810.333333333333333
        }
    }
}
