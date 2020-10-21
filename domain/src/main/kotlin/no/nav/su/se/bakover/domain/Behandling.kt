package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradragWithForventetInntekt
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.behandling.UnderkjentAttestering
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.time.LocalDate
import java.util.UUID

data class Behandling(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    private var behandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
    val søknad: Søknad,
    private var beregning: Beregning? = null,
    internal var simulering: Simulering? = null,
    private var status: BehandlingsStatus = BehandlingsStatus.OPPRETTET,
    private var saksbehandler: NavIdentBruker.Saksbehandler? = null,
    private var attestant: NavIdentBruker.Attestant? = null,
    val sakId: UUID,
    val hendelseslogg: Hendelseslogg = Hendelseslogg(id.toString()), // TODO create when behandling created by service probably also move out from behandling alltogether.
    val fnr: Fnr
) {

    private var tilstand: Tilstand = resolve(status)

    fun status() = tilstand.status

    fun saksbehandler() = saksbehandler

    fun attestant() = attestant

    fun beregning() = beregning

    fun behandlingsinformasjon() = behandlingsinformasjon

    fun simulering() = simulering

    fun hendelser() = hendelseslogg.hendelser()

    fun getUtledetSatsBeløp(): Int? {
        if (status == BehandlingsStatus.VILKÅRSVURDERT_INNVILGET ||
            status == BehandlingsStatus.BEREGNET_INNVILGET ||
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
        BehandlingsStatus.BEREGNET_INNVILGET -> Beregnet()
        BehandlingsStatus.BEREGNET_AVSLAG -> Beregnet().Avslag()
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
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag> = emptyList()
    ): Behandling {
        tilstand.opprettBeregning(fraOgMed, tilOgMed, fradrag)
        return this
    }

    fun leggTilSimulering(simulering: Simulering): Behandling {
        return tilstand.leggTilSimulering(simulering)
    }

    fun sendTilAttestering(
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Behandling {
        return tilstand.sendTilAttestering(saksbehandler)
    }

    fun iverksett(
        attestant: NavIdentBruker.Attestant
    ): Either<IverksettFeil, Behandling> {
        return tilstand.iverksett(attestant)
    }

    fun underkjenn(begrunnelse: String, attestant: NavIdentBruker.Attestant): Either<KunneIkkeUnderkjenne, Behandling> {
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
            fraOgMed: LocalDate,
            tilOgMed: LocalDate,
            fradrag: List<Fradrag>
        ) {
            throw TilstandException(status, this::opprettBeregning.toString())
        }

        fun leggTilSimulering(simulering: Simulering): Behandling {
            throw TilstandException(status, this::leggTilSimulering.toString())
        }

        fun sendTilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler
        ): Behandling {
            throw TilstandException(status, this::sendTilAttestering.toString())
        }

        fun iverksett(
            attestant: NavIdentBruker.Attestant
        ): Either<IverksettFeil, Behandling> {
            throw TilstandException(status, this::iverksett.toString())
        }

        fun underkjenn(begrunnelse: String, attestant: NavIdentBruker.Attestant): Either<KunneIkkeUnderkjenne, Behandling> {
            throw TilstandException(status, this::underkjenn.toString())
        }
    }

    private fun nyTilstand(target: Tilstand): Tilstand {
        status = target.status
        tilstand = resolve(status)
        return tilstand
    }

    private inner class Opprettet : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.OPPRETTET

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            if (this@Behandling.beregning != null) {
                this@Behandling.beregning =
                    null // TODO we need to discuss how to divide responsibility between service and domain.
            }

            behandlingsinformasjon =
                behandlingsinformasjon.patch(oppdatert) // TODO we need to discuss how to divide responsibility between service and domain.
            if (behandlingsinformasjon.isInnvilget()) {
                nyTilstand(Vilkårsvurdert().Innvilget())
            } else if (behandlingsinformasjon.isAvslag()) {
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
            override fun opprettBeregning(fraOgMed: LocalDate, tilOgMed: LocalDate, fradrag: List<Fradrag>) {
                val sats = this@Behandling.behandlingsinformasjon.bosituasjon?.utledSats()
                    ?: throw TilstandException(
                        status,
                        this::opprettBeregning.toString(),
                        "Kan ikke opprette beregning. Behandlingsinformasjon er ikke komplett."
                    )
                val oppdatertFradrag = fradragWithForventetInntekt(
                    fradrag = fradrag,
                    forventetInntekt = this@Behandling.behandlingsinformasjon.uførhet!!.forventetInntekt ?: 0
                )

                beregning = Beregning(
                    fraOgMed = fraOgMed,
                    tilOgMed = tilOgMed,
                    sats = sats,
                    fradrag = oppdatertFradrag
                )

                if (beregning!!.beløpErNull() || beregning!!.beløpErOverNullMenUnderMinstebeløp()) {
                    nyTilstand(Beregnet().Avslag())
                    return
                }

                nyTilstand(Beregnet())
            }
        }

        inner class Avslag : Vilkårsvurdert() {
            override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_AVSLAG

            override fun sendTilAttestering(
                saksbehandler: NavIdentBruker.Saksbehandler,
            ): Behandling {
                this@Behandling.saksbehandler = saksbehandler
                nyTilstand(TilAttestering().Avslag())
                return this@Behandling
            }
        }
    }

    private open inner class Beregnet : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET_INNVILGET

        override fun opprettBeregning(fraOgMed: LocalDate, tilOgMed: LocalDate, fradrag: List<Fradrag>) {
            nyTilstand(Vilkårsvurdert()).opprettBeregning(fraOgMed, tilOgMed, fradrag)
        }

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(oppdatert)
        }

        override fun leggTilSimulering(simulering: Simulering): Behandling {
            this@Behandling.simulering = simulering
            nyTilstand(Simulert())
            return this@Behandling
        }

        inner class Avslag : Beregnet() {
            override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET_AVSLAG
            override fun leggTilSimulering(simulering: Simulering): Behandling {
                throw TilstandException(status, this::leggTilSimulering.toString())
            }

            override fun sendTilAttestering(
                saksbehandler: NavIdentBruker.Saksbehandler,
            ): Behandling {
                this@Behandling.saksbehandler = saksbehandler
                nyTilstand(TilAttestering().Avslag())
                return this@Behandling
            }
        }
    }

    private inner class Simulert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.SIMULERT

        override fun sendTilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler
        ): Behandling {
            this@Behandling.saksbehandler = saksbehandler
            nyTilstand(TilAttestering().Innvilget())
            return this@Behandling
        }

        override fun opprettBeregning(fraOgMed: LocalDate, tilOgMed: LocalDate, fradrag: List<Fradrag>) {
            nyTilstand(Vilkårsvurdert().Innvilget()).opprettBeregning(fraOgMed, tilOgMed, fradrag)
        }

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(oppdatert)
        }

        override fun leggTilSimulering(simulering: Simulering): Behandling {
            return nyTilstand(Beregnet()).leggTilSimulering(simulering)
        }
    }

    private open inner class TilAttestering : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING_INNVILGET

        inner class Innvilget : TilAttestering() {
            override fun iverksett(
                attestant: NavIdentBruker.Attestant
            ): Either<IverksettFeil, Behandling> {
                if (attestant.navIdent == this@Behandling.saksbehandler?.navIdent) {
                    return IverksettFeil.AttestantOgSaksbehandlerErLik.left()
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
            ): Either<IverksettFeil, Behandling> {
                if (attestant.navIdent == this@Behandling.saksbehandler?.navIdent) {
                    return IverksettFeil.AttestantOgSaksbehandlerErLik.left()
                }
                this@Behandling.attestant = attestant
                nyTilstand(Iverksatt().Avslag())
                return this@Behandling.right()
            }
        }

        override fun underkjenn(begrunnelse: String, attestant: NavIdentBruker.Attestant): Either<KunneIkkeUnderkjenne, Behandling> {
            if (attestant.navIdent == this@Behandling.saksbehandler?.navIdent) {
                return KunneIkkeUnderkjenne().left()
            }

            hendelseslogg.hendelse(UnderkjentAttestering(attestant.navIdent, begrunnelse))
            nyTilstand(Simulert())
            return this@Behandling.right()
        }
    }

    private open inner class Iverksatt : Tilstand {
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

    sealed class IverksettFeil {
        object AttestantOgSaksbehandlerErLik : IverksettFeil()
        object KunneIkkeUtbetale : IverksettFeil()
        object KunneIkkeKontrollSimulere : IverksettFeil()
        object SimuleringHarBlittEndretSidenSaksbehandlerSimulerte : IverksettFeil()
    }

    data class KunneIkkeUnderkjenne(val msg: String = "Attestant og saksbehandler kan ikke vare samme person!")
}
