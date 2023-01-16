package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode

data class LukketSøknadsbehandling private constructor(
    val underliggendeSøknadsbehandling: Søknadsbehandling,
    override val søknad: Søknad.Journalført.MedOppgave.Lukket,
) : Søknadsbehandling() {
    override val stønadsperiode = underliggendeSøknadsbehandling.stønadsperiode
    override val grunnlagsdata = underliggendeSøknadsbehandling.grunnlagsdata
    override val vilkårsvurderinger = underliggendeSøknadsbehandling.vilkårsvurderinger
    override val attesteringer = underliggendeSøknadsbehandling.attesteringer
    override val fritekstTilBrev = underliggendeSøknadsbehandling.fritekstTilBrev
    override val oppgaveId = underliggendeSøknadsbehandling.oppgaveId
    override val id = underliggendeSøknadsbehandling.id
    override val opprettet = underliggendeSøknadsbehandling.opprettet
    override val sakId = underliggendeSøknadsbehandling.sakId
    override val saksnummer = underliggendeSøknadsbehandling.saksnummer
    override val fnr = underliggendeSøknadsbehandling.fnr

    // Så vi kan initialiseres uten at periode er satt (typisk ved ny søknadsbehandling)
    override val periode by lazy { underliggendeSøknadsbehandling.periode }
    override val avkorting: AvkortingVedSøknadsbehandling = when (val avkorting = underliggendeSøknadsbehandling.avkorting) {
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
    override val sakstype: Sakstype = underliggendeSøknadsbehandling.sakstype

    // TODO jah: Denne bør overstyres av saksbehandler som avsluttet revurderingen.
    override val saksbehandler: NavIdentBruker.Saksbehandler? = underliggendeSøknadsbehandling.saksbehandler

    val lukketTidspunkt = søknad.lukketTidspunkt
    val lukketAv = søknad.lukketAv
    val lukketBrevvalg = søknad.brevvalg

    override val beregning = when (underliggendeSøknadsbehandling) {
        is Beregnet.Avslag -> underliggendeSøknadsbehandling.beregning
        is Beregnet.Innvilget -> underliggendeSøknadsbehandling.beregning
        is Iverksatt -> throw IllegalArgumentException("Ugyldig tilstand")
        is LukketSøknadsbehandling -> throw IllegalArgumentException("Ugyldig tilstand")
        is Simulert -> underliggendeSøknadsbehandling.beregning
        is TilAttestering -> throw IllegalArgumentException("Ugyldig tilstand")
        is Underkjent.Avslag.MedBeregning -> underliggendeSøknadsbehandling.beregning
        is Underkjent.Avslag.UtenBeregning -> null
        is Underkjent.Innvilget -> underliggendeSøknadsbehandling.beregning
        is Vilkårsvurdert.Avslag -> null
        is Vilkårsvurdert.Innvilget -> null
        is Vilkårsvurdert.Uavklart -> null
    }

    override val simulering = when (underliggendeSøknadsbehandling) {
        is Beregnet.Avslag -> null
        is Beregnet.Innvilget -> null
        is Iverksatt -> throw IllegalArgumentException("Ugyldig tilstand")
        is LukketSøknadsbehandling -> throw IllegalArgumentException("Ugyldig tilstand")
        is Simulert -> underliggendeSøknadsbehandling.simulering
        is TilAttestering -> throw IllegalArgumentException("Ugyldig tilstand")
        is Underkjent.Avslag.MedBeregning -> null
        is Underkjent.Avslag.UtenBeregning -> null
        is Underkjent.Innvilget -> underliggendeSøknadsbehandling.simulering
        is Vilkårsvurdert.Avslag -> null
        is Vilkårsvurdert.Innvilget -> null
        is Vilkårsvurdert.Uavklart -> null
    }

    override fun copyInternal(
        stønadsperiode: Stønadsperiode,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        avkorting: AvkortingVedSøknadsbehandling,
    ) = throw UnsupportedOperationException("Kan ikke kalle copyInternal på en lukket søknadsbehandling.")

    init {
        kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
        validateState(this.underliggendeSøknadsbehandling).tapLeft {
            throw IllegalArgumentException("Ugyldig tilstand. Underliggende feil: $it")
        }
    }

    companion object {

        /**
         * Prøver lukke søknadsbehandlingen og tilhørende søknad.
         * Den underliggende søknadsbehandlingen kan ikke være av typen [LukketSøknadsbehandling], [Søknadsbehandling.Iverksatt] eller [Søknadsbehandling.TilAttestering]
         * @throws IllegalStateException Dersom den underliggende søknaden ikke er av typen [Søknad.Journalført.MedOppgave.IkkeLukket]
         */
        fun tryCreate(
            søknadsbehandlingSomSkalLukkes: Søknadsbehandling,
            lukkSøknadCommand: LukkSøknadCommand,
        ): Either<KunneIkkeLukkeSøknadsbehandling, LukketSøknadsbehandling> {
            validateState(søknadsbehandlingSomSkalLukkes).tapLeft {
                return it.left()
            }
            return when (val søknad = søknadsbehandlingSomSkalLukkes.søknad) {
                is Søknad.Journalført.MedOppgave.IkkeLukket -> LukketSøknadsbehandling(
                    underliggendeSøknadsbehandling = søknadsbehandlingSomSkalLukkes,
                    søknad = søknad.lukk(
                        lukkSøknadCommand = lukkSøknadCommand,
                    ),
                ).right()

                is Søknad.Journalført.MedOppgave.Lukket -> throw IllegalStateException("Kan ikke opprette en LukketSøknadsbehandling dersom søknaden allerede er lukket.")
            }
        }

        /**
         * Denne funksjonen er reservert for persisteringslaget og asserts i tester og skal ikke brukes i domenesammenheng.
         * Krever at søknadsbehandlingen allerede inneholder en lukket søknad.
         */
        fun createFromPersistedState(
            søknadsbehandling: Søknadsbehandling,
            søknad: Søknad.Journalført.MedOppgave.Lukket = søknadsbehandling.søknad as Søknad.Journalført.MedOppgave.Lukket,
        ): LukketSøknadsbehandling {
            validateState(søknadsbehandling).tapLeft {
                throw IllegalArgumentException("Ugyldig tilstand. Underliggende feil: $it")
            }
            return LukketSøknadsbehandling(
                underliggendeSøknadsbehandling = søknadsbehandling,
                søknad = søknad,
            )
        }

        private fun validateState(søknadsbehandlingSomSkalLukkes: Søknadsbehandling): Either<KunneIkkeLukkeSøknadsbehandling, Unit> {
            if (søknadsbehandlingSomSkalLukkes is LukketSøknadsbehandling) {
                return KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnAlleredeLukketSøknadsbehandling.left()
            }
            if (søknadsbehandlingSomSkalLukkes is Iverksatt) {
                return KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnIverksattSøknadsbehandling.left()
            }
            if (søknadsbehandlingSomSkalLukkes is TilAttestering) {
                return KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnSøknadsbehandlingTilAttestering.left()
            }
            return Unit.right()
        }
    }

    override fun accept(visitor: SøknadsbehandlingVisitor) = visitor.visit(this)
}
