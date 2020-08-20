package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Detaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.Utbetaling
import no.nav.su.se.bakover.web.routes.behandling.OppdragJson.OppdragslinjeJson.Companion.toOppdragslinjeJson
import no.nav.su.se.bakover.web.routes.behandling.OppdragJson.SimuleringJson.Companion.toSimuleringJson
import no.nav.su.se.bakover.web.routes.behandling.OppdragJson.SimuleringJson.SimulertPeriodeJson.Companion.toSimulertPeriodeJson
import no.nav.su.se.bakover.web.routes.behandling.OppdragJson.SimuleringJson.SimulertPeriodeJson.UtbetalingJson.Companion.toUtbetalingJson
import no.nav.su.se.bakover.web.routes.behandling.OppdragJson.SimuleringJson.SimulertPeriodeJson.UtbetalingJson.DetaljerJson.Companion.toDetaljerJson
import java.time.Instant
import java.time.LocalDate

data class OppdragJson(
    val id: String,
    val opprettet: Instant,
    val sakId: String,
    val behandlingId: String,
    val simulering: SimuleringJson?,
    val oppdragslinjer: List<OppdragslinjeJson>,
) {
    companion object {
        fun Oppdrag.toJson() =
            OppdragJson(
                id = this.id.toString(),
                opprettet = this.opprettet,
                sakId = this.sakId.toString(),
                behandlingId = this.behandlingId.toString(),
                oppdragslinjer = this.oppdragslinjer.toOppdragslinjeJson(),
                simulering = this.getSimulering()?.toSimuleringJson(),
            )
    }

    data class OppdragslinjeJson(
        val id: String,
        val opprettet: Instant,
        val fom: LocalDate,
        val tom: LocalDate,
        var forrigeOppdragslinjeId: String?,
        val beløp: Int
    ) {
        companion object {
            fun List<Oppdragslinje>.toOppdragslinjeJson(): List<OppdragslinjeJson> = this.map {
                OppdragslinjeJson(
                    id = it.id.toString(),
                    opprettet = it.opprettet,
                    fom = it.fom,
                    tom = it.tom,
                    forrigeOppdragslinjeId = it.forrigeOppdragslinjeId.toString(),
                    beløp = it.beløp
                )
            }
        }
    }

    data class SimuleringJson(
        val gjelderId: String,
        val gjelderNavn: String,
        val datoBeregnet: LocalDate,
        val totalBelop: Int,
        val periodeList: List<SimulertPeriodeJson>
    ) {
        companion object {
            fun Simulering.toSimuleringJson() = SimuleringJson(
                gjelderId = this.gjelderId,
                gjelderNavn = this.gjelderNavn,
                totalBelop = this.totalBelop,
                datoBeregnet = this.datoBeregnet,
                periodeList = this.periodeList.toSimulertPeriodeJson()
            )
        }

        data class SimulertPeriodeJson(
            val fom: LocalDate,
            val tom: LocalDate,
            val utbetaling: List<UtbetalingJson>
        ) {
            companion object {
                fun List<SimulertPeriode>.toSimulertPeriodeJson() = this.map {
                    SimulertPeriodeJson(
                        fom = it.fom,
                        tom = it.tom,
                        utbetaling = it.utbetaling.toUtbetalingJson()
                    )
                }
            }

            data class UtbetalingJson(
                val fagSystemId: String,
                val utbetalesTilId: String,
                val utbetalesTilNavn: String,
                val forfall: LocalDate,
                val feilkonto: Boolean,
                val detaljer: List<DetaljerJson>
            ) {
                companion object {
                    fun List<Utbetaling>.toUtbetalingJson(): List<UtbetalingJson> = this.map {
                        UtbetalingJson(
                            fagSystemId = it.fagSystemId,
                            utbetalesTilId = it.utbetalesTilId,
                            utbetalesTilNavn = it.utbetalesTilNavn,
                            forfall = it.forfall,
                            feilkonto = it.feilkonto,
                            detaljer = it.detaljer.toDetaljerJson()
                        )
                    }
                }
                data class DetaljerJson(
                    val faktiskFom: LocalDate,
                    val faktiskTom: LocalDate,
                    val konto: String,
                    val belop: Int,
                    val tilbakeforing: Boolean,
                    val sats: Int,
                    val typeSats: String,
                    val antallSats: Int,
                    val uforegrad: Int,
                    val klassekode: String,
                    val klassekodeBeskrivelse: String,
                    val utbetalingsType: String,
                    val refunderesOrgNr: String
                ) {
                    companion object {
                        fun List<Detaljer>.toDetaljerJson(): List<DetaljerJson> = this.map {
                            DetaljerJson(
                                faktiskFom = it.faktiskFom,
                                klassekode = it.klassekode,
                                antallSats = it.antallSats,
                                belop = it.belop,
                                faktiskTom = it.faktiskTom,
                                klassekodeBeskrivelse = it.klassekodeBeskrivelse,
                                konto = it.konto,
                                refunderesOrgNr = it.refunderesOrgNr,
                                sats = it.sats,
                                tilbakeforing = it.tilbakeforing,
                                typeSats = it.typeSats,
                                uforegrad = it.uforegrad,
                                utbetalingsType = it.utbetalingsType
                            )
                        }
                    }
                }
            }
        }
    }
}
