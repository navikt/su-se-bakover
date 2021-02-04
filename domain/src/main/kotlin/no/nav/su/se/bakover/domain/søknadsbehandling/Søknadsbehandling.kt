package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

sealed class Søknadsbehandling {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val sakId: UUID
    abstract val saksnummer: Saksnummer
    abstract val søknad: Søknad.Journalført.MedOppgave
    abstract val oppgaveId: OppgaveId
    abstract val behandlingsinformasjon: Behandlingsinformasjon
    abstract val status: BehandlingsStatus
    abstract val fnr: Fnr

    abstract fun accept(visitor: SøknadsbehandlingVisitor)

    sealed class Vilkårsvurdert : Søknadsbehandling() {
        fun tilVilkårsvurdert(behandlingsinformasjon: Behandlingsinformasjon): Vilkårsvurdert =
            opprett(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                this.behandlingsinformasjon.patch(behandlingsinformasjon),
                fnr
            )

        fun tilBeregnet(beregning: Beregning): Beregnet =
            Beregnet.opprett(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                behandlingsinformasjon,
                fnr,
                beregning
            )

        companion object {
            fun opprett(
                id: UUID,
                opprettet: Tidspunkt,
                sakId: UUID,
                saksnummer: Saksnummer,
                søknad: Søknad.Journalført.MedOppgave,
                oppgaveId: OppgaveId,
                behandlingsinformasjon: Behandlingsinformasjon,
                fnr: Fnr
            ): Vilkårsvurdert {
                return when {
                    behandlingsinformasjon.erInnvilget() -> {
                        Innvilget(
                            id,
                            opprettet,
                            sakId,
                            saksnummer,
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
                            saksnummer,
                            søknad,
                            oppgaveId,
                            behandlingsinformasjon,
                            fnr
                        )
                    }
                    else -> {
                        Uavklart(
                            id,
                            opprettet,
                            sakId,
                            saksnummer,
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
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr
        ) : Vilkårsvurdert() {

            override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_INNVILGET

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }
        }

        data class Avslag(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr
        ) : Vilkårsvurdert(), no.nav.su.se.bakover.domain.søknadsbehandling.Avslag {

            override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_AVSLAG

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler): TilAttestering.Avslag.UtenBeregning =
                TilAttestering.Avslag.UtenBeregning(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    saksbehandler,
                )

            override val avslagsgrunner: List<Avslagsgrunn> = behandlingsinformasjon.utledAvslagsgrunner()
        }

        data class Uavklart(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr
        ) : Vilkårsvurdert() {

            override val status: BehandlingsStatus = BehandlingsStatus.OPPRETTET

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }
        }
    }

    sealed class Beregnet : Søknadsbehandling() {
        abstract override val id: UUID
        abstract override val opprettet: Tidspunkt
        abstract override val sakId: UUID
        abstract override val søknad: Søknad.Journalført.MedOppgave
        abstract override val oppgaveId: OppgaveId
        abstract override val behandlingsinformasjon: Behandlingsinformasjon
        abstract override val fnr: Fnr
        abstract val beregning: Beregning

        fun tilVilkårsvurdert(behandlingsinformasjon: Behandlingsinformasjon): Søknadsbehandling =
            Vilkårsvurdert.opprett(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                this.behandlingsinformasjon.patch(behandlingsinformasjon),
                fnr
            )

        fun tilBeregnet(beregning: Beregning): Søknadsbehandling =
            opprett(id, opprettet, sakId, saksnummer, søknad, oppgaveId, behandlingsinformasjon, fnr, beregning)

        fun tilSimulert(simulering: Simulering): Simulert =
            Simulert(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                behandlingsinformasjon,
                fnr,
                beregning,
                simulering
            )

        companion object {
            fun opprett(
                id: UUID,
                opprettet: Tidspunkt,
                sakId: UUID,
                saksnummer: Saksnummer,
                søknad: Søknad.Journalført.MedOppgave,
                oppgaveId: OppgaveId,
                behandlingsinformasjon: Behandlingsinformasjon,
                fnr: Fnr,
                beregning: Beregning
            ): Beregnet =
                when (VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> Avslag(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
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
                        saksnummer,
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
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            override val beregning: Beregning
        ) : Beregnet() {
            override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET_INNVILGET

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }
        }

        data class Avslag(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            override val beregning: Beregning
        ) : Beregnet(), no.nav.su.se.bakover.domain.søknadsbehandling.Avslag {
            override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET_AVSLAG

            private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> listOf(vurdering.avslagsgrunn)
                    is AvslagGrunnetBeregning.Nei -> emptyList()
                }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler): TilAttestering.Avslag.MedBeregning =
                TilAttestering.Avslag.MedBeregning(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    beregning,
                    saksbehandler,
                )

            override val avslagsgrunner: List<Avslagsgrunn> =
                behandlingsinformasjon.utledAvslagsgrunner() + avslagsgrunnForBeregning
        }
    }

    data class Simulert(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val søknad: Søknad.Journalført.MedOppgave,
        override val oppgaveId: OppgaveId,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        override val fnr: Fnr,
        val beregning: Beregning,
        val simulering: Simulering
    ) : Søknadsbehandling() {
        override val status: BehandlingsStatus = BehandlingsStatus.SIMULERT

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        fun tilVilkårsvurdert(behandlingsinformasjon: Behandlingsinformasjon): Søknadsbehandling =
            Vilkårsvurdert.opprett(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                this.behandlingsinformasjon.patch(behandlingsinformasjon),
                fnr
            )

        fun tilBeregnet(beregning: Beregning): Søknadsbehandling =
            Beregnet.opprett(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                behandlingsinformasjon,
                fnr,
                beregning
            )

        fun tilSimulert(simulering: Simulering): Søknadsbehandling =
            Simulert(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                behandlingsinformasjon,
                fnr,
                beregning,
                simulering
            )

        fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler): TilAttestering.Innvilget =
            TilAttestering.Innvilget(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                behandlingsinformasjon,
                fnr,
                beregning,
                simulering,
                saksbehandler,
            )
    }

    sealed class TilAttestering : Søknadsbehandling() {
        abstract val saksbehandler: NavIdentBruker
        abstract fun nyOppgaveId(nyOppgaveId: OppgaveId): TilAttestering
        abstract fun tilUnderkjent(attestering: Attestering): Underkjent

        data class Innvilget(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            val beregning: Beregning,
            val simulering: Simulering,
            override val saksbehandler: NavIdentBruker.Saksbehandler
        ) : TilAttestering() {
            override val status: BehandlingsStatus =
                BehandlingsStatus.TIL_ATTESTERING_INNVILGET

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            override fun nyOppgaveId(nyOppgaveId: OppgaveId): Innvilget {
                return this.copy(oppgaveId = nyOppgaveId)
            }

            override fun tilUnderkjent(attestering: Attestering): Underkjent.Innvilget {
                return Underkjent.Innvilget(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    beregning,
                    simulering,
                    saksbehandler,
                    attestering
                )
            }

            fun tilIverksatt(attestering: Attestering, utbetalingId: UUID30): Iverksatt.Innvilget {
                return Iverksatt.Innvilget(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    beregning,
                    simulering,
                    saksbehandler,
                    attestering,
                    utbetalingId
                )
            }
        }

        sealed class Avslag : TilAttestering() {
            final override val status: BehandlingsStatus =
                BehandlingsStatus.TIL_ATTESTERING_AVSLAG

            abstract val avslagsgrunner: List<Avslagsgrunn>

            data class UtenBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                override val saksbehandler: NavIdentBruker.Saksbehandler
            ) : Avslag() {

                override val avslagsgrunner = behandlingsinformasjon.utledAvslagsgrunner()

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                override fun nyOppgaveId(nyOppgaveId: OppgaveId): UtenBeregning {
                    return this.copy(oppgaveId = nyOppgaveId)
                }

                override fun tilUnderkjent(attestering: Attestering): Underkjent.Avslag.UtenBeregning {
                    return Underkjent.Avslag.UtenBeregning(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        saksbehandler,
                        attestering
                    )
                }

                fun tilIverksatt(
                    attestering: Attestering,
                    eksterneIverksettingsteg: Iverksatt.Avslag.EksterneIverksettingsteg
                ): Iverksatt.Avslag.UtenBeregning {
                    return Iverksatt.Avslag.UtenBeregning(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        saksbehandler,
                        attestering,
                        eksterneIverksettingsteg
                    )
                }
            }

            data class MedBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                val beregning: Beregning,
                override val saksbehandler: NavIdentBruker.Saksbehandler
            ) : Avslag() {

                private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                    when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                        is AvslagGrunnetBeregning.Ja -> listOf(vurdering.avslagsgrunn)
                        is AvslagGrunnetBeregning.Nei -> emptyList()
                    }

                override val avslagsgrunner =
                    behandlingsinformasjon.utledAvslagsgrunner() + avslagsgrunnForBeregning

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                override fun nyOppgaveId(nyOppgaveId: OppgaveId): MedBeregning {
                    return this.copy(oppgaveId = nyOppgaveId)
                }

                override fun tilUnderkjent(attestering: Attestering): Underkjent.Avslag.MedBeregning {
                    return Underkjent.Avslag.MedBeregning(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        beregning,
                        saksbehandler,
                        attestering
                    )
                }

                fun tilIverksatt(
                    attestering: Attestering,
                    eksterneIverksettingsteg: Iverksatt.Avslag.EksterneIverksettingsteg
                ): Iverksatt.Avslag.MedBeregning {
                    return Iverksatt.Avslag.MedBeregning(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        beregning,
                        saksbehandler,
                        attestering,
                        eksterneIverksettingsteg
                    )
                }
            }
        }
    }

    sealed class Underkjent : Søknadsbehandling() {
        abstract override val id: UUID
        abstract override val opprettet: Tidspunkt
        abstract override val sakId: UUID
        abstract override val saksnummer: Saksnummer
        abstract override val søknad: Søknad.Journalført.MedOppgave
        abstract override val oppgaveId: OppgaveId
        abstract override val behandlingsinformasjon: Behandlingsinformasjon
        abstract override val fnr: Fnr
        abstract val saksbehandler: NavIdentBruker.Saksbehandler
        abstract val attestering: Attestering

        abstract fun nyOppgaveId(nyOppgaveId: OppgaveId): Underkjent

        fun tilVilkårsvurdert(behandlingsinformasjon: Behandlingsinformasjon): Søknadsbehandling =
            Vilkårsvurdert.opprett(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                this.behandlingsinformasjon.patch(behandlingsinformasjon),
                fnr
            )

        data class Innvilget(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            val beregning: Beregning,
            val simulering: Simulering,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestering: Attestering
        ) : Underkjent() {

            override fun nyOppgaveId(nyOppgaveId: OppgaveId): Innvilget {
                return this.copy(oppgaveId = nyOppgaveId)
            }

            override val status: BehandlingsStatus =
                BehandlingsStatus.UNDERKJENT_INNVILGET

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            fun tilBeregnet(beregning: Beregning): Beregnet =
                Beregnet.opprett(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    beregning
                )

            fun tilSimulert(simulering: Simulering): Simulert =
                Simulert(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    beregning,
                    simulering
                )

            fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler): TilAttestering.Innvilget =
                TilAttestering.Innvilget(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    beregning,
                    simulering,
                    saksbehandler,
                )
        }

        sealed class Avslag : Underkjent() {
            data class MedBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                val beregning: Beregning,
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val attestering: Attestering
            ) : Avslag(), no.nav.su.se.bakover.domain.søknadsbehandling.Avslag {
                override val status: BehandlingsStatus =
                    BehandlingsStatus.UNDERKJENT_AVSLAG

                private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                    when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                        is AvslagGrunnetBeregning.Ja -> listOf(vurdering.avslagsgrunn)
                        is AvslagGrunnetBeregning.Nei -> emptyList()
                    }

                override fun nyOppgaveId(nyOppgaveId: OppgaveId): MedBeregning {
                    return this.copy(oppgaveId = nyOppgaveId)
                }

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                fun tilBeregnet(beregning: Beregning): Beregnet =
                    Beregnet.opprett(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        beregning
                    )

                fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler): TilAttestering.Avslag.MedBeregning =
                    TilAttestering.Avslag.MedBeregning(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        beregning,
                        saksbehandler,
                    )

                override val avslagsgrunner: List<Avslagsgrunn> =
                    behandlingsinformasjon.utledAvslagsgrunner() + avslagsgrunnForBeregning
            }

            data class UtenBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val attestering: Attestering
            ) : Avslag(), no.nav.su.se.bakover.domain.søknadsbehandling.Avslag {
                override val status: BehandlingsStatus =
                    BehandlingsStatus.UNDERKJENT_AVSLAG

                override fun nyOppgaveId(nyOppgaveId: OppgaveId): UtenBeregning {
                    return this.copy(oppgaveId = nyOppgaveId)
                }

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler): TilAttestering.Avslag.UtenBeregning =
                    TilAttestering.Avslag.UtenBeregning(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        saksbehandler,
                    )

                override val avslagsgrunner: List<Avslagsgrunn> = behandlingsinformasjon.utledAvslagsgrunner()
            }
        }
    }

    sealed class Iverksatt : Søknadsbehandling() {
        abstract override val id: UUID
        abstract override val opprettet: Tidspunkt
        abstract override val sakId: UUID
        abstract override val saksnummer: Saksnummer
        abstract override val søknad: Søknad.Journalført.MedOppgave
        abstract override val oppgaveId: OppgaveId
        abstract override val behandlingsinformasjon: Behandlingsinformasjon
        abstract override val fnr: Fnr
        abstract val saksbehandler: NavIdentBruker.Saksbehandler
        abstract val attestering: Attestering

        sealed class KunneIkkeDistribuereBrev {
            object MåJournalføresFørst : KunneIkkeDistribuereBrev()
            data class AlleredeDistribuertBrev(val journalpostId: JournalpostId) : KunneIkkeDistribuereBrev()
            data class FeilVedDistribueringAvBrev(val journalpostId: JournalpostId) : KunneIkkeDistribuereBrev()
        }

        sealed class KunneIkkeJournalføre {
            data class AlleredeJournalført(val journalpostId: JournalpostId) : KunneIkkeJournalføre()
            object FeilVedJournalføring : KunneIkkeJournalføre()
        }

        // abstract fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeDistribuereBrev, Iverksatt>

        data class Innvilget(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            val beregning: Beregning,
            val simulering: Simulering,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestering: Attestering,
            val utbetalingId: UUID30,
            val eksterneIverksettingsteg: EksterneIverksettingsteg = EksterneIverksettingsteg.VenterPåKvittering
        ) : Iverksatt() {
            override val status: BehandlingsStatus =
                BehandlingsStatus.IVERKSATT_INNVILGET

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            fun journalfør(journalfør: () -> Either<KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføre, Innvilget> {
                return when (eksterneIverksettingsteg) {
                    is EksterneIverksettingsteg.VenterPåKvittering -> journalfør().map {
                        this.copy(
                            eksterneIverksettingsteg = EksterneIverksettingsteg.VenterPåKvittering.medJournalpost(
                                it
                            )
                        )
                    }
                    is EksterneIverksettingsteg.Journalført -> KunneIkkeJournalføre.AlleredeJournalført(
                        eksterneIverksettingsteg.journalpostId
                    ).left()
                    is EksterneIverksettingsteg.JournalførtOgDistribuertBrev -> KunneIkkeJournalføre.AlleredeJournalført(
                        eksterneIverksettingsteg.journalpostId
                    ).left()
                }
            }

            fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeDistribuereBrev, Innvilget> {
                return when (eksterneIverksettingsteg) {
                    is EksterneIverksettingsteg.VenterPåKvittering -> KunneIkkeDistribuereBrev.MåJournalføresFørst.left()
                    is EksterneIverksettingsteg.Journalført -> distribuerBrev(eksterneIverksettingsteg.journalpostId).map {
                        this.copy(eksterneIverksettingsteg = eksterneIverksettingsteg.medDistribuertBrev(it))
                    }
                    is EksterneIverksettingsteg.JournalførtOgDistribuertBrev -> KunneIkkeDistribuereBrev.AlleredeDistribuertBrev(
                        eksterneIverksettingsteg.journalpostId
                    ).left()
                }
            }

            sealed class EksterneIverksettingsteg {
                abstract fun journalpostId(): JournalpostId?

                object VenterPåKvittering : EksterneIverksettingsteg() {
                    fun medJournalpost(journalpostId: JournalpostId): Journalført {
                        return Journalført(
                            journalpostId
                        )
                    }

                    override fun journalpostId(): JournalpostId? = null
                }

                data class Journalført(val journalpostId: JournalpostId) : EksterneIverksettingsteg() {
                    fun medDistribuertBrev(brevbestillingId: BrevbestillingId): JournalførtOgDistribuertBrev {
                        return JournalførtOgDistribuertBrev(
                            journalpostId,
                            brevbestillingId
                        )
                    }

                    override fun journalpostId() = journalpostId
                }

                data class JournalførtOgDistribuertBrev(
                    val journalpostId: JournalpostId,
                    val brevbestillingId: BrevbestillingId
                ) : EksterneIverksettingsteg() {
                    override fun journalpostId() = journalpostId
                }
            }
        }

        sealed class Avslag : Iverksatt(), no.nav.su.se.bakover.domain.søknadsbehandling.Avslag {
            abstract val eksterneIverksettingsteg: EksterneIverksettingsteg
            abstract fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeDistribuereBrev, Avslag>

            data class MedBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                val beregning: Beregning,
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val attestering: Attestering,
                override val eksterneIverksettingsteg: EksterneIverksettingsteg
            ) : Avslag(), no.nav.su.se.bakover.domain.søknadsbehandling.Avslag {
                override val status: BehandlingsStatus =
                    BehandlingsStatus.IVERKSATT_AVSLAG

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                    when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                        is AvslagGrunnetBeregning.Ja -> listOf(vurdering.avslagsgrunn)
                        is AvslagGrunnetBeregning.Nei -> emptyList()
                    }

                override fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeDistribuereBrev, MedBeregning> {
                    return when (eksterneIverksettingsteg) {
                        is EksterneIverksettingsteg.Journalført -> distribuerBrev(eksterneIverksettingsteg.journalpostId).map {
                            this.copy(eksterneIverksettingsteg = eksterneIverksettingsteg.medDistribuertBrev(it))
                        }
                        is EksterneIverksettingsteg.JournalførtOgDistribuertBrev -> KunneIkkeDistribuereBrev.AlleredeDistribuertBrev(
                            eksterneIverksettingsteg.journalpostId
                        ).left()
                    }
                }

                override val avslagsgrunner: List<Avslagsgrunn> =
                    behandlingsinformasjon.utledAvslagsgrunner() + avslagsgrunnForBeregning
            }

            data class UtenBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val attestering: Attestering,
                override val eksterneIverksettingsteg: EksterneIverksettingsteg
            ) : Avslag(), no.nav.su.se.bakover.domain.søknadsbehandling.Avslag {
                override val status: BehandlingsStatus =
                    BehandlingsStatus.IVERKSATT_AVSLAG

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                override fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeDistribuereBrev, UtenBeregning> {
                    return when (eksterneIverksettingsteg) {
                        is EksterneIverksettingsteg.Journalført -> distribuerBrev(eksterneIverksettingsteg.journalpostId).map {
                            this.copy(eksterneIverksettingsteg = eksterneIverksettingsteg.medDistribuertBrev(it))
                        }
                        is EksterneIverksettingsteg.JournalførtOgDistribuertBrev -> KunneIkkeDistribuereBrev.AlleredeDistribuertBrev(
                            eksterneIverksettingsteg.journalpostId
                        ).left()
                    }
                }

                override val avslagsgrunner: List<Avslagsgrunn> = behandlingsinformasjon.utledAvslagsgrunner()
            }

            sealed class EksterneIverksettingsteg {
                abstract val journalpostId: JournalpostId

                data class Journalført(override val journalpostId: JournalpostId) : EksterneIverksettingsteg() {
                    fun medDistribuertBrev(brevbestillingId: BrevbestillingId): JournalførtOgDistribuertBrev {
                        return JournalførtOgDistribuertBrev(journalpostId, brevbestillingId)
                    }
                }

                data class JournalførtOgDistribuertBrev(
                    override val journalpostId: JournalpostId,
                    val brevbestillingId: BrevbestillingId
                ) : EksterneIverksettingsteg()
            }
        }
    }
}

// TODO jah, jm: Fjern på sikt?
enum class BehandlingsStatus {
    OPPRETTET,
    VILKÅRSVURDERT_INNVILGET,
    VILKÅRSVURDERT_AVSLAG,
    BEREGNET_INNVILGET,
    BEREGNET_AVSLAG,
    SIMULERT,
    TIL_ATTESTERING_INNVILGET,
    TIL_ATTESTERING_AVSLAG,
    UNDERKJENT_INNVILGET,
    UNDERKJENT_AVSLAG,
    IVERKSATT_INNVILGET,
    IVERKSATT_AVSLAG,
}
