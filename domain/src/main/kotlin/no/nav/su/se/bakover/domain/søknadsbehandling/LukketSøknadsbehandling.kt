package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.søknadsbehandling.domain.avbryt.KunneIkkeLukkeSøknadsbehandling
import no.nav.su.se.bakover.common.domain.Avsluttet
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import org.jetbrains.annotations.TestOnly
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt

/**
 * En avvist søknadsbehandling kan resultere i et "vedtak" eller "avbrutt". Derfor arver den fra "avsluttet" og ikke "avbrutt" eller "vedtak".
 * "Trukket" og "Bortfalt" anses som "avbrutt".
 */
data class LukketSøknadsbehandling private constructor(
    val underliggendeSøknadsbehandling: Søknadsbehandling,
    override val søknad: Søknad.Journalført.MedOppgave.Lukket,
    override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
) : Søknadsbehandling,
    Avsluttet {
    override val avsluttetTidspunkt: Tidspunkt = søknad.lukketTidspunkt
    override val avsluttetAv: NavIdentBruker = søknad.lukketAv
    override val aldersvurdering: Aldersvurdering? = underliggendeSøknadsbehandling.aldersvurdering
    override val stønadsperiode: Stønadsperiode? get() = aldersvurdering?.stønadsperiode
    override val grunnlagsdataOgVilkårsvurderinger = underliggendeSøknadsbehandling.grunnlagsdataOgVilkårsvurderinger
    override val attesteringer = underliggendeSøknadsbehandling.attesteringer
    override val fritekstTilBrev = underliggendeSøknadsbehandling.fritekstTilBrev
    override val oppgaveId = underliggendeSøknadsbehandling.oppgaveId
    override val id = underliggendeSøknadsbehandling.id
    override val opprettet = underliggendeSøknadsbehandling.opprettet
    override val sakId = underliggendeSøknadsbehandling.sakId
    override val saksnummer = underliggendeSøknadsbehandling.saksnummer
    override val fnr = underliggendeSøknadsbehandling.fnr
    override val årsak: Revurderingsårsak.Årsak? = underliggendeSøknadsbehandling.årsak
    override val omgjøringsgrunn: Omgjøringsgrunn? = underliggendeSøknadsbehandling.omgjøringsgrunn

    // Så vi kan initialiseres uten at periode er satt (typisk ved ny søknadsbehandling)
    override val periode get() = underliggendeSøknadsbehandling.periode

    override val sakstype: Sakstype = underliggendeSøknadsbehandling.sakstype

    /**
     * Saksbehandler som lukket søknadsbehandling og søknad.
     * For siste handling før dette; se saksbehandler på [underliggendeSøknadsbehandling].
     */
    override val saksbehandler: NavIdentBruker.Saksbehandler = søknad.lukketAv

    val lukketTidspunkt = søknad.lukketTidspunkt
    val lukketAv = søknad.lukketAv

    override fun erÅpen() = false
    override fun erAvsluttet() = true

    override fun erAvbrutt() = søknad.erAvbrutt()

    override fun skalSendeVedtaksbrev(): Boolean {
        return søknad.brevvalg.skalSendeBrev()
    }

    override fun oppdaterOppgaveId(oppgaveId: OppgaveId): Søknadsbehandling =
        throw IllegalStateException("Skal ikke kunne oppdatere oppgave for en lukket søknadsbehandling $id")

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

    init {
        kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
        validateState(this.underliggendeSøknadsbehandling).onLeft {
            throw IllegalArgumentException("Ugyldig tilstand. Underliggende feil: $it")
        }
    }

    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt) = KunneIkkeLeggeTilSkattegrunnlag.UgyldigTilstand.left()

    companion object {

        /**
         * Prøver lukke søknadsbehandlingen og tilhørende søknad.
         * Den underliggende søknadsbehandlingen kan ikke være av typen [LukketSøknadsbehandling], [IverksattSøknadsbehandling] eller [SøknadsbehandlingTilAttestering]
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

    /**
     * TODO jah: Det er litt uheldig at søknadsbehandlingshistorikken ligger på LukketSøknad, men ikke den underliggende søknadsbehandlingen når den lukkes fra domenet, men den ligger på begge når den hentes fra databasen. Sammen med en private konstruktør, blir det vanskelig å teste.
     *  Måtte legge til denne funksjonen pga. privat konstruktør.
     */
    @TestOnly
    fun oppdaterSøknadshistorikkForTest(historikk: Søknadsbehandlingshistorikk): LukketSøknadsbehandling {
        return LukketSøknadsbehandling(
            underliggendeSøknadsbehandling = underliggendeSøknadsbehandling,
            søknad = søknad,
            søknadsbehandlingsHistorikk = historikk,
        )
    }
}
