package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.behandling.UnderkjentAttestering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.time.LocalDate
import java.util.UUID

data class Behandling(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    private var behandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon(
        uførhet = null,
        flyktning = null,
        lovligOpphold = null,
        fastOppholdINorge = null,
        oppholdIUtlandet = null,
        formue = null,
        personligOppmøte = null,
        bosituasjon = null
    ),
    val søknad: Søknad,
    private var beregning: Beregning? = null,
    var utbetaling: Utbetaling? = null,
    private var status: BehandlingsStatus = BehandlingsStatus.OPPRETTET,
    private var saksbehandler: Saksbehandler? = null,
    private var attestant: Attestant? = null,
    val sakId: UUID,
    val hendelseslogg: Hendelseslogg = Hendelseslogg(id.toString()), // TODO create when behandling created by service
) : PersistentDomainObject<BehandlingPersistenceObserver>() {

    private var tilstand: Tilstand = resolve(status)

    fun status() = tilstand.status

    fun saksbehandler() = saksbehandler

    fun attestant() = attestant

    fun beregning() = beregning

    fun behandlingsinformasjon() = behandlingsinformasjon

    /**
     * Henter fødselsnummer fra sak via persisteringslaget (lazy)
     */
    val fnr: Fnr by lazy { persistenceObserver.hentFnr(sakId) }

    fun utbetaling() = utbetaling

    fun hendelser() = hendelseslogg.hendelser()

    fun getUtledetSatsBeløp(): Int? {
        if (status == BehandlingsStatus.VILKÅRSVURDERT_INNVILGET ||
            status == BehandlingsStatus.BEREGNET ||
            status == BehandlingsStatus.SIMULERT
        ) {
            return behandlingsinformasjon().bosituasjon?.utledSats()?.fraDatoAsInt(LocalDate.now())
        }
        return null
    }

    private fun resolve(status: BehandlingsStatus): Tilstand = when (status) {
        BehandlingsStatus.OPPRETTET -> Opprettet()
        BehandlingsStatus.VILKÅRSVURDERT_INNVILGET -> Vilkårsvurdert().Innvilget()
        BehandlingsStatus.VILKÅRSVURDERT_AVSLAG -> Vilkårsvurdert().Avslag()
        BehandlingsStatus.BEREGNET -> Beregnet()
        BehandlingsStatus.SIMULERT -> Simulert()
        BehandlingsStatus.TIL_ATTESTERING_INNVILGET -> TilAttestering().Innvilget()
        BehandlingsStatus.TIL_ATTESTERING_AVSLAG -> TilAttestering().Avslag()
        BehandlingsStatus.IVERKSATT_INNVILGET -> Iverksatt().Innvilget()
        BehandlingsStatus.IVERKSATT_AVSLAG -> Iverksatt().Avslag()
    }

    fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon): Behandling {
        tilstand.oppdaterBehandlingsinformasjon(oppdatert)
        return this
    }

    fun opprettBeregning(
        fom: LocalDate,
        tom: LocalDate,
        fradrag: List<Fradrag> = emptyList()
    ): Behandling {
        tilstand.opprettBeregning(fom, tom, fradrag)
        return this
    }

    fun simuler(utbetaling: Utbetaling): Either<SimuleringFeilet, Behandling> {
        return tilstand.simuler(utbetaling)
    }

    fun sendTilAttestering(
        aktørId: AktørId,
        saksbehandler: Saksbehandler
    ): Behandling {
        return tilstand.sendTilAttestering(aktørId, saksbehandler)
    }

    fun iverksett(
        attestant: Attestant
    ): Either<IverksettFeil, Behandling> {
        return tilstand.iverksett(attestant)
    }

    fun underkjenn(begrunnelse: String, attestant: Attestant): Either<KunneIkkeUnderkjenne, Behandling> {
        return tilstand.underkjenn(begrunnelse, attestant)
    }

    override fun equals(other: Any?) = other is Behandling && id == other.id
    override fun hashCode() = id.hashCode()

    interface Tilstand {
        val status: BehandlingsStatus

        fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            throw TilstandException(status, this::oppdaterBehandlingsinformasjon.toString())
        }

        fun opprettBeregning(
            fom: LocalDate,
            tom: LocalDate,
            fradrag: List<Fradrag>
        ) {
            throw TilstandException(status, this::opprettBeregning.toString())
        }

        fun simuler(utbetaling: Utbetaling): Either<SimuleringFeilet, Behandling> {
            throw TilstandException(status, this::simuler.toString())
        }

        fun sendTilAttestering(
            aktørId: AktørId,
            saksbehandler: Saksbehandler
        ): Behandling {
            throw TilstandException(status, this::sendTilAttestering.toString())
        }

        fun iverksett(
            attestant: Attestant
        ): Either<IverksettFeil, Behandling> {
            throw TilstandException(status, this::iverksett.toString())
        }

        fun underkjenn(begrunnelse: String, attestant: Attestant): Either<KunneIkkeUnderkjenne, Behandling> {
            throw TilstandException(status, this::underkjenn.toString())
        }
    }

    private fun nyTilstand(target: Tilstand): Tilstand {
        status = persistenceObserver.oppdaterBehandlingStatus(id, target.status)
        tilstand = resolve(status)
        return tilstand
    }

    private inner class Opprettet : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.OPPRETTET

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            if (this@Behandling.beregning != null) {
                this@Behandling.beregning = null // TODO service will handle
            }

            behandlingsinformasjon = behandlingsinformasjon.patch(oppdatert) // TODO service will handle
            if (behandlingsinformasjon.isInnvilget()) {
                tilstand = resolve(Vilkårsvurdert().Innvilget().status)
                this@Behandling.status = tilstand.status
            } else if (behandlingsinformasjon.isAvslag()) {
                tilstand = resolve(Vilkårsvurdert().Avslag().status)
                this@Behandling.status = tilstand.status
            }
        }
    }

    private open inner class Vilkårsvurdert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_INNVILGET

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(oppdatert)
        }

        inner class Innvilget : Vilkårsvurdert() {
            override fun opprettBeregning(fom: LocalDate, tom: LocalDate, fradrag: List<Fradrag>) {
                val sats = this@Behandling.behandlingsinformasjon.bosituasjon?.utledSats()
                    ?: throw TilstandException(
                        status,
                        this::opprettBeregning.toString(),
                        "Kan ikke opprette beregning. Behandlingsinformasjon er ikke komplett."
                    )

                val forventetInntekt =
                    this@Behandling.behandlingsinformasjon.uførhet?.forventetInntekt ?: throw TilstandException(
                        status,
                        this::opprettBeregning.toString(),
                        "Kan ikke opprette beregning. Forventet inntekt finnes ikke."
                    )

                beregning = Beregning(
                    fom = fom,
                    tom = tom,
                    sats = sats,
                    fradrag = fradrag,
                    forventetInntekt = forventetInntekt
                )
                tilstand = Beregnet()
                this@Behandling.status = tilstand.status
            }
        }

        inner class Avslag : Vilkårsvurdert() {
            override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_AVSLAG

            override fun sendTilAttestering(
                aktørId: AktørId,
                saksbehandler: Saksbehandler,
            ): Behandling {
                this@Behandling.saksbehandler = saksbehandler
                tilstand = TilAttestering().Avslag()
                this@Behandling.status = tilstand.status
                return this@Behandling
            }
        }
    }

    private inner class Beregnet : Behandling.Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET

        override fun opprettBeregning(fom: LocalDate, tom: LocalDate, fradrag: List<Fradrag>) {
            nyTilstand(Vilkårsvurdert()).opprettBeregning(fom, tom, fradrag)
        }

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(oppdatert)
        }

        override fun simuler(utbetaling: Utbetaling): Either<SimuleringFeilet, Behandling> {
            // TODO just passing the utbetaling for now for backwards compatability
            this@Behandling.utbetaling = utbetaling
            tilstand = Simulert()
            this@Behandling.status = tilstand.status
            return this@Behandling.right()
        }
    }

    private inner class Simulert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.SIMULERT

        override fun sendTilAttestering(
            aktørId: AktørId,
            saksbehandler: Saksbehandler
        ): Behandling {
            this@Behandling.saksbehandler = saksbehandler
            tilstand = TilAttestering().Innvilget()
            this@Behandling.status = tilstand.status
            return this@Behandling
        }

        override fun opprettBeregning(fom: LocalDate, tom: LocalDate, fradrag: List<Fradrag>) {
            nyTilstand(Vilkårsvurdert().Innvilget()).opprettBeregning(fom, tom, fradrag)
        }

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(oppdatert)
        }

        override fun simuler(utbetaling: Utbetaling): Either<SimuleringFeilet, Behandling> {
            return nyTilstand(Beregnet()).simuler(utbetaling)
        }
    }

    private open inner class TilAttestering : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING_INNVILGET

        inner class Innvilget : TilAttestering() {
            override fun iverksett(
                attestant: Attestant
            ): Either<IverksettFeil, Behandling> {
                if (attestant.id == this@Behandling.saksbehandler?.id) {
                    return IverksettFeil.AttestantOgSaksbehandlerErLik().left()
                }
                this@Behandling.attestant = attestant
                tilstand = Iverksatt().Innvilget()
                this@Behandling.status = tilstand.status
                return this@Behandling.right()
            }
        }

        inner class Avslag : TilAttestering() {
            override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING_AVSLAG
            override fun iverksett(
                attestant: Attestant
            ): Either<IverksettFeil, Behandling> {
                if (attestant.id == this@Behandling.saksbehandler?.id) {
                    return IverksettFeil.AttestantOgSaksbehandlerErLik().left()
                }
                this@Behandling.attestant = attestant
                tilstand = Iverksatt().Avslag()
                this@Behandling.status = tilstand.status
                return this@Behandling.right()
            }
        }

        override fun underkjenn(begrunnelse: String, attestant: Attestant): Either<KunneIkkeUnderkjenne, Behandling> {
            if (attestant.id == this@Behandling.saksbehandler?.id) {
                return KunneIkkeUnderkjenne().left()
            }

            hendelseslogg.hendelse(UnderkjentAttestering(attestant.id, begrunnelse))
            tilstand = Simulert()
            this@Behandling.status = tilstand.status
            return this@Behandling.right()
        }
    }

    private open inner class Iverksatt : Behandling.Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_INNVILGET

        inner class Innvilget : Iverksatt()
        inner class Avslag : Iverksatt() {
            override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_AVSLAG
        }
    }

    enum class BehandlingsStatus {
        OPPRETTET,
        VILKÅRSVURDERT_INNVILGET,
        VILKÅRSVURDERT_AVSLAG,
        BEREGNET,
        SIMULERT,
        TIL_ATTESTERING_INNVILGET,
        TIL_ATTESTERING_AVSLAG,
        IVERKSATT_INNVILGET,
        IVERKSATT_AVSLAG
    }

    class TilstandException(
        val state: BehandlingsStatus,
        val operation: String,
        val msg: String = "Illegal operation: $operation for state: $state"
    ) :
        RuntimeException(msg)

    sealed class IverksettFeil {
        class AttestantOgSaksbehandlerErLik(val msg: String = "Attestant og saksbehandler kan ikke vare samme person!") :
            IverksettFeil()

        class Utbetaling(val msg: String) : IverksettFeil()
    }

    data class KunneIkkeUnderkjenne(val msg: String = "Attestant og saksbehandler kan ikke vare samme person!")
}

interface BehandlingPersistenceObserver : PersistenceObserver {
    fun oppdaterBehandlingStatus(
        behandlingId: UUID,
        status: Behandling.BehandlingsStatus
    ): Behandling.BehandlingsStatus

    fun hentOppdrag(sakId: UUID): Oppdrag
    fun hentFnr(sakId: UUID): Fnr
}
