package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjonFeil
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.util.UUID

sealed class Søknadsbehandling : Behandling, Visitable<SøknadsbehandlingVisitor> {
    abstract val søknad: Søknad.Journalført.MedOppgave
    abstract val behandlingsinformasjon: Behandlingsinformasjon
    abstract val status: BehandlingsStatus

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
        ) : Vilkårsvurdert(), ErAvslag {

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

        fun tilVilkårsvurdert(behandlingsinformasjon: Behandlingsinformasjon): Vilkårsvurdert =
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

        fun tilBeregnet(beregning: Beregning): Beregnet =
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
        ) : Beregnet(), ErAvslag {
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

        fun tilVilkårsvurdert(behandlingsinformasjon: Behandlingsinformasjon): Vilkårsvurdert =
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

        sealed class Avslag : TilAttestering(), ErAvslag {
            final override val status: BehandlingsStatus =
                BehandlingsStatus.TIL_ATTESTERING_AVSLAG

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
                    attestering: Attestering
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
                    attestering: Attestering
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

        fun tilVilkårsvurdert(behandlingsinformasjon: Behandlingsinformasjon): Vilkårsvurdert =
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

        sealed class Avslag : Underkjent(), ErAvslag {
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
            ) : Avslag() {
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
            ) : Avslag() {
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
            val eksterneIverksettingsteg: JournalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert
        ) : Iverksatt() {
            override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_INNVILGET

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            fun journalfør(journalfør: () -> Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeJournalføre, Innvilget> {
                return eksterneIverksettingsteg.journalfør(journalfør).map { copy(eksterneIverksettingsteg = it) }
            }

            fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev, Innvilget> {
                return eksterneIverksettingsteg.distribuerBrev(distribuerBrev)
                    .map { copy(eksterneIverksettingsteg = it) }
            }
        }

        sealed class Avslag : Iverksatt(), ErAvslag {
            abstract val eksterneIverksettingsteg: JournalføringOgBrevdistribusjon
            abstract fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev, Avslag>

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
                override val eksterneIverksettingsteg: JournalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert
            ) : Avslag() {
                override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_AVSLAG

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                    when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                        is AvslagGrunnetBeregning.Ja -> listOf(vurdering.avslagsgrunn)
                        is AvslagGrunnetBeregning.Nei -> emptyList()
                    }

                override fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev, MedBeregning> {
                    return eksterneIverksettingsteg.distribuerBrev(distribuerBrev)
                        .map { copy(eksterneIverksettingsteg = it) }
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
                override val eksterneIverksettingsteg: JournalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert
            ) : Avslag() {
                override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_AVSLAG

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                override fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev, UtenBeregning> {
                    return eksterneIverksettingsteg.distribuerBrev(distribuerBrev)
                        .map { copy(eksterneIverksettingsteg = it) }
                }

                override val avslagsgrunner: List<Avslagsgrunn> = behandlingsinformasjon.utledAvslagsgrunner()
            }
        }
    }
}

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
