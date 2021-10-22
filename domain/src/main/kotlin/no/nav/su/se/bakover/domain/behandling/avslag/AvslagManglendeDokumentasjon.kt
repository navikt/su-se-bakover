package no.nav.su.se.bakover.domain.behandling.avslag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.søknadsbehandling.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.Clock
import java.time.LocalDate

data class AvslagManglendeDokumentasjon private constructor(
    val søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning,
) : ErAvslag {
    override val avslagsgrunner: List<Avslagsgrunn> = listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON)

    companion object {
        fun tryCreate(
            søknadsbehandling: Søknadsbehandling,
            saksbehandler: NavIdentBruker.Saksbehandler,
            fritekstTilBrev: String = "",
            clock: Clock,
        ): Either<SøknadsbehandlingErIUgyldigTilstand, AvslagManglendeDokumentasjon> {
            return when (søknadsbehandling) {
                is Søknadsbehandling.Iverksatt,
                is LukketSøknadsbehandling,
                is Søknadsbehandling.TilAttestering,
                -> {
                    SøknadsbehandlingErIUgyldigTilstand.left()
                }
                else -> {
                    AvslagManglendeDokumentasjon(
                        søknadsbehandling = Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
                            id = søknadsbehandling.id,
                            opprettet = søknadsbehandling.opprettet,
                            sakId = søknadsbehandling.sakId,
                            saksnummer = søknadsbehandling.saksnummer,
                            søknad = søknadsbehandling.søknad,
                            oppgaveId = søknadsbehandling.oppgaveId,
                            behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                            fnr = søknadsbehandling.fnr,
                            saksbehandler = saksbehandler,
                            attesteringer = søknadsbehandling.attesteringer.leggTilNyAttestering(
                                Attestering.Iverksatt(
                                    attestant = NavIdentBruker.Attestant(saksbehandler.navIdent),
                                    opprettet = Tidspunkt.now(clock),
                                ),
                            ),
                            fritekstTilBrev = fritekstTilBrev,
                            /**
                             * Setter stønadsperiode til inneværende måned dersom det ikke eksisterer.
                             * Workaround for å oppfylle krav om dette feltet, selv om det har liten praktisk
                             * betydning for avslag.
                             */
                            stønadsperiode = søknadsbehandling.stønadsperiode ?: Stønadsperiode.create(
                                periode = Periode.create(
                                    fraOgMed = LocalDate.now(clock).startOfMonth(),
                                    tilOgMed = LocalDate.now(clock).endOfMonth(),
                                ),
                                begrunnelse = "",
                            ),
                            grunnlagsdata = søknadsbehandling.grunnlagsdata,
                            vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger,
                        ),
                    ).right()
                }
            }
        }
    }

    object SøknadsbehandlingErIUgyldigTilstand
}
