package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Behandling.BehandlingOppdragsinformasjon
import no.nav.su.se.bakover.domain.Sak.SakOppdragsinformasjon
import no.nav.su.se.bakover.domain.beregning.BeregningsPeriode
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OppdragFactoryTest {
    private val sakId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val fnr = "12345678910"
    @Test
    fun `no existing oppdrag`() {

        val actual = OppdragFactory(
            behandling = BehandlingOppdragsinformasjon(
                behandlingId = behandlingId,
                perioder = listOf(BeregningsPeriode(1.januar(2020),31.desember(2020), 5600))
            ),
            sak = SakOppdragsinformasjon(
                sakId = sakId,
                sisteOppdrag = null,
                fnr = fnr
            )
        ).build()

        actual shouldBe Oppdrag(
            id = actual.id,
            opprettet = actual.opprettet,
            sakId = sakId,
            behandlingId = behandlingId,
            endringskode = Oppdrag.Endringskode.NY,
            simulering = null,
            oppdragGjelder = fnr,
            oppdragslinjer = listOf(
                Oppdragslinje(
                    id = actual.sisteOppdragslinje().id,
                    opprettet = actual.sisteOppdragslinje().opprettet,
                    fom = 1.januar(2020),
                    tom = 31.desember(2020),
                    endringskode = Oppdragslinje.Endringskode.NY,
                    refOppdragslinjeId = null,
                    refSakId = sakId,
                    beløp = 5600,
                    klassekode = Oppdragslinje.Klassekode.KLASSE,
                    status = null,
                    statusFom = null,
                    beregningsfrekvens = Oppdragslinje.Beregningsfrekvens.MND,
                    saksbehandler = "saksbehandler",
                    attestant = null
                )
            )
        )
    }

    @Test
    fun `Test at ziping funker`() {
        val
    }

    @Test
    fun `skal referere til forrige oppdragslinje`() {
        val tidligereBehandlingId = UUID.randomUUID()
        val behandlingsInfo = BehandlingOppdragsinformasjon(
            behandlingId = behandlingId,
            perioder = listOf(BeregningsPeriode(1.januar(2021), 31.desember(2021), 12000))
        )
        val eksisterendeOppdragslinje = Oppdragslinje(
            id = UUID.randomUUID(),
            fom = 1.januar(2020),
            tom = 31.desember(2020),
            endringskode = Oppdragslinje.Endringskode.NY,
            refOppdragslinjeId = null,
            refSakId = sakId,
            beløp = 5600,
            klassekode = Oppdragslinje.Klassekode.KLASSE,
            status = null,
            statusFom = null,
            beregningsfrekvens = Oppdragslinje.Beregningsfrekvens.MND,
            saksbehandler = "saksbehandler",
            attestant = "attestant"
        )

        val actual = OppdragFactory(
            behandling = behandlingsInfo,
            sak = SakOppdragsinformasjon(
                sakId = sakId,
                sisteOppdrag = Oppdrag(
                    sakId = sakId,
                    behandlingId = tidligereBehandlingId,
                    endringskode = Oppdrag.Endringskode.NY,
                    oppdragslinjer = listOf(eksisterendeOppdragslinje),
                    oppdragGjelder = fnr
                ),
                fnr = "12345678910"
            )
        ).build()

        actual shouldBe Oppdrag(
            id = actual.id,
            opprettet = actual.opprettet,
            sakId = sakId,
            behandlingId = behandlingId,
            endringskode = Oppdrag.Endringskode.ENDR,
            simulering = null,
            oppdragGjelder = fnr,
            oppdragslinjer = listOf(
                Oppdragslinje(
                    id = actual.sisteOppdragslinje().id,
                    opprettet = actual.sisteOppdragslinje().opprettet,
                    fom = 1.januar(2021),
                    tom = 31.desember(2021),
                    endringskode = Oppdragslinje.Endringskode.NY,
                    refOppdragslinjeId = eksisterendeOppdragslinje.id,
                    refSakId = sakId,
                    beløp = 12000,
                    klassekode = Oppdragslinje.Klassekode.KLASSE,
                    status = null,
                    statusFom = null,
                    beregningsfrekvens = Oppdragslinje.Beregningsfrekvens.MND,
                    saksbehandler = "saksbehandler",
                    attestant = null
                )
            )
        )
    }
}
