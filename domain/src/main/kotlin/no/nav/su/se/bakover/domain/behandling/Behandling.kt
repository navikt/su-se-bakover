package no.nav.su.se.bakover.domain.behandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.behandling.UnderkjentAttestering
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
    private var attestant: NavIdentBruker.Attestant?,
    val sakId: UUID,
    val hendelseslogg: Hendelseslogg,
    val fnr: Fnr,
    private var oppgaveId: OppgaveId,
    private var iverksattJournalpostId: JournalpostId?,
    private var iverksattBrevbestillingId: BrevbestillingId?,
) {

    private var tilstand: Tilstand = resolve(status)

    fun status() = tilstand.status

    fun saksbehandler() = saksbehandler

    fun attestant() = attestant

    fun beregning() = beregning

    fun behandlingsinformasjon() = behandlingsinformasjon

    fun simulering() = simulering

    fun hendelser() = hendelseslogg.hendelser()

    fun oppgaveId() = oppgaveId

    fun iverksattJournalpostId() = iverksattJournalpostId

    fun iverksattBrevbestillingId() = iverksattBrevbestillingId

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
        BehandlingsStatus.IVERKSATT_INNVILGET -> Iverksatt().Innvilget()
        BehandlingsStatus.IVERKSATT_AVSLAG -> Iverksatt().Avslag()
    }

    fun erInnvilget() = listOf(
        BehandlingsStatus.SIMULERT,
        BehandlingsStatus.BEREGNET_INNVILGET,
        BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
        BehandlingsStatus.IVERKSATT_INNVILGET
    ).contains(status)

    fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon): Behandling {
        tilstand.oppdaterBehandlingsinformasjon(oppdatert)
        return this
    }

    fun opprettBeregning(
        saksbehandler: Saksbehandler,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag> = emptyList()
    ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
        return tilstand.opprettBeregning(saksbehandler, fraOgMed, tilOgMed, fradrag)
    }

    fun leggTilSimulering(saksbehandler: Saksbehandler, simulering: () -> Simulering?): Either<KunneIkkeLeggeTilSimulering, Behandling> {
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
        begrunnelse: String,
        attestant: NavIdentBruker.Attestant
    ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
        return tilstand.underkjenn(begrunnelse, attestant)
    }

    fun utledAvslagsgrunner(): List<Avslagsgrunn> {
        return tilstand.utledAvslagsgrunner()
    }

    interface Tilstand {

        val status: BehandlingsStatus

        fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
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

        fun leggTilSimulering(saksbehandler: Saksbehandler, simulering: () -> Simulering?): Either<KunneIkkeLeggeTilSimulering, Behandling> {
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
            begrunnelse: String,
            attestant: NavIdentBruker.Attestant
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

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            if (this@Behandling.beregning != null) {
                this@Behandling.beregning = null
            }

            behandlingsinformasjon =
                behandlingsinformasjon.patch(oppdatert) // TODO we need to discuss how to divide responsibility between service and domain.
            if (behandlingsinformasjon.erInnvilget()) {
                nyTilstand(Vilkårsvurdert().Innvilget())
            } else if (behandlingsinformasjon.erAvslag()) {
                nyTilstand(Vilkårsvurdert().Avslag())
            }
        }
    }

    private open inner class Vilkårsvurdert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_INNVILGET

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(oppdatert)
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
                val beregningsgrunnlag = Beregningsgrunnlag(
                    periode = Periode(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                    fradrag = fradrag.plus(
                        FradragFactory.ny(
                            type = Fradragstype.ForventetInntekt,
                            beløp = behandlingsinformasjon.uførhet!!.forventetInntekt?.toDouble() ?: 0.0,
                            periode = Periode(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER
                        )
                    )
                )

                val strategy = this@Behandling.behandlingsinformasjon.bosituasjon!!.getBeregningStrategy()
                beregning = strategy.beregn(beregningsgrunnlag)

                if (beregning!!.getSumYtelse() <= 0 || beregning!!.getSumYtelseErUnderMinstebeløp()) {
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

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(oppdatert)
        }

        override fun leggTilSimulering(saksbehandler: Saksbehandler, simulering: () -> Simulering?): Either<KunneIkkeLeggeTilSimulering, Behandling> {
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
            override fun leggTilSimulering(saksbehandler: Saksbehandler, simulering: () -> Simulering?): Either<KunneIkkeLeggeTilSimulering, Behandling> {
                throw TilstandException(status, this::leggTilSimulering.toString())
            }

            override fun sendTilAttestering(
                saksbehandler: Saksbehandler,
            ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
                this@Behandling.saksbehandler = saksbehandler
                nyTilstand(TilAttestering().Avslag())
                return this@Behandling.right()
            }

            override fun utledAvslagsgrunner(): List<Avslagsgrunn> {
                return behandlingsinformasjon().utledAvslagsgrunner() + (
                    beregning?.utledAvslagsgrunner()
                        ?: emptyList()
                    )
            }
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

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(oppdatert)
        }

        override fun leggTilSimulering(saksbehandler: Saksbehandler, simulering: () -> Simulering?): Either<KunneIkkeLeggeTilSimulering, Behandling> {
            return nyTilstand(Beregnet()).leggTilSimulering(saksbehandler, simulering)
        }
    }

    private fun erAttestantOgSakbehandlerSammePerson(saksbehandler: Saksbehandler): Boolean {
        return this@Behandling.attestant?.let {
            it.navIdent == saksbehandler.navIdent
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
                this@Behandling.attestant = attestant
                nyTilstand(Iverksatt().Innvilget())
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
                this@Behandling.attestant = attestant
                nyTilstand(Iverksatt().Avslag())
                return this@Behandling.right()
            }

            override fun utledAvslagsgrunner(): List<Avslagsgrunn> {
                return behandlingsinformasjon().utledAvslagsgrunner() + (
                    beregning?.utledAvslagsgrunner()
                        ?: emptyList()
                    )
            }
        }

        override fun underkjenn(
            begrunnelse: String,
            attestant: NavIdentBruker.Attestant
        ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Behandling> {
            if (attestant.navIdent == this@Behandling.saksbehandler?.navIdent) {
                return AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            hendelseslogg.hendelse(UnderkjentAttestering(attestant.navIdent, begrunnelse))
            this@Behandling.attestant = attestant
            nyTilstand(Simulert())
            return this@Behandling.right()
        }

        override fun oppdaterOppgaveId(
            oppgaveId: OppgaveId
        ): Behandling {
            this@Behandling.oppgaveId = oppgaveId
            return this@Behandling
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

            override fun utledAvslagsgrunner(): List<Avslagsgrunn> {
                return behandlingsinformasjon().utledAvslagsgrunner() + (
                    beregning?.utledAvslagsgrunner()
                        ?: emptyList()
                    )
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

    companion object {
        fun Beregning.utledAvslagsgrunner(): List<Avslagsgrunn> {
            return listOfNotNull(
                if (getSumYtelse() <= 0) Avslagsgrunn.FOR_HØY_INNTEKT else null,
                if (getSumYtelseErUnderMinstebeløp()) Avslagsgrunn.SU_UNDER_MINSTEGRENSE else null,
            )
        }
    }
}
