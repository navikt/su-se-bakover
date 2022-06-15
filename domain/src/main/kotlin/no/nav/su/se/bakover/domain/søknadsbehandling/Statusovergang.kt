package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import java.time.Clock
import java.util.UUID

abstract class Statusovergang<L, T> : StatusovergangVisitor {

    protected lateinit var result: Either<L, T>
    fun get(): Either<L, T> = result

    class TilVilkårsvurdert(
        private val behandlingsinformasjon: Behandlingsinformasjon,
        private val clock: Clock,
    ) : Statusovergang<Nothing, Søknadsbehandling.Vilkårsvurdert>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }
    }

    class TilAttestering(
        private val saksbehandler: NavIdentBruker.Saksbehandler,
        private val fritekstTilBrev: String,
    ) : Statusovergang<Nothing, Søknadsbehandling.TilAttestering>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
            result = søknadsbehandling.tilAttestering(saksbehandler).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
            result = søknadsbehandling.tilAttestering(saksbehandler).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            result = søknadsbehandling.tilAttestering(saksbehandler).right()
        }
    }

    class TilUnderkjent(
        private val attestering: Attestering,
    ) : Statusovergang<SaksbehandlerOgAttestantKanIkkeVæreSammePerson, Søknadsbehandling.Underkjent>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
            evaluerStatusovergang(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
            evaluerStatusovergang(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
            evaluerStatusovergang(søknadsbehandling)
        }

        private fun evaluerStatusovergang(søknadsbehandling: Søknadsbehandling.TilAttestering) {
            result = when (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {
                true -> søknadsbehandling.tilUnderkjent(attestering).right()
                false -> SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
            }
        }

        private fun saksbehandlerOgAttestantErForskjellig(
            søknadsbehandling: Søknadsbehandling.TilAttestering,
            attestering: Attestering,
        ): Boolean = søknadsbehandling.saksbehandler.navIdent != attestering.attestant.navIdent
    }

    object SaksbehandlerOgAttestantKanIkkeVæreSammePerson

    class TilIverksatt(
        private val attestering: Attestering,
        private val hentOpprinneligAvkorting: (id: UUID) -> Avkortingsvarsel?,
    ) : Statusovergang<KunneIkkeIverksette, Søknadsbehandling.Iverksatt>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
            result = if (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {
                søknadsbehandling.tilIverksatt(attestering).right()
            } else {
                KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
            result = if (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {
                søknadsbehandling.tilIverksatt(attestering).right()
            } else {
                KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
            result = if (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {

                /**
                 * Skulle ideelt gjort dette inne i [Søknadsbehandling.TilAttestering.Innvilget.tilIverksatt], men må få
                 * sjekket dette før vi oversender til oppdrag.
                 * //TODO erstatt statusovergang med funksjon
                 */
                when (søknadsbehandling.avkorting) {
                    is AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående -> {
                        hentOpprinneligAvkorting(søknadsbehandling.avkorting.avkortingsvarsel.id).also { avkortingsvarsel ->
                            when (avkortingsvarsel) {
                                Avkortingsvarsel.Ingen -> {
                                    throw IllegalStateException("Prøver å iverksette avkorting uten at det finnes noe å avkorte")
                                }
                                is Avkortingsvarsel.Utenlandsopphold.Annullert -> {
                                    result = KunneIkkeIverksette.HarBlittAnnullertAvEnAnnen.left()
                                    return
                                }
                                is Avkortingsvarsel.Utenlandsopphold.Avkortet -> {
                                    result = KunneIkkeIverksette.HarAlleredeBlittAvkortetAvEnAnnen.left()
                                    return
                                }
                                is Avkortingsvarsel.Utenlandsopphold.Opprettet -> {
                                    throw IllegalStateException("Prøver å iverksette avkorting uten at det finnes noe å avkorte")
                                }
                                is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
                                    // Dette er den eneste som er gyldig
                                }
                                null -> {
                                    throw IllegalStateException("Prøver å iverksette avkorting uten at det finnes noe å avkorte")
                                }
                            }
                        }
                        if (!søknadsbehandling.avkorting.avkortingsvarsel.fullstendigAvkortetAv(
                                søknadsbehandling.beregning,
                            )
                        ) {
                            result = KunneIkkeIverksette.AvkortingErUfullstendig.left()
                            return
                        }
                    }
                    is AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående -> {
                        // noop
                    }
                    is AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere -> {
                        throw IllegalStateException("Søknadsbehandling:${søknadsbehandling.id} i tilstand ${søknadsbehandling::class} skal ha håndtert eventuell avkorting")
                    }
                }

                if (søknadsbehandling.simulering.harFeilutbetalinger()) {
                    /**
                     * Kun en nødbrems for tilfeller som i utgangspunktet skal være håndtert og forhindret av andre mekanismer.
                     * //TODO erstatt statusovergang med funksjon
                     */
                    throw IllegalStateException("Simulering inneholder feilutbetalinger")
                }

                søknadsbehandling.tilIverksatt(attestering).right()
            } else {
                KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
        }

        private fun saksbehandlerOgAttestantErForskjellig(
            søknadsbehandling: Søknadsbehandling.TilAttestering,
            attestering: Attestering,
        ): Boolean = søknadsbehandling.saksbehandler.navIdent != attestering.attestant.navIdent
    }
}

fun <T> statusovergang(
    søknadsbehandling: Søknadsbehandling,
    statusovergang: Statusovergang<Nothing, T>,
): T {
    // Kan aldri være Either.Left<Nothing>
    return forsøkStatusovergang(søknadsbehandling, statusovergang).orNull()!!
}

fun <L, T> forsøkStatusovergang(
    søknadsbehandling: Søknadsbehandling,
    statusovergang: Statusovergang<L, T>,
): Either<L, T> {
    søknadsbehandling.accept(statusovergang)
    return statusovergang.get()
}
