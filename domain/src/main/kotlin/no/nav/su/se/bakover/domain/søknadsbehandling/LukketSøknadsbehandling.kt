package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling

data class LukketSøknadsbehandling private constructor(
    val lukketSøknadsbehandling: Søknadsbehandling,
) : Søknadsbehandling() {
    override val søknad = lukketSøknadsbehandling.søknad
    override val behandlingsinformasjon = lukketSøknadsbehandling.behandlingsinformasjon
    override val status = lukketSøknadsbehandling.status
    override val stønadsperiode = lukketSøknadsbehandling.stønadsperiode
    override val grunnlagsdata = lukketSøknadsbehandling.grunnlagsdata
    override val vilkårsvurderinger = lukketSøknadsbehandling.vilkårsvurderinger
    override val attesteringer = lukketSøknadsbehandling.attesteringer
    override val fritekstTilBrev = lukketSøknadsbehandling.fritekstTilBrev
    override val oppgaveId = lukketSøknadsbehandling.oppgaveId
    override val id = lukketSøknadsbehandling.id
    override val opprettet = lukketSøknadsbehandling.opprettet
    override val sakId = lukketSøknadsbehandling.sakId
    override val saksnummer = lukketSøknadsbehandling.saksnummer
    override val fnr = lukketSøknadsbehandling.fnr
    // Så vi kan initialiseres uten at periode er satt (typisk ved ny søknadsbehandling)
    override val periode by lazy { lukketSøknadsbehandling.periode }
    override val avkorting: AvkortingVedSøknadsbehandling = when (val avkorting = lukketSøknadsbehandling.avkorting) {
        is AvkortingVedSøknadsbehandling.Håndtert -> {
            avkorting.kanIkke()
        }
        is AvkortingVedSøknadsbehandling.Iverksatt -> {
            throw IllegalStateException("Kan ikke lukke iverksatt")
        }
        is AvkortingVedSøknadsbehandling.Uhåndtert -> {
            avkorting.kanIkke()
        }
    }
    override val sakstype: Sakstype = lukketSøknadsbehandling.sakstype

    init {
        kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
    }
    companion object {

        fun tryCreate(
            lukketSøknadsbehandling: Søknadsbehandling,
        ): Either<KunneIkkeLukkeSøknadsbehandling, LukketSøknadsbehandling> {
            if (lukketSøknadsbehandling is LukketSøknadsbehandling) {
                return KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnAlleredeLukketSøknadsbehandling.left()
            }
            if (lukketSøknadsbehandling is Iverksatt) {
                return KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnIverksattSøknadsbehandling.left()
            }
            if (lukketSøknadsbehandling is TilAttestering) {
                return KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnSøknadsbehandlingTilAttestering.left()
            }
            return LukketSøknadsbehandling(lukketSøknadsbehandling).right()
        }

        fun create(lukketSøknadsbehandling: Søknadsbehandling): LukketSøknadsbehandling {
            return tryCreate(lukketSøknadsbehandling).getOrHandle {
                throw IllegalArgumentException("Kunne ikke opprette LukketSøknadsbehandling: $it")
            }
        }
    }

    override fun accept(visitor: SøknadsbehandlingVisitor) = visitor.visit(this)
}
