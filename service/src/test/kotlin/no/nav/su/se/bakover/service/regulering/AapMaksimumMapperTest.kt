package no.nav.su.se.bakover.service.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.regulering.MaksimumVedtakDto
import no.nav.su.se.bakover.domain.regulering.tilMånedsbeløpForSu
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

    private fun maksimumVedtak(dagsats: Int) = MaksimumVedtakDto(
        dagsats = dagsats,
        barnetillegg = 0,
    )
}
