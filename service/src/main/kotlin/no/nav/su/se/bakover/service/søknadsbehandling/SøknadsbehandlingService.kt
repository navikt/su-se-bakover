package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingRequest
import java.util.UUID
import kotlin.reflect.KClass

interface SøknadsbehandlingService {
    fun opprett(request: OpprettRequest): Either<KunneIkkeOpprette, Søknadsbehandling.Vilkårsvurdert.Uavklart>
    fun vilkårsvurder(request: VilkårsvurderRequest): Either<KunneIkkeVilkårsvurdere, Søknadsbehandling.Vilkårsvurdert>
    fun beregn(request: BeregnRequest): Either<KunneIkkeBeregne, Søknadsbehandling.Beregnet>
    fun simuler(request: SimulerRequest): Either<KunneIkkeSimulereBehandling, Søknadsbehandling.Simulert>
    fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeTilAttestering, Søknadsbehandling.TilAttestering>
    fun underkjenn(request: UnderkjennRequest): Either<KunneIkkeUnderkjenne, Søknadsbehandling.Underkjent>
    fun iverksett(request: IverksettRequest): Either<KunneIkkeIverksette, Søknadsbehandling.Iverksatt>
    fun brev(request: BrevRequest): Either<KunneIkkeLageBrev, ByteArray>
    fun hent(request: HentRequest): Either<FantIkkeBehandling, Søknadsbehandling>
    fun oppdaterStønadsperiode(request: OppdaterStønadsperiodeRequest): Either<KunneIkkeOppdatereStønadsperiode, Søknadsbehandling>
    fun leggTilUføregrunnlag(request: LeggTilUførevurderingRequest): Either<KunneIkkeLeggeTilGrunnlag, Søknadsbehandling>

    data class OpprettRequest(
        val søknadId: UUID,
    )

    sealed class KunneIkkeOpprette {
        object FantIkkeSøknad : KunneIkkeOpprette()
        object SøknadManglerOppgave : KunneIkkeOpprette()
        object SøknadErLukket : KunneIkkeOpprette()
        object SøknadHarAlleredeBehandling : KunneIkkeOpprette()
    }

    data class VilkårsvurderRequest(
        val behandlingId: UUID,
        val behandlingsinformasjon: Behandlingsinformasjon,
    )

    sealed class KunneIkkeVilkårsvurdere {
        object FantIkkeBehandling : KunneIkkeVilkårsvurdere()
    }

    data class BeregnRequest(
        val behandlingId: UUID,
        val fradrag: List<FradragRequest>,
        val begrunnelse: String?,
    ) {
        data class FradragRequest(
            val periode: Periode?,
            val type: Fradragstype,
            val månedsbeløp: Double,
            val utenlandskInntekt: UtenlandskInntekt?,
            val tilhører: FradragTilhører,
        )

        object IkkeLovMedFradragUtenforPerioden

        fun toFradrag(
            stønadsperiode: Stønadsperiode,
            opprettet: Tidspunkt,
        ): Either<IkkeLovMedFradragUtenforPerioden, List<Fradrag>> {
            return fradrag.map {
                if (it.periode != null && !(stønadsperiode.periode inneholder it.periode)) {
                    return IkkeLovMedFradragUtenforPerioden.left()
                }
                FradragFactory.ny(
                    opprettet = opprettet,
                    type = it.type,
                    månedsbeløp = it.månedsbeløp,
                    periode = it.periode ?: stønadsperiode.periode,
                    utenlandskInntekt = it.utenlandskInntekt,
                    tilhører = it.tilhører,
                )
            }.right()
        }
    }

    sealed class KunneIkkeBeregne {
        object FantIkkeBehandling : KunneIkkeBeregne()
        object IkkeLovMedFradragUtenforPerioden : KunneIkkeBeregne()
    }

    data class SimulerRequest(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    )

    sealed class KunneIkkeSimulereBehandling {
        object KunneIkkeSimulere : KunneIkkeSimulereBehandling()
        object FantIkkeBehandling : KunneIkkeSimulereBehandling()
    }

    data class SendTilAttesteringRequest(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val fritekstTilBrev: String,
    )

    sealed class KunneIkkeSendeTilAttestering {
        object FantIkkeBehandling : KunneIkkeSendeTilAttestering()
        object KunneIkkeFinneAktørId : KunneIkkeSendeTilAttestering()
        object KunneIkkeOppretteOppgave : KunneIkkeSendeTilAttestering()
    }

    data class UnderkjennRequest(
        val behandlingId: UUID,
        val attestering: Attestering.Underkjent,
    )

    sealed class KunneIkkeUnderkjenne {
        object FantIkkeBehandling : KunneIkkeUnderkjenne()
        object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeUnderkjenne()
        object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenne()
        object FantIkkeAktørId : KunneIkkeUnderkjenne()
    }

    data class IverksettRequest(
        val behandlingId: UUID,
        val attestering: Attestering,
    )

    sealed class KunneIkkeIverksette {
        object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksette()
        object KunneIkkeUtbetale : KunneIkkeIverksette()
        object KunneIkkeKontrollsimulere : KunneIkkeIverksette()
        object SimuleringHarBlittEndretSidenSaksbehandlerSimulerte : KunneIkkeIverksette()
        object KunneIkkeJournalføreBrev : KunneIkkeIverksette()
        object FantIkkeBehandling : KunneIkkeIverksette()
        object FantIkkePerson : KunneIkkeIverksette()
        object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeIverksette()
    }

    sealed class BrevRequest {
        abstract val behandling: Søknadsbehandling

        data class MedFritekst(
            override val behandling: Søknadsbehandling,
            val fritekst: String,
        ) : BrevRequest()

        data class UtenFritekst(
            override val behandling: Søknadsbehandling,
        ) : BrevRequest()
    }

    sealed class KunneIkkeLageBrev {
        data class KanIkkeLageBrevutkastForStatus(val status: BehandlingsStatus) : KunneIkkeLageBrev()
        object KunneIkkeLagePDF : KunneIkkeLageBrev()
        object FantIkkePerson : KunneIkkeLageBrev()
        object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeLageBrev()
    }

    data class HentRequest(
        val behandlingId: UUID,
    )

    object FantIkkeBehandling
    object KunneIkkeHenteAktiveBehandlinger

    sealed class KunneIkkeOppdatereStønadsperiode {
        object FantIkkeBehandling : SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode()
        object FraOgMedDatoKanIkkeVæreFør2021 : SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode()
        object PeriodeKanIkkeVæreLengreEnn12Måneder : SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode()
    }

    data class OppdaterStønadsperiodeRequest(
        val behandlingId: UUID,
        val stønadsperiode: Stønadsperiode,
    )

    sealed class KunneIkkeLeggeTilGrunnlag {
        object FantIkkeBehandling : KunneIkkeLeggeTilGrunnlag()
        data class UgyldigTilstand(val fra: KClass<out Søknadsbehandling>, val til: KClass<out Søknadsbehandling>) : KunneIkkeLeggeTilGrunnlag()
        object UføregradOgForventetInntektMangler : KunneIkkeLeggeTilGrunnlag()
        object PeriodeForGrunnlagOgVurderingErForskjellig : KunneIkkeLeggeTilGrunnlag()
        object OverlappendeVurderingsperioder : KunneIkkeLeggeTilGrunnlag()
        object VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden : KunneIkkeLeggeTilGrunnlag()
    }
}
