package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OppdragFactoryTest {
    @Test
    fun `no existing oppdrag`() {
        val oppdragDto = OppdragFactory(
            behandling = behandling(),
            sak = sak()
        ).build().toDto()

        oppdragDto.endringskode shouldBe Oppdrag.Endringskode.NY
        oppdragDto.oppdragslinjer shouldHaveSize 1
        oppdragDto.oppdragslinjer.first().endringskode shouldBe Oppdragslinje.Endringskode.NY
        oppdragDto.oppdragslinjer.first().fom shouldBe 1.januar(2020)
        oppdragDto.oppdragslinjer.first().tom shouldBe 31.desember(2020)
    }

    /**
     * L1 |-----|
     * L2       |-----|
     */
    @Test
    fun `no overlap in oppdragslinjer`() {
    }

    /**
     * L1 |-----|
     * L2    |-----|
     */
    @Test
    fun `overlap in oppdragslinjer`() {
    }

    fun behandling() = Behandling.Oppdragsinformasjon(
        behandlingId = UUID.randomUUID(),
        fom = 1.januar(2020),
        tom = 31.desember(2020)
    )

    fun sak() = Sak.Oppdragsinformasjon(
        sakId = UUID.randomUUID()
    )
}
