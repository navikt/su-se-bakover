package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.Behandling.BehandlingOppdragsinformasjon
import no.nav.su.se.bakover.domain.Sak.SakOppdragsinformasjon
import no.nav.su.se.bakover.domain.beregning.BeregningsPeriode
import no.nav.su.se.bakover.domain.beregning.Sats.HØY
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.Endringskode.ENDR
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.Endringskode.NY
import no.nav.su.se.bakover.domain.oppdrag.Oppdragslinje.Beregningsfrekvens.MND
import no.nav.su.se.bakover.domain.oppdrag.Oppdragslinje.Endringskode
import no.nav.su.se.bakover.domain.oppdrag.Oppdragslinje.Klassekode.KLASSE
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
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
                perioder = listOf(
                    BeregningsPeriode(
                        fom = 1.januar(2020),
                        tom = 31.desember(2020),
                        beløp = 5600,
                        sats = HØY
                    )
                )
            ),
            sak = SakOppdragsinformasjon(
                sakId = sakId,
                sisteOppdrag = null,
                fnr = fnr
            )
        ).build()

        val first = actual.oppdragslinjer.first()
        actual shouldBe expectedOppdrag(
            actual, NY,
            listOf(
                expectedOppdragslinje(
                    id = first.id,
                    opprettet = first.opprettet,
                    fom = 1.januar(2020),
                    tom = 31.desember(2020),
                    beløp = 5600,
                    refOppdragslinjeId = null
                )
            )
        )
    }

    @Test
    fun `nye oppdragslinjer skal refere til forutgående oppdragslinjer`() {
        val tidligereBehandlingId = UUID.randomUUID()
        val behandlingsInfo = BehandlingOppdragsinformasjon(
            behandlingId = behandlingId,
            perioder = listOf(
                BeregningsPeriode(
                    fom = 1.januar(2020),
                    tom = 31.mai(2020),
                    beløp = 5600,
                    sats = HØY
                ),
                BeregningsPeriode(
                    fom = 1.juni(2020),
                    tom = 31.august(2020),
                    beløp = 5700,
                    sats = HØY
                ),
                BeregningsPeriode(
                    fom = 1.september(2020),
                    tom = 31.desember(2020),
                    beløp = 5800,
                    sats = HØY
                )
            )
        )
        val eksisterendeOppdragslinje = expectedOppdragslinje(
            id = UUID.randomUUID(),
            opprettet = now(),
            fom = 1.januar(2019),
            tom = 31.desember(2019),
            beløp = 5500,
            refOppdragslinjeId = null
        )

        val actualOppdrag = OppdragFactory(
            behandling = behandlingsInfo,
            sak = SakOppdragsinformasjon(
                sakId = sakId,
                sisteOppdrag = Oppdrag(
                    sakId = sakId,
                    behandlingId = tidligereBehandlingId,
                    endringskode = NY,
                    oppdragslinjer = listOf(
                        eksisterendeOppdragslinje
                    ),
                    oppdragGjelder = fnr
                ),
                fnr = "12345678910"
            )
        ).build()

        actualOppdrag shouldBe expectedOppdrag(
            actual = actualOppdrag,
            endringskode = ENDR,
            oppdragslinjer = listOf(
                expectedOppdragslinje(
                    id = actualOppdrag.oppdragslinjer[0].id,
                    opprettet = actualOppdrag.sisteOppdragslinje().opprettet,
                    fom = 1.januar(2020),
                    tom = 31.mai(2020),
                    beløp = 5600,
                    refOppdragslinjeId = eksisterendeOppdragslinje.id
                ),
                expectedOppdragslinje(
                    id = actualOppdrag.oppdragslinjer[1].id,
                    opprettet = actualOppdrag.sisteOppdragslinje().opprettet,
                    fom = 1.juni(2020),
                    tom = 31.august(2020),
                    beløp = 5700,
                    refOppdragslinjeId = actualOppdrag.oppdragslinjer[0].id
                ),
                expectedOppdragslinje(
                    id = actualOppdrag.oppdragslinjer[2].id,
                    opprettet = actualOppdrag.sisteOppdragslinje().opprettet,
                    fom = 1.september(2020),
                    tom = 31.desember(2020),
                    beløp = 5800,
                    refOppdragslinjeId = actualOppdrag.oppdragslinjer[1].id
                )
            )
        )
    }

    private fun expectedOppdrag(actual: Oppdrag, endringskode: Oppdrag.Endringskode, oppdragslinjer: List<Oppdragslinje>): Oppdrag {
        return Oppdrag(
            id = actual.id,
            opprettet = actual.opprettet,
            sakId = sakId,
            behandlingId = behandlingId,
            endringskode = endringskode,
            simulering = null,
            oppdragGjelder = fnr,
            oppdragslinjer = oppdragslinjer
        )
    }

    private fun expectedOppdragslinje(id: UUID, opprettet: Instant, fom: LocalDate, tom: LocalDate, beløp: Int, refOppdragslinjeId: UUID?): Oppdragslinje {
        return Oppdragslinje(
            id = id,
            opprettet = opprettet,
            fom = fom,
            tom = tom,
            endringskode = Endringskode.NY,
            refOppdragslinjeId = refOppdragslinjeId,
            refSakId = sakId,
            beløp = beløp,
            klassekode = KLASSE,
            status = null,
            statusFom = null,
            beregningsfrekvens = MND,
            saksbehandler = "saksbehandler",
            attestant = null
        )
    }
}
