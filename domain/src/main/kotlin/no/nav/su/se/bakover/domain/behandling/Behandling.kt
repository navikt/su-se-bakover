package no.nav.su.se.bakover.domain.behandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.Hendelse
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

data class Behandling internal constructor(
    private val behandlingMetrics: BehandlingMetrics,
    val id: UUID,
    val opprettet: Tidspunkt,
    private var behandlingsinformasjon: Behandlingsinformasjon,
    val søknad: Søknad.Journalført.MedOppgave,
    private var beregning: Beregning?,
    private var simulering: Simulering?,
    private var status: BehandlingsStatus,
    private var saksbehandler: Saksbehandler?,
    private var attestering: Attestering?,
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val hendelseslogg: Hendelseslogg,
    val fnr: Fnr,
    private var oppgaveId: OppgaveId,
    private var iverksattJournalpostId: JournalpostId?,
    private var iverksattBrevbestillingId: BrevbestillingId?,
) {

    private var tilstand: Tilstand = resolve(status)

    fun status(): BehandlingsStatus = tilstand.status

    fun saksbehandler(): Saksbehandler? = saksbehandler

    fun attestering(): Attestering? = attestering

    fun beregning(): Beregning? = beregning

    fun behandlingsinformasjon(): Behandlingsinformasjon = behandlingsinformasjon

    fun simulering(): Simulering? = simulering

    fun hendelser(): List<Hendelse> = hendelseslogg.hendelser()

    fun oppgaveId(): OppgaveId = oppgaveId

    fun iverksattJournalpostId(): JournalpostId? = iverksattJournalpostId

    fun iverksattBrevbestillingId(): BrevbestillingId? = iverksattBrevbestillingId

    fun getUtledetSatsBeløp(fraDato: LocalDate): Int? {
        if (status == BehandlingsStatus.VILKÅRSVURDERT_INNVILGET ||
            status == BehandlingsStatus.BEREGNET_INNVILGET ||
            status == BehandlingsStatus.SIMULERT
        ) {
            return behandlingsinformasjon().bosituasjon?.utledSats()?.årsbeløp(fraDato)?.roundToInt()
        }
        return null
    }

    private fun resolve(status: BehandlingsStatus): Tilstand = when (status) {
        BehandlingsStatus.OPPRETTET -> Opprettet()
        BehandlingsStatus.VILKÅRSVURDERT_INNVILGET -> Vilkårsvurdert().Innvilget()
        BehandlingsStatus.VILKÅRSVURDERT_AVSLAG -> Vilkårsvurdert().Avslag()
        BehandlingsStatus.BEREGNET_INNVILGET -> Beregnet()
        BehandlingsStatus.BEREGNET_AVSLAG -> Beregnet().Avslag()
        BehandlingsStatus.SIMULERT -> Simulert()
        BehandlingsStatus.TIL_ATTESTERING_INNVILGET -> TilAttestering().Innvilget()
        BehandlingsStatus.TIL_ATTESTERING_AVSLAG -> TilAttestering().Avslag()
        BehandlingsStatus.UNDERKJENT_INNVILGET -> Underkjent().Innvilget()
        BehandlingsStatus.UNDERKJENT_AVSLAG -> Underkjent().Avslag()
        BehandlingsStatus.IVERKSATT_INNVILGET -> Iverksatt().Innvilget()
        BehandlingsStatus.IVERKSATT_AVSLAG -> Iverksatt().Avslag()
    }

    fun erInnvilget() = listOf(
        BehandlingsStatus.SIMULERT,
        BehandlingsStatus.BEREGNET_INNVILGET,
        BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
        BehandlingsStatus.IVERKSATT_INNVILGET,
        BehandlingsStatus.UNDERKJENT_INNVILGET
    ).contains(status)

    fun erAvslag() = listOf(
        BehandlingsStatus.VILKÅRSVURDERT_AVSLAG,
        BehandlingsStatus.BEREGNET_AVSLAG,
        BehandlingsStatus.TIL_ATTESTERING_AVSLAG,
        BehandlingsStatus.IVERKSATT_AVSLAG,
        BehandlingsStatus.UNDERKJENT_AVSLAG
    ).contains(status)

    fun oppdaterBehandlingsinformasjon(
        saksbehandler: Saksbehandler,
        oppdatert: Behandlingsinformasjon
    ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
        return tilstand.oppdaterBehandlingsinformasjon(saksbehandler, oppdatert)
    }

    fun opprettBeregning(
        saksbehandler: Saksbehandler,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag> = emptyList()
    ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
        return tilstand.opprettBeregning(saksbehandler, fraOgMed, tilOgMed, fradrag)
    }

    fun leggTilSimulering(
        saksbehandler: Saksbehandler,
        simulering: () -> Simulering?
    ): Either<KunneIkkeLeggeTilSimulering, Behandling> {
        return tilstand.leggTilSimulering(saksbehandler, simulering)
    }

    fun sendTilAttestering(
        saksbehandler: Saksbehandler
    ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
        return tilstand.sendTilAttestering(saksbehandler)
    }

    fun oppdaterOppgaveId(
        oppgaveId: OppgaveId
    ): Behandling {
        return tilstand.oppdaterOppgaveId(oppgaveId)
    }

    fun iverksett(
        attestant: NavIdentBruker.Attestant
    ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
        return tilstand.iverksett(attestant)
    }

    fun oppdaterIverksattJournalpostId(journalpostId: JournalpostId): Behandling {
        return tilstand.oppdaterIverksattJournalpostId(journalpostId)
    }

    fun oppdaterIverksattBrevbestillingId(brevbestillingId: BrevbestillingId): Behandling {
        return tilstand.oppdaterIverksattBrevbestillingId(brevbestillingId)
    }

    fun underkjenn(
        attestering: Attestering.Underkjent
    ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
        return tilstand.underkjenn(attestering)
    }

    fun utledAvslagsgrunner(): List<Avslagsgrunn> {
        return tilstand.utledAvslagsgrunner()
    }

    interface Tilstand {

        val status: BehandlingsStatus

        fun oppdaterBehandlingsinformasjon(
            saksbehandler: Saksbehandler,
            oppdatert: Behandlingsinformasjon
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            throw TilstandException(status, this::oppdaterBehandlingsinformasjon.toString())
        }

        fun opprettBeregning(
            saksbehandler: Saksbehandler,
            fraOgMed: LocalDate,
            tilOgMed: LocalDate,
            fradrag: List<Fradrag>
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            throw TilstandException(status, this::opprettBeregning.toString())
        }

        fun leggTilSimulering(
            saksbehandler: Saksbehandler,
            simulering: () -> Simulering?
        ): Either<KunneIkkeLeggeTilSimulering, Behandling> {
            throw TilstandException(status, this::leggTilSimulering.toString())
        }

        fun sendTilAttestering(
            saksbehandler: Saksbehandler
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            throw TilstandException(status, this::sendTilAttestering.toString())
        }

        fun oppdaterOppgaveId(
            oppgaveId: OppgaveId
        ): Behandling {
            throw TilstandException(status, this::oppdaterOppgaveId.toString())
        }

        fun iverksett(
            attestant: NavIdentBruker.Attestant
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            throw TilstandException(status, this::iverksett.toString())
        }

        fun oppdaterIverksattJournalpostId(journalpostId: JournalpostId): Behandling {
            throw TilstandException(status, this::oppdaterOppgaveId.toString())
        }

        fun oppdaterIverksattBrevbestillingId(brevbestillingId: BrevbestillingId): Behandling {
            throw TilstandException(status, this::oppdaterOppgaveId.toString())
        }

        fun underkjenn(
            attestering: Attestering.Underkjent
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            throw TilstandException(status, this::underkjenn.toString())
        }

        fun utledAvslagsgrunner(): List<Avslagsgrunn> {
            throw TilstandException(status, this::utledAvslagsgrunner.toString())
        }
    }

    private fun nyTilstand(target: Tilstand): Tilstand {
        behandlingMetrics.behandlingsstatusChanged(status, target.status)
        status = target.status
        tilstand = resolve(status)
        return tilstand
    }

    private inner class Opprettet : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.OPPRETTET

        override fun oppdaterBehandlingsinformasjon(
            saksbehandler: Saksbehandler,
            oppdatert: Behandlingsinformasjon
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            if (erAttestantOgSakbehandlerSammePerson(saksbehandler)) return AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            if (this@Behandling.beregning != null) {
                this@Behandling.beregning = null
            }

            behandlingsinformasjon =
                behandlingsinformasjon.patch(oppdatert) // TODO we need to discuss how to divide responsibility between service and domain.
            // er samme logikk som brukes fra Underkjent::opprettBeregning
            if (behandlingsinformasjon.erInnvilget()) {
                nyTilstand(Vilkårsvurdert().Innvilget())
            } else if (behandlingsinformasjon.erAvslag()) {
                nyTilstand(Vilkårsvurdert().Avslag())
            }
            return this@Behandling.right()
        }
    }

    private open inner class Vilkårsvurdert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_INNVILGET

        override fun oppdaterBehandlingsinformasjon(
            saksbehandler: Saksbehandler,
            oppdatert: Behandlingsinformasjon
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(saksbehandler, oppdatert)
            return this@Behandling.right()
        }

        inner class Innvilget : Vilkårsvurdert() {
            override fun opprettBeregning(
                saksbehandler: Saksbehandler,
                fraOgMed: LocalDate,
                tilOgMed: LocalDate,
                fradrag: List<Fradrag>
            ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
                if (erAttestantOgSakbehandlerSammePerson(saksbehandler)) {
                    return AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                }

                val beregningsperiode = Periode(fraOgMed, tilOgMed)
                val beregningsgrunnlag = Beregningsgrunnlag(
                    periode = beregningsperiode,
                    forventetInntektPerÅr = behandlingsinformasjon.uførhet?.forventetInntekt?.toDouble() ?: 0.0,
                    fradragFraSaksbehandler = fradrag
                )

                val strategy = this@Behandling.behandlingsinformasjon.bosituasjon!!.getBeregningStrategy()
                beregning = strategy.beregn(beregningsgrunnlag)

                if (vurderAvslagGrunnetBeregning(beregning!!) is AvslagGrunnetBeregning.Ja) {
                    nyTilstand(Beregnet().Avslag())
                    return this@Behandling.right()
                }

                nyTilstand(Beregnet())
                return this@Behandling.right()
            }
        }

        inner class Avslag : Vilkårsvurdert() {
            override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_AVSLAG

            override fun sendTilAttestering(
                saksbehandler: Saksbehandler,
            ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
                this@Behandling.saksbehandler = saksbehandler
                nyTilstand(TilAttestering().Avslag())
                return this@Behandling.right()
            }

            override fun utledAvslagsgrunner(): List<Avslagsgrunn> {
                return behandlingsinformasjon().utledAvslagsgrunner()
            }
        }
    }

    private open inner class Beregnet : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET_INNVILGET

        override fun opprettBeregning(
            saksbehandler: Saksbehandler,
            fraOgMed: LocalDate,
            tilOgMed: LocalDate,
            fradrag: List<Fradrag>
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            return nyTilstand(Vilkårsvurdert()).opprettBeregning(saksbehandler, fraOgMed, tilOgMed, fradrag)
        }

        override fun oppdaterBehandlingsinformasjon(
            saksbehandler: Saksbehandler,
            oppdatert: Behandlingsinformasjon
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            return nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(saksbehandler, oppdatert)
        }

        override fun leggTilSimulering(
            saksbehandler: Saksbehandler,
            simulering: () -> Simulering?
        ): Either<KunneIkkeLeggeTilSimulering, Behandling> {
            if (erAttestantOgSakbehandlerSammePerson(saksbehandler)) {
                return KunneIkkeLeggeTilSimulering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }

            simulering()?.let {
                this@Behandling.simulering = it
                nyTilstand(Simulert())
                return this@Behandling.right()
            }
                ?: return KunneIkkeLeggeTilSimulering.KunneIkkeSimulere.left()
        }

        inner class Avslag : Beregnet() {
            override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET_AVSLAG
            override fun leggTilSimulering(
                saksbehandler: Saksbehandler,
                simulering: () -> Simulering?
            ): Either<KunneIkkeLeggeTilSimulering, Behandling> {
                throw TilstandException(status, this::leggTilSimulering.toString())
            }

            override fun sendTilAttestering(
                saksbehandler: Saksbehandler,
            ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
                this@Behandling.saksbehandler = saksbehandler
                nyTilstand(TilAttestering().Avslag())
                return this@Behandling.right()
            }

            override fun utledAvslagsgrunner(): List<Avslagsgrunn> =
                behandlingsinformasjon().utledAvslagsgrunner() + utledAvslagsgrunnForBeregning()
        }
    }

    sealed class KunneIkkeLeggeTilSimulering {
        object KunneIkkeSimulere : KunneIkkeLeggeTilSimulering()
        object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeLeggeTilSimulering()
    }

    private inner class Simulert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.SIMULERT

        override fun sendTilAttestering(
            saksbehandler: Saksbehandler
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            if (erAttestantOgSakbehandlerSammePerson(saksbehandler)) {
                return AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            this@Behandling.saksbehandler = saksbehandler
            nyTilstand(TilAttestering().Innvilget())
            return this@Behandling.right()
        }

        override fun oppdaterOppgaveId(
            oppgaveId: OppgaveId
        ): Behandling {
            this@Behandling.oppgaveId = oppgaveId
            return this@Behandling
        }

        override fun opprettBeregning(
            saksbehandler: Saksbehandler,
            fraOgMed: LocalDate,
            tilOgMed: LocalDate,
            fradrag: List<Fradrag>
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            return nyTilstand(Vilkårsvurdert().Innvilget()).opprettBeregning(saksbehandler, fraOgMed, tilOgMed, fradrag)
        }

        override fun oppdaterBehandlingsinformasjon(
            saksbehandler: Saksbehandler,
            oppdatert: Behandlingsinformasjon
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            return nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(saksbehandler, oppdatert)
        }

        override fun leggTilSimulering(
            saksbehandler: Saksbehandler,
            simulering: () -> Simulering?
        ): Either<KunneIkkeLeggeTilSimulering, Behandling> {
            return nyTilstand(Beregnet()).leggTilSimulering(saksbehandler, simulering)
        }
    }

    private fun erAttestantOgSakbehandlerSammePerson(saksbehandler: Saksbehandler): Boolean {
        return this@Behandling.attestering?.let {
            it.attestant.navIdent == saksbehandler.navIdent
        } ?: false
    }

    private open inner class TilAttestering : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING_INNVILGET

        inner class Innvilget : TilAttestering() {
            override fun iverksett(
                attestant: NavIdentBruker.Attestant
            ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
                if (attestant.navIdent == this@Behandling.saksbehandler?.navIdent) {
                    return AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                }
                this@Behandling.attestering = Attestering.Iverksatt(attestant)
                nyTilstand(Iverksatt().Innvilget())
                return this@Behandling.right()
            }

            override fun underkjenn(
                attestering: Attestering.Underkjent
            ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
                if (attestering.attestant.navIdent == this@Behandling.saksbehandler?.navIdent) {
                    return AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                }

                this@Behandling.attestering = attestering
                nyTilstand(Underkjent().Innvilget())
                return this@Behandling.right()
            }
        }

        inner class Avslag : TilAttestering() {
            override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING_AVSLAG
            override fun iverksett(
                attestant: NavIdentBruker.Attestant
            ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
                if (attestant.navIdent == this@Behandling.saksbehandler?.navIdent) {
                    return AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                }
                this@Behandling.attestering = Attestering.Iverksatt(attestant)
                nyTilstand(Iverksatt().Avslag())
                return this@Behandling.right()
            }

            override fun underkjenn(
                attestering: Attestering.Underkjent
            ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
                if (attestering.attestant.navIdent == this@Behandling.saksbehandler?.navIdent) {
                    return AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                }

                this@Behandling.attestering = attestering
                nyTilstand(Underkjent().Avslag())
                return this@Behandling.right()
            }

            override fun utledAvslagsgrunner(): List<Avslagsgrunn> =
                behandlingsinformasjon().utledAvslagsgrunner() + utledAvslagsgrunnForBeregning()
        }

        override fun oppdaterOppgaveId(
            oppgaveId: OppgaveId
        ): Behandling {
            this@Behandling.oppgaveId = oppgaveId
            return this@Behandling
        }
    }

    private open inner class Underkjent : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.UNDERKJENT_INNVILGET

        override fun oppdaterBehandlingsinformasjon(
            saksbehandler: Saksbehandler,
            oppdatert: Behandlingsinformasjon
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            return nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(saksbehandler, oppdatert)
        }

        override fun oppdaterOppgaveId(
            oppgaveId: OppgaveId
        ): Behandling {
            this@Behandling.oppgaveId = oppgaveId
            return this@Behandling
        }

        inner class Innvilget : Underkjent() {
            override val status: BehandlingsStatus = BehandlingsStatus.UNDERKJENT_INNVILGET

            override fun sendTilAttestering(
                saksbehandler: Saksbehandler
            ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
                if (erAttestantOgSakbehandlerSammePerson(saksbehandler)) {
                    return AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                }
                this@Behandling.saksbehandler = saksbehandler
                nyTilstand(TilAttestering().Innvilget())
                return this@Behandling.right()
            }

            override fun opprettBeregning(
                saksbehandler: Saksbehandler,
                fraOgMed: LocalDate,
                tilOgMed: LocalDate,
                fradrag: List<Fradrag>
            ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
                return nyTilstand(Vilkårsvurdert().Innvilget()).opprettBeregning(
                    saksbehandler,
                    fraOgMed,
                    tilOgMed,
                    fradrag
                )
            }

            override fun leggTilSimulering(
                saksbehandler: Saksbehandler,
                simulering: () -> Simulering?
            ): Either<KunneIkkeLeggeTilSimulering, Behandling> {
                return nyTilstand(Beregnet()).leggTilSimulering(saksbehandler, simulering)
            }
        }

        inner class Avslag : Underkjent() {
            override val status: BehandlingsStatus = BehandlingsStatus.UNDERKJENT_AVSLAG

            override fun sendTilAttestering(
                saksbehandler: Saksbehandler
            ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
                if (erAttestantOgSakbehandlerSammePerson(saksbehandler)) {
                    return AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                }
                this@Behandling.saksbehandler = saksbehandler
                nyTilstand(TilAttestering().Avslag())
                return this@Behandling.right()
            }

            override fun opprettBeregning(
                saksbehandler: Saksbehandler,
                fraOgMed: LocalDate,
                tilOgMed: LocalDate,
                fradrag: List<Fradrag>
            ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
                // er samme logikk som brukes fra Opprettet::opprettBeregning
                if (this@Behandling.behandlingsinformasjon.erInnvilget()) {
                    return nyTilstand(Vilkårsvurdert().Innvilget()).opprettBeregning(
                        saksbehandler,
                        fraOgMed,
                        tilOgMed,
                        fradrag
                    )
                }
                throw TilstandException(status, this::sendTilAttestering.toString())
            }

            override fun leggTilSimulering(
                saksbehandler: Saksbehandler,
                simulering: () -> Simulering?
            ): Either<KunneIkkeLeggeTilSimulering, Behandling> {
                return nyTilstand(Beregnet().Avslag()).leggTilSimulering(saksbehandler, simulering)
            }

            override fun utledAvslagsgrunner(): List<Avslagsgrunn> =
                behandlingsinformasjon().utledAvslagsgrunner() + utledAvslagsgrunnForBeregning()
        }
    }

    private open inner class
    Iverksatt : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_INNVILGET

        override fun oppdaterIverksattJournalpostId(journalpostId: JournalpostId): Behandling {
            this@Behandling.iverksattJournalpostId = journalpostId
            return this@Behandling
        }

        override fun oppdaterIverksattBrevbestillingId(brevbestillingId: BrevbestillingId): Behandling {
            this@Behandling.iverksattBrevbestillingId = brevbestillingId
            return this@Behandling
        }

        inner class Innvilget : Iverksatt()
        inner class Avslag : Iverksatt() {
            override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_AVSLAG

            override fun utledAvslagsgrunner(): List<Avslagsgrunn> =
                behandlingsinformasjon().utledAvslagsgrunner() + utledAvslagsgrunnForBeregning()
        }
    }

    private fun utledAvslagsgrunnForBeregning(): List<Avslagsgrunn> =
        when (val vurdering = vurderAvslagGrunnetBeregning(beregning)) {
            is AvslagGrunnetBeregning.Ja -> listOf(vurdering.avslagsgrunn)
            AvslagGrunnetBeregning.Nei -> emptyList()
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

    class TilstandException(
        val state: BehandlingsStatus,
        val operation: String,
        val msg: String = "Illegal operation: $operation for state: $state"
    ) :
        RuntimeException(msg)

    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson
}
