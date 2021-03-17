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
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
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
                fnr,
                grunnlagsdata,
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
                beregning,
                grunnlagsdata,
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
                grunnlagsdata: Grunnlagsdata,
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
                            fnr,
                            grunnlagsdata,
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
                            fnr,
                            grunnlagsdata,
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
                            fnr,
                            grunnlagsdata,
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
            override val fnr: Fnr,
            override val grunnlagsdata: Grunnlagsdata,
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
            override val fnr: Fnr,
            override val grunnlagsdata: Grunnlagsdata,
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
                    grunnlagsdata,
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
            override val fnr: Fnr,
            override val grunnlagsdata: Grunnlagsdata,
        ) : Vilkårsvurdert() {

            override val status: BehandlingsStatus = BehandlingsStatus.OPPRETTET

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }
        }
    }

    sealed class Beregnet : Søknadsbehandling() {
        abstract override val behandlingsinformasjon: Behandlingsinformasjon
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
                fnr,
                grunnlagsdata,
            )

        fun tilBeregnet(beregning: Beregning): Beregnet =
            opprett(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                behandlingsinformasjon,
                fnr,
                beregning,
                grunnlagsdata,
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
                simulering,
                grunnlagsdata,
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
                beregning: Beregning,
                grunnlagsdata: Grunnlagsdata,
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
                        beregning,
                        grunnlagsdata,
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
                        beregning,
                        grunnlagsdata,
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
            override val beregning: Beregning,
            override val grunnlagsdata: Grunnlagsdata,
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
            override val beregning: Beregning,
            override val grunnlagsdata: Grunnlagsdata,
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
                    grunnlagsdata,
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
        val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
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
                fnr,
                grunnlagsdata,
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
                beregning,
                grunnlagsdata,
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
                simulering,
                grunnlagsdata,
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
                grunnlagsdata,
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
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val grunnlagsdata: Grunnlagsdata,
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
                    attestering,
                    grunnlagsdata,
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
                    utbetalingId,
                    grunnlagsdata,
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
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val grunnlagsdata: Grunnlagsdata,
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
                        attestering,
                        grunnlagsdata,
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
                        grunnlagsdata,
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
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val grunnlagsdata: Grunnlagsdata,
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
                        attestering,
                        grunnlagsdata,
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
                        grunnlagsdata,
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
                fnr,
                grunnlagsdata,
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
            override val attestering: Attestering,
            override val grunnlagsdata: Grunnlagsdata,
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
                    beregning,
                    grunnlagsdata,
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
                    simulering,
                    grunnlagsdata,
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
                    grunnlagsdata,
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
                override val attestering: Attestering,
                override val grunnlagsdata: Grunnlagsdata,
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
                        beregning,
                        grunnlagsdata,
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
                        grunnlagsdata,
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
                override val attestering: Attestering,
                override val grunnlagsdata: Grunnlagsdata,
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
                        grunnlagsdata,
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
            override val grunnlagsdata: Grunnlagsdata,
            val eksterneIverksettingsteg: JournalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert
        ) : Iverksatt() {
            override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_INNVILGET

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, Innvilget> {
                return eksterneIverksettingsteg.journalfør(journalfør).map { copy(eksterneIverksettingsteg = it) }
            }

            fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, Innvilget> {
                return eksterneIverksettingsteg.distribuerBrev(distribuerBrev)
                    .map { copy(eksterneIverksettingsteg = it) }
            }
        }

        sealed class Avslag : Iverksatt(), ErAvslag {
            abstract val eksterneIverksettingsteg: JournalføringOgBrevdistribusjon
            abstract fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, Avslag>

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
                override val grunnlagsdata: Grunnlagsdata,
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

                override fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, MedBeregning> {
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
                override val grunnlagsdata: Grunnlagsdata,
                override val eksterneIverksettingsteg: JournalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert
            ) : Avslag() {
                override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_AVSLAG

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                override fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, UtenBeregning> {
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
