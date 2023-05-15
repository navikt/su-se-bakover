package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.skatt.EksternGrunnlagSkattRequest
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode

data class LukketSøknadsbehandling private constructor(
    val underliggendeSøknadsbehandling: Søknadsbehandling,
    override val søknad: Søknad.Journalført.MedOppgave.Lukket,
    override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
) : Søknadsbehandling() {
    override val aldersvurdering: Aldersvurdering? = underliggendeSøknadsbehandling.aldersvurdering
    override val stønadsperiode: Stønadsperiode? get() = aldersvurdering?.stønadsperiode
    override val grunnlagsdata = underliggendeSøknadsbehandling.grunnlagsdata
    override val vilkårsvurderinger = underliggendeSøknadsbehandling.vilkårsvurderinger
    override val eksterneGrunnlag = underliggendeSøknadsbehandling.eksterneGrunnlag
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
    override val avkorting: AvkortingVedSøknadsbehandling =
        when (val avkorting = underliggendeSøknadsbehandling.avkorting) {
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

    /**
     * Saksbehandler som lukket søknadsbehandling og søknad.
     * For siste handling før dette; se saksbehandler på [underliggendeSøknadsbehandling].
     */
    override val saksbehandler: NavIdentBruker.Saksbehandler = søknad.lukketAv

    val lukketTidspunkt = søknad.lukketTidspunkt
    val lukketAv = søknad.lukketAv

    override fun skalSendeVedtaksbrev(): Boolean {
        return søknad.brevvalg.skalSendeBrev()
    }

    override val beregning = when (underliggendeSøknadsbehandling) {
        is BeregnetSøknadsbehandling.Avslag -> underliggendeSøknadsbehandling.beregning
        is BeregnetSøknadsbehandling.Innvilget -> underliggendeSøknadsbehandling.beregning
        is IverksattSøknadsbehandling -> throw IllegalArgumentException("Ugyldig tilstand")
        is LukketSøknadsbehandling -> throw IllegalArgumentException("Ugyldig tilstand")
        is SimulertSøknadsbehandling -> underliggendeSøknadsbehandling.beregning
        is SøknadsbehandlingTilAttestering -> throw IllegalArgumentException("Ugyldig tilstand")
        is UnderkjentSøknadsbehandling.Avslag.MedBeregning -> underliggendeSøknadsbehandling.beregning
        is UnderkjentSøknadsbehandling.Avslag.UtenBeregning -> null
        is UnderkjentSøknadsbehandling.Innvilget -> underliggendeSøknadsbehandling.beregning
        is VilkårsvurdertSøknadsbehandling.Avslag -> null
        is VilkårsvurdertSøknadsbehandling.Innvilget -> null
        is VilkårsvurdertSøknadsbehandling.Uavklart -> null
    }

    override val simulering = when (underliggendeSøknadsbehandling) {
        is BeregnetSøknadsbehandling.Avslag -> null
        is BeregnetSøknadsbehandling.Innvilget -> null
        is IverksattSøknadsbehandling -> throw IllegalArgumentException("Ugyldig tilstand")
        is LukketSøknadsbehandling -> throw IllegalArgumentException("Ugyldig tilstand")
        is SimulertSøknadsbehandling -> underliggendeSøknadsbehandling.simulering
        is SøknadsbehandlingTilAttestering -> throw IllegalArgumentException("Ugyldig tilstand")
        is UnderkjentSøknadsbehandling.Avslag.MedBeregning -> null
        is UnderkjentSøknadsbehandling.Avslag.UtenBeregning -> null
        is UnderkjentSøknadsbehandling.Innvilget -> underliggendeSøknadsbehandling.simulering
        is VilkårsvurdertSøknadsbehandling.Avslag -> null
        is VilkårsvurdertSøknadsbehandling.Innvilget -> null
        is VilkårsvurdertSøknadsbehandling.Uavklart -> null
    }

    override fun copyInternal(
        stønadsperiode: Stønadsperiode,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        avkorting: AvkortingVedSøknadsbehandling,
        søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
        aldersvurdering: Aldersvurdering,
    ) = throw UnsupportedOperationException("Kan ikke kalle copyInternal på en lukket søknadsbehandling.")

    init {
        kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
        validateState(this.underliggendeSøknadsbehandling).onLeft {
            throw IllegalArgumentException("Ugyldig tilstand. Underliggende feil: $it")
        }
    }

    override fun leggTilSkatt(skatt: EksternGrunnlagSkattRequest): Either<KunneIkkeLeggeTilSkattegrunnlag, Søknadsbehandling> =
        KunneIkkeLeggeTilSkattegrunnlag.UgyldigTilstand.left()

    companion object {

        /**
         * Prøver lukke søknadsbehandlingen og tilhørende søknad.
         * Den underliggende søknadsbehandlingen kan ikke være av typen [LukketSøknadsbehandling], [Søknadsbehandling.Iverksatt] eller [SøknadsbehandlingTilAttestering]
         * @throws IllegalStateException Dersom den underliggende søknaden ikke er av typen [Søknad.Journalført.MedOppgave.IkkeLukket]
         */
        fun tryCreate(
            søknadsbehandlingSomSkalLukkes: Søknadsbehandling,
            lukkSøknadCommand: LukkSøknadCommand,
        ): Either<KunneIkkeLukkeSøknadsbehandling, LukketSøknadsbehandling> {
            validateState(søknadsbehandlingSomSkalLukkes).onLeft {
                return it.left()
            }
            return when (val søknad = søknadsbehandlingSomSkalLukkes.søknad) {
                is Søknad.Journalført.MedOppgave.IkkeLukket -> LukketSøknadsbehandling(
                    underliggendeSøknadsbehandling = søknadsbehandlingSomSkalLukkes,
                    søknad = søknad.lukk(
                        lukkSøknadCommand = lukkSøknadCommand,
                    ),
                    søknadsbehandlingsHistorikk = søknadsbehandlingSomSkalLukkes.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                        saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                            tidspunkt = lukkSøknadCommand.lukketTidspunkt,
                            saksbehandler = lukkSøknadCommand.saksbehandler,
                            handling = SøknadsbehandlingsHandling.Lukket,
                        ),
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
            validateState(søknadsbehandling).onLeft {
                throw IllegalArgumentException("Ugyldig tilstand. Underliggende feil: $it")
            }
            return LukketSøknadsbehandling(
                underliggendeSøknadsbehandling = søknadsbehandling,
                søknad = søknad,
                søknadsbehandlingsHistorikk = søknadsbehandling.søknadsbehandlingsHistorikk,
            )
        }

        private fun validateState(søknadsbehandlingSomSkalLukkes: Søknadsbehandling): Either<KunneIkkeLukkeSøknadsbehandling, Unit> {
            if (søknadsbehandlingSomSkalLukkes is LukketSøknadsbehandling) {
                return KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnAlleredeLukketSøknadsbehandling.left()
            }
            if (søknadsbehandlingSomSkalLukkes is IverksattSøknadsbehandling) {
                return KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnIverksattSøknadsbehandling.left()
            }
            if (søknadsbehandlingSomSkalLukkes is SøknadsbehandlingTilAttestering) {
                return KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnSøknadsbehandlingTilAttestering.left()
            }
            return Unit.right()
        }
    }

    override fun accept(visitor: SøknadsbehandlingVisitor) = visitor.visit(this)
}
