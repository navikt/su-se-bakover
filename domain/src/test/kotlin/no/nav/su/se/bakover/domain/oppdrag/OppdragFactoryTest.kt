package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Behandling.BehandlingOppdragsinformasjon
import no.nav.su.se.bakover.domain.Sak.SakOppdragsinformasjon
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OppdragFactoryTest {
    val sakId = UUID.randomUUID()
    val behandlingId = UUID.randomUUID()

    @Test
    fun `no existing oppdrag`() {

        val actual = OppdragFactory(
            behandling = BehandlingOppdragsinformasjon(
                behandlingId = behandlingId,
                fom = 1.januar(2020),
                tom = 31.desember(2020)
            ),
            sak = SakOppdragsinformasjon(
                sakId = sakId,
                sisteOppdrag = null
            )
        ).build()

        actual shouldBe Oppdrag(
            id = actual.id,
            opprettet = actual.opprettet,
            sakId = sakId,
            behandlingId = behandlingId,
            endringskode = Oppdrag.Endringskode.NY,
            simulering = null,
            oppdragslinjer = listOf(
                Oppdragslinje(
                    id = actual.sisteOppdragslinje().id,
                    opprettet = actual.sisteOppdragslinje().opprettet,
                    fom = 1.januar(2020),
                    tom = 31.desember(2020),
                    endringskode = Oppdragslinje.Endringskode.NY,
                    refOppdragslinjeId = null
                )
            )
        )
    }

    /**
     * L1 |-----|
     * L2       |-----|
     */
    @Test
    fun `skal referere til forrige oppdragslinje`() {
        val tidligereBehandlingId = UUID.randomUUID()
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

        val actual = OppdragFactory(
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

        actual shouldBe Oppdrag(
            id = actual.id,
            opprettet = actual.opprettet,
            sakId = sakId,
            behandlingId = behandlingId,
            endringskode = Oppdrag.Endringskode.ENDR,
            simulering = null,
            oppdragslinjer = listOf(
                Oppdragslinje(
                    id = actual.sisteOppdragslinje().id,
                    opprettet = actual.sisteOppdragslinje().opprettet,
                    fom = 1.januar(2021),
                    tom = 31.desember(2021),
                    endringskode = Oppdragslinje.Endringskode.NY,
                    refOppdragslinjeId = eksisterendeOppdragslinje.id
                )
            )
        )
    }

    private fun nyttOppdrag(
        sakId: UUID = UUID.randomUUID(),
        behandlingId: UUID = UUID.randomUUID(),
        vararg oppdragslinje: Oppdragslinje
    ): Oppdrag {
        return Oppdrag(
            sakId = sakId,
            behandlingId = behandlingId,
            endringskode = Oppdrag.Endringskode.NY,
            oppdragslinjer = oppdragslinje.toList()
        )
    }
}
