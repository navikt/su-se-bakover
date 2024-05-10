package no.nav.su.se.bakover.domain.regulering

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

class ÅrsakTilManuellReguleringTest {

    @Test
    fun `differanse i mismatch`() {
        val mistmatch = ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering(
            fradragskategori = Fradragstype.Kategori.Dagpenger,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = "sed",
            eksterntBeløpFørRegulering = BigDecimal(100),
            vårtBeløpFørRegulering = BigDecimal(40),
        )

        mistmatch.differanse shouldBe BigDecimal(60)
    }

    @Test
    fun `differanse i beløp er større en forventet`() {
        val forv = ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering(
            fradragskategori = Fradragstype.Kategori.Dagpenger,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = "sed",
            eksterntBeløpEtterRegulering = BigDecimal(100),
            forventetBeløpEtterRegulering = BigDecimal(60),
        )
        forv.differanse shouldBe BigDecimal(40)
    }
}
