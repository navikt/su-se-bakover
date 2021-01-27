package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

abstract class Saksbehandling {

    abstract fun accept(visitor: StatusovergangVisitor)

    sealed class Søknadsbehandling : Saksbehandling() {
        abstract val id: UUID
        abstract val opprettet: Tidspunkt
        abstract val sakId: UUID
        abstract val søknad: Søknad
        abstract val oppgaveId: OppgaveId
        abstract val behandlingsinformasjon: Behandlingsinformasjon
        abstract val status: Behandling.BehandlingsStatus
        abstract val fnr: Fnr

        data class Opprettet(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val søknad: Søknad,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            override val fnr: Fnr
        ) : Søknadsbehandling() {
            override val status: Behandling.BehandlingsStatus = Behandling.BehandlingsStatus.OPPRETTET

            override fun accept(visitor: StatusovergangVisitor) {
                visitor.visit(this)
            }

            fun tilVilkårsvurdert(behandlingsinformasjon: Behandlingsinformasjon) =
                Vilkårsvurdert.opprett(
                    id,
                    opprettet,
                    sakId,
                    søknad,
                    oppgaveId,
                    this.behandlingsinformasjon.patch(behandlingsinformasjon),
                    fnr
                )
        }

        sealed class Vilkårsvurdert : Søknadsbehandling() {
            abstract override val id: UUID
            abstract override val opprettet: Tidspunkt
            abstract override val sakId: UUID
            abstract override val søknad: Søknad
            abstract override val oppgaveId: OppgaveId
            abstract override val behandlingsinformasjon: Behandlingsinformasjon
            abstract override val fnr: Fnr

            fun tilVilkårsvurdert(behandlingsinformasjon: Behandlingsinformasjon): Saksbehandling =
                opprett(
                    id,
                    opprettet,
                    sakId,
                    søknad,
                    oppgaveId,
                    this.behandlingsinformasjon.patch(behandlingsinformasjon),
                    fnr
                )

            fun tilBeregnet(beregning: Beregning): Saksbehandling =
                Beregnet.opprett(id, opprettet, sakId, søknad, oppgaveId, behandlingsinformasjon, fnr, beregning)

            companion object {
                fun opprett(
                    id: UUID,
                    opprettet: Tidspunkt,
                    sakId: UUID,
                    søknad: Søknad,
                    oppgaveId: OppgaveId,
                    behandlingsinformasjon: Behandlingsinformasjon,
                    fnr: Fnr
                ): Saksbehandling {
                    return when {
                        behandlingsinformasjon.erInnvilget() -> {
                            Innvilget(
                                id,
                                opprettet,
                                sakId,
                                søknad,
                                oppgaveId,
                                behandlingsinformasjon,
                                fnr
                            )
                        }
                        behandlingsinformasjon.erAvslag() -> {
                            Avslag(
                                id,
                                opprettet,
                                sakId,
                                søknad,
                                oppgaveId,
                                behandlingsinformasjon,
                                fnr
                            )
                        }
                        else -> {
                            Opprettet(
                                id,
                                opprettet,
                                sakId,
                                søknad,
                                oppgaveId,
                                behandlingsinformasjon,
                                fnr
                            )
                        }
                    }
                }
            }

            data class Innvilget(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val søknad: Søknad,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr
            ) : Vilkårsvurdert() {

                override val status: Behandling.BehandlingsStatus = Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET

                override fun accept(visitor: StatusovergangVisitor) {
                    visitor.visit(this)
                }
            }

            data class Avslag(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val søknad: Søknad,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr
            ) : Vilkårsvurdert() {

                override val status: Behandling.BehandlingsStatus = Behandling.BehandlingsStatus.VILKÅRSVURDERT_AVSLAG

                override fun accept(visitor: StatusovergangVisitor) {
                    visitor.visit(this)
                }
            }
        }

        sealed class Beregnet : Søknadsbehandling() {
            abstract override val id: UUID
            abstract override val opprettet: Tidspunkt
            abstract override val sakId: UUID
            abstract override val søknad: Søknad
            abstract override val oppgaveId: OppgaveId
            abstract override val behandlingsinformasjon: Behandlingsinformasjon
            abstract override val fnr: Fnr
            abstract val beregning: Beregning

            fun tilVilkårsvurdert(behandlingsinformasjon: Behandlingsinformasjon): Saksbehandling =
                Vilkårsvurdert.opprett(
                    id,
                    opprettet,
                    sakId,
                    søknad,
                    oppgaveId,
                    this.behandlingsinformasjon.patch(behandlingsinformasjon),
                    fnr
                )

            fun tilBeregnet(beregning: Beregning): Saksbehandling =
                opprett(id, opprettet, sakId, søknad, oppgaveId, behandlingsinformasjon, fnr, beregning)

            fun tilSimulert(simulering: Simulering): Saksbehandling =
                Simulert(id, opprettet, sakId, søknad, oppgaveId, behandlingsinformasjon, fnr, beregning, simulering)

            companion object {
                fun opprett(
                    id: UUID,
                    opprettet: Tidspunkt,
                    sakId: UUID,
                    søknad: Søknad,
                    oppgaveId: OppgaveId,
                    behandlingsinformasjon: Behandlingsinformasjon,
                    fnr: Fnr,
                    beregning: Beregning
                ): Saksbehandling =
                    when (VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                        is AvslagGrunnetBeregning.Ja -> Avslag(
                            id,
                            opprettet,
                            sakId,
                            søknad,
                            oppgaveId,
                            behandlingsinformasjon,
                            fnr,
                            beregning
                        )
                        AvslagGrunnetBeregning.Nei -> Innvilget(
                            id,
                            opprettet,
                            sakId,
                            søknad,
                            oppgaveId,
                            behandlingsinformasjon,
                            fnr,
                            beregning
                        )
                    }
            }

            data class Innvilget(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val søknad: Søknad,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                override val beregning: Beregning
            ) : Beregnet() {
                override val status: Behandling.BehandlingsStatus = Behandling.BehandlingsStatus.BEREGNET_INNVILGET

                override fun accept(visitor: StatusovergangVisitor) {
                    visitor.visit(this)
                }
            }

            data class Avslag(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val søknad: Søknad,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                override val beregning: Beregning
            ) : Beregnet() {
                override val status: Behandling.BehandlingsStatus = Behandling.BehandlingsStatus.BEREGNET_AVSLAG
                val avslagsgrunn =
                    when (val avslag = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                        is AvslagGrunnetBeregning.Ja -> avslag.avslagsgrunn
                        AvslagGrunnetBeregning.Nei -> throw RuntimeException("Dette skal ikke være mulig")
                    }

                override fun accept(visitor: StatusovergangVisitor) {
                    visitor.visit(this)
                }
            }
        }

        data class Simulert(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val søknad: Søknad,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            val beregning: Beregning,
            val simulering: Simulering
        ) : Søknadsbehandling() {
            override val status: Behandling.BehandlingsStatus = Behandling.BehandlingsStatus.SIMULERT

            override fun accept(visitor: StatusovergangVisitor) {
                visitor.visit(this)
            }

            fun tilVilkårsvurdert(behandlingsinformasjon: Behandlingsinformasjon): Saksbehandling =
                Vilkårsvurdert.opprett(
                    id,
                    opprettet,
                    sakId,
                    søknad,
                    oppgaveId,
                    this.behandlingsinformasjon.patch(behandlingsinformasjon),
                    fnr
                )

            fun tilBeregnet(beregning: Beregning): Saksbehandling =
                Beregnet.opprett(id, opprettet, sakId, søknad, oppgaveId, behandlingsinformasjon, fnr, beregning)

            fun tilSimulert(simulering: Simulering): Saksbehandling =
                Simulert(id, opprettet, sakId, søknad, oppgaveId, behandlingsinformasjon, fnr, beregning, simulering)
        }

        sealed class TilAttestering : Søknadsbehandling() {
            abstract val saksbehandler: NavIdentBruker

            data class Innvilget(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val søknad: Søknad,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                val beregning: Beregning,
                val simulering: Simulering,
                override val saksbehandler: NavIdentBruker
            ) : TilAttestering() {
                override val status: Behandling.BehandlingsStatus =
                    Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET

                override fun accept(visitor: StatusovergangVisitor) {
                    visitor.visit(this)
                }
            }
        }

        sealed class Attestert : Søknadsbehandling() {
            abstract val attestering: Attestering

            data class Underkjent(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val søknad: Søknad,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                val beregning: Beregning,
                val simulering: Simulering,
                val saksbehandler: NavIdentBruker,
                override val attestering: Attestering
            ) : Attestert() {
                override val status: Behandling.BehandlingsStatus =
                    Behandling.BehandlingsStatus.UNDERKJENT_INNVILGET

                override fun accept(visitor: StatusovergangVisitor) {
                    visitor.visit(this)
                }
            }

            sealed class Iverksatt : Attestert() {
                data class Innvilget(
                    override val id: UUID,
                    override val opprettet: Tidspunkt,
                    override val sakId: UUID,
                    override val søknad: Søknad,
                    override val oppgaveId: OppgaveId,
                    override val behandlingsinformasjon: Behandlingsinformasjon,
                    override val fnr: Fnr,
                    val beregning: Beregning,
                    val simulering: Simulering,
                    val saksbehandler: NavIdentBruker,
                    override val attestering: Attestering,
                ) : Iverksatt() {
                    override val status: Behandling.BehandlingsStatus =
                        Behandling.BehandlingsStatus.IVERKSATT_INNVILGET

                    override fun accept(visitor: StatusovergangVisitor) {
                        visitor.visit(this)
                    }
                }

                data class OversendtOppdrag(
                    override val id: UUID,
                    override val opprettet: Tidspunkt,
                    override val sakId: UUID,
                    override val søknad: Søknad,
                    override val oppgaveId: OppgaveId,
                    override val behandlingsinformasjon: Behandlingsinformasjon,
                    override val fnr: Fnr,
                    val beregning: Beregning,
                    val simulering: Simulering,
                    val saksbehandler: NavIdentBruker,
                    override val attestering: Attestering,
                    val utbetaling: Utbetaling
                ) : Iverksatt() {
                    override val status: Behandling.BehandlingsStatus =
                        Behandling.BehandlingsStatus.IVERKSATT_INNVILGET

                    override fun accept(visitor: StatusovergangVisitor) {
                        visitor.visit(this)
                    }
                }
            }
        }
    }
}
