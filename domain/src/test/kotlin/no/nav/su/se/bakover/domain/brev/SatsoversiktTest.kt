package no.nav.su.se.bakover.domain.brev

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test

internal class SatsoversiktTest {
    @Test
    fun `tar høyde for endringer i sats`() {
        Satsoversikt.fra(
            listOf(
                bosituasjongrunnlagEnslig(periode = år(2021)),
            ),
            satsFactory = satsFactoryTestPåDato(påDato = 21.mai(2021)),
            sakstype = Sakstype.UFØRE,
        ) shouldBe Satsoversikt(
            perioder = listOf(
                Satsoversikt.Satsperiode(
                    fraOgMed = "01.01.2021",
                    tilOgMed = "30.04.2021",
                    sats = "høy",
                    satsBeløp = 20946,
                    satsGrunn = "ENSLIG",
                ),
                Satsoversikt.Satsperiode(
                    fraOgMed = "01.05.2021",
                    tilOgMed = "31.12.2021",
                    sats = "høy",
                    satsBeløp = 21989,
                    satsGrunn = "ENSLIG",
                ),
            ),
        )
    }

    @Test
    fun `håndterer forskjellige bosituasjoner`() {
        Satsoversikt.fra(
            listOf(
                bosituasjongrunnlagEnslig(periode = Periode.create(1.april(2021), 31.mai(2021))),
                bosituasjongrunnlagEpsUførFlyktning(periode = Periode.create(1.juni(2021), 31.desember(2021))),
            ),
            satsFactory = satsFactoryTestPåDato(påDato = 21.mai(2021)),
            sakstype = Sakstype.UFØRE,
        ) shouldBe Satsoversikt(
            perioder = listOf(
                Satsoversikt.Satsperiode(
                    fraOgMed = "01.04.2021",
                    tilOgMed = "30.04.2021",
                    sats = "høy",
                    satsBeløp = 20946,
                    satsGrunn = "ENSLIG",
                ),
                Satsoversikt.Satsperiode(
                    fraOgMed = "01.05.2021",
                    tilOgMed = "31.05.2021",
                    sats = "høy",
                    satsBeløp = 21989,
                    satsGrunn = "ENSLIG",
                ),
                Satsoversikt.Satsperiode(
                    fraOgMed = "01.06.2021",
                    tilOgMed = "31.12.2021",
                    sats = "ordinær",
                    satsBeløp = 20216,
                    satsGrunn = "DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING",
                ),
            ),
        )
    }
}
