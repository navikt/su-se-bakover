package no.nav.su.se.bakover.service.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.aap.MaksimumVedtakDto
import no.nav.su.se.bakover.common.person.Fnr
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AapMaksimumMapperTest {

    @Test
    fun `regner om vanlig dagsats til månedsbeløp for SU`() {
        val vedtak = maksimumVedtak(dagsats = 500)

        vedtak.tilMånedsbeløpForSu() shouldBe BigDecimal("10833.33")
    }

    @Test
    fun `regner om dagsats med barnetillegg til månedsbeløp for SU`() {
        val vedtak = maksimumVedtak(dagsats = 650)

        vedtak.tilMånedsbeløpForSu() shouldBe BigDecimal("14083.33")
    }

    @Test
    fun `mapper før og etter vedtak til regulert AAP beløp`() {
        val regulertBeløp = tilRegulertAapBeløp(
            fnr = Fnr("12345678910"),
            førRegulering = maksimumVedtak(dagsats = 500),
            etterRegulering = maksimumVedtak(dagsats = 525),
        )

        regulertBeløp.førRegulering shouldBe BigDecimal("10833.33")
        regulertBeløp.etterRegulering shouldBe BigDecimal("11375.00")
    }

    private fun maksimumVedtak(dagsats: Int) = MaksimumVedtakDto(
        dagsats = dagsats,
    )
}
