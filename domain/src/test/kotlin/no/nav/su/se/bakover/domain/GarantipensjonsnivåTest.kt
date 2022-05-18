package no.nav.su.se.bakover.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.satser.Garantipensjonsnivå
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
    fun `garantipensjonsnivå etter mai 2021 skal være 187 252 kr`() {
        Garantipensjonsnivå.Ordinær
            .forDato(1.mai(2021)) shouldBe 187252

        Garantipensjonsnivå.Ordinær
            .forDato(1.juli(2021)) shouldBe 187252
    }
}
