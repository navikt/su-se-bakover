package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Behandling.BehandlingOppdragsinformasjon
import no.nav.su.se.bakover.domain.Sak.SakOppdragsinformasjon
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OppdragFactoryTest {
    @Test
    fun `no existing oppdrag`() {
        val oppdragDto = OppdragFactory(
            behandling = BehandlingOppdragsinformasjon(
                behandlingId = UUID.randomUUID(),
                fom = 1.januar(2020),
                tom = 31.desember(2020)
            ),
            sak = SakOppdragsinformasjon(
                sakId = UUID.randomUUID(),
                sisteOppdrag = null
            )
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
    fun `skal referere til forrige oppdragslinje`() {
        val sakId = UUID.randomUUID()
        val tidligereBehandlingId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()
        val behandlingsInfo = BehandlingOppdragsinformasjon(
            behandlingId = behandlingId,
            fom = 1.januar(2021),
            tom = 31.desember(2021)

        )
        val eksisterendeOppdragslinje = Oppdragslinje(
            id = UUID.randomUUID(),
            fom = 1.januar(2020),
            tom = 31.desember(2020),
            endringskode = Oppdragslinje.Endringskode.NY,
            refOppdragslinjeId = null
        )

        val oppdrag = OppdragFactory(
            behandling = behandlingsInfo,
            sak = SakOppdragsinformasjon(
                sakId = sakId,
                sisteOppdrag = nyttOppdrag(
                    sakId,
                    tidligereBehandlingId,
                    eksisterendeOppdragslinje
                )
            )
        ).build()

        val dto = oppdrag.toDto()
        dto.sakId shouldBe sakId
        dto.endringskode shouldBe Oppdrag.Endringskode.ENDR
        dto.oppdragslinjer.first().endringskode shouldBe Oppdragslinje.Endringskode.NY
        dto.oppdragslinjer.first().refOppdragslinjeId shouldBe eksisterendeOppdragslinje.id
    }

    private fun nyttOppdrag(sakId: UUID = UUID.randomUUID(), behandlingId: UUID = UUID.randomUUID(), vararg oppdragslinje: Oppdragslinje): Oppdrag {
        return Oppdrag(
            sakId = sakId,
            behandlingId = behandlingId,
            endringskode = Oppdrag.Endringskode.NY,
            oppdragslinjer = oppdragslinje.toList()
        )
    }
}
