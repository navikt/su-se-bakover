package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårAvslått
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsinnholdAlder
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt0
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

val behandlingsinformasjonAlleVilkårUavklart = Behandlingsinformasjon
    .lagTomBehandlingsinformasjon()

val behandlingsinformasjonAlleVilkårInnvilget = Behandlingsinformasjon
    .lagTomBehandlingsinformasjon()
    .withAlleVilkårOppfylt()

val behandlingsinformasjonAlleVilkårAvslått = Behandlingsinformasjon
    .lagTomBehandlingsinformasjon()
    .withAlleVilkårAvslått()

/**
 * Skal tilsvare en ny søknadsbehandling.
 * TODO jah: Vi bør kunne gjøre dette via NySøknadsbehandling og en funksjon som tar inn saksnummer og gir oss Søknadsbehandling.Vilkårsvurdert.Uavklart
 */
fun søknadsbehandlingVilkårsvurdertUavklart(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode? = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = Grunnlagsdata.IkkeVurdert,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårUavklart,
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
    clock: Clock = fixedClock,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Uavklart> {
    return nySakMedjournalførtSøknadOgOppgave(
        sakId = sakId,
        saksnummer = saksnummer,
        oppgaveId = oppgaveIdSøknad,
        fnr = fnr,
    ).let { (sak, journalførtSøknadMedOppgave) ->
        val søknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            søknad = journalførtSøknadMedOppgave,
            oppgaveId = journalførtSøknadMedOppgave.oppgaveId,
            behandlingsinformasjon = behandlingsinformasjon,
            fnr = sak.fnr,
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = avkorting,
            sakstype = sak.type,
        )
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(søknadsbehandling),
            ),
            søknadsbehandling,
        )
    }
}

fun søknadsbehandlingVilkårsvurdertInnvilget(
    clock: Clock = fixedClock,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Innvilget> {
    return søknadsbehandlingVilkårsvurdertUavklart(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        avkorting = avkorting.kanIkke(),
    ).let { (sak, søknadsbehandling) ->
        søknadsbehandling.copy(
            behandlingsinformasjon = behandlingsinformasjon,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        ).vilkårsvurder(clock).let { vilkårsvurdert ->
            vilkårsvurdert.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>().let {
                Pair(
                    sak.copy(
                        søknadsbehandlinger = nonEmptyListOf(it),
                    ),
                    it,
                )
            }
        }
    }
}

/**
 * @param grunnlagsdata defaults to enslig uten fradrag
 * @param vilkårsvurderinger alle vilkår gir avslag
 */
fun søknadsbehandlingVilkårsvurdertAvslag(
    clock: Clock = fixedClock,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårAvslått,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Avslag> {
    return søknadsbehandlingVilkårsvurdertUavklart(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ).let { (sak, søknadsbehandling) ->
        søknadsbehandling.copy(
            behandlingsinformasjon = behandlingsinformasjon,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        ).vilkårsvurder(clock).let { vilkårsvurdert ->
            vilkårsvurdert.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>().let {
                Pair(
                    sak.copy(
                        søknadsbehandlinger = nonEmptyListOf(it),
                    ),
                    it,
                )
            }
        }
    }
}

fun søknadsbehandlingBeregnetInnvilget(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
    clock: Clock = fixedClock,
): Pair<Sak, Søknadsbehandling.Beregnet.Innvilget> {
    return søknadsbehandlingVilkårsvurdertInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        avkorting = avkorting.kanIkke(),
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.beregn(
            begrunnelse = null,
            clock = clock,
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail() as Søknadsbehandling.Beregnet.Innvilget
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

/**
 * Defaultverdier:
 * - Forventet inntekt: 1_000_000
 *
 * @param beregning må gi avslag, hvis ikke får man en runtime exception
 */
fun søknadsbehandlingBeregnetAvslag(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        periode = stønadsperiode.periode,
        uføre = innvilgetUførevilkårForventetInntekt0(
            id = UUID.randomUUID(),
            periode = stønadsperiode.periode,
            uføregrunnlag = uføregrunnlagForventetInntekt(
                periode = stønadsperiode.periode,
                forventetInntekt = 1_000_000,
            ),
        ),
    ),
    clock: Clock = fixedClock,
): Pair<Sak, Søknadsbehandling.Beregnet.Avslag> {
    return søknadsbehandlingVilkårsvurdertInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.beregn(
            begrunnelse = null,
            clock = clock,
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail() as Søknadsbehandling.Beregnet.Avslag
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingSimulert(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Simulert> {
    return søknadsbehandlingBeregnetInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        avkorting = avkorting,
    ).let { (sak, søknadsbehandling) ->
        søknadsbehandling.simuler(
            saksbehandler = saksbehandler,
        ) {
            simulerNyUtbetaling(
                sak = sak,
                request = it,
                clock = fixedClock,
            )
        }.getOrFail()
            .let { simulert ->
                Pair(
                    sak.copy(søknadsbehandlinger = sak.søknadsbehandlinger + simulert),
                    simulert,
                )
            }
    }
}

fun søknadsbehandlingTilAttesteringInnvilget(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.TilAttestering.Innvilget> {
    return søknadsbehandlingSimulert(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        avkorting = avkorting,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
        )
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingTilAttesteringAvslagMedBeregning(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        periode = stønadsperiode.periode,
        uføre = innvilgetUførevilkårForventetInntekt0(
            id = UUID.randomUUID(),
            periode = stønadsperiode.periode,
            uføregrunnlag = uføregrunnlagForventetInntekt(
                periode = stønadsperiode.periode,
                forventetInntekt = 1_000_000,
            ),
        ),
    ),
): Pair<Sak, Søknadsbehandling.TilAttestering.Avslag.MedBeregning> {
    return søknadsbehandlingBeregnetAvslag(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
        )
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingTilAttesteringAvslagUtenBeregning(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårAvslått,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
): Pair<Sak, Søknadsbehandling.TilAttestering.Avslag.UtenBeregning> {
    return søknadsbehandlingVilkårsvurdertAvslag(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
        )
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingUnderkjentInnvilget(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(periode = stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    clock: Clock = fixedClock,
    attestering: Attestering = attesteringUnderkjent(clock = clock),
): Pair<Sak, Søknadsbehandling.Underkjent.Innvilget> {
    return søknadsbehandlingTilAttesteringInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilUnderkjent(
            attestering = attestering,
        )
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingUnderkjentAvslagUtenBeregning(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
    clock: Clock = fixedClock,
    attestering: Attestering = attesteringUnderkjent(clock = clock),
): Pair<Sak, Søknadsbehandling.Underkjent.Avslag.UtenBeregning> {
    return søknadsbehandlingTilAttesteringAvslagUtenBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilUnderkjent(
            attestering = attestering,
        )
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingUnderkjentAvslagMedBeregning(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        periode = stønadsperiode.periode,
        uføre = innvilgetUførevilkårForventetInntekt0(
            id = UUID.randomUUID(),
            periode = stønadsperiode.periode,
            uføregrunnlag = uføregrunnlagForventetInntekt(
                periode = stønadsperiode.periode,
                forventetInntekt = 1_000_000,
            ),
        ),
    ),
    clock: Clock = fixedClock,
    attestering: Attestering = attesteringUnderkjent(clock = clock),
): Pair<Sak, Søknadsbehandling.Underkjent.Avslag.MedBeregning> {
    return søknadsbehandlingTilAttesteringAvslagMedBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilUnderkjent(
            attestering = attestering,
        )
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingIverksattInnvilget(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    clock: Clock = fixedClock,
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Iverksatt.Innvilget> {
    return søknadsbehandlingTilAttesteringInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        avkorting = avkorting,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilIverksatt(
            attestering = attesteringIverksatt(clock),
        )
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingIverksattAvslagMedBeregning(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        periode = stønadsperiode.periode,
        uføre = innvilgetUførevilkårForventetInntekt0(
            id = UUID.randomUUID(),
            periode = stønadsperiode.periode,
            uføregrunnlag = uføregrunnlagForventetInntekt(
                periode = stønadsperiode.periode,
                forventetInntekt = 1_000_000,
            ),
        ),
    ),
    clock: Clock = fixedClock,
): Pair<Sak, Søknadsbehandling.Iverksatt.Avslag.MedBeregning> {
    return søknadsbehandlingTilAttesteringAvslagMedBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilIverksatt(
            attestering = attesteringIverksatt(clock),
        )
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingIverksattAvslagUtenBeregning(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårAvslått,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
    clock: Clock = fixedClock,
): Pair<Sak, Søknadsbehandling.Iverksatt.Avslag.UtenBeregning> {
    return søknadsbehandlingTilAttesteringAvslagUtenBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilIverksatt(
            attestering = attesteringIverksatt(clock = clock.plus(1, ChronoUnit.SECONDS)),
        )
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

/**
 * En lukket uavklart vilkårsvurdert søknadsbehandling
 */
fun søknadsbehandlingLukket(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
): Pair<Sak, LukketSøknadsbehandling> {
    return søknadsbehandlingVilkårsvurdertUavklart(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ).run {
        Pair(first, second.lukkSøknadsbehandling().orNull()!!)
    }
}

fun nySøknadsbehandlingAlder(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakAlder(søknadsInnhold = søknadsinnholdAlder()),
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    require(sakOgSøknad.first.type == Sakstype.ALDER) { "Bruk nySøknadsbehandlingUføre dersom du ønsker deg en uføresak." }
    return nySøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
    )
}

fun nySøknadsbehandlingUføre(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    require(sakOgSøknad.first.type == Sakstype.UFØRE) { "Bruk nySøknadsbehandlingAlder dersom du ønsker deg en alderssak." }
    return nySøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
    )
}

/**
 * Oppretter en søknadsbehandling med bagrunn i [sakOgSøknad]. Støtter både uføre og alder.
 */
fun nySøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave>,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    return sakOgSøknad.let { (sak, søknad) ->
        val søknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            søknad = søknad,
            oppgaveId = søknad.oppgaveId,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = sak.fnr,
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = when (sak.type) {
                Sakstype.ALDER -> {
                    Vilkårsvurderinger.Søknadsbehandling.Alder.ikkeVurdert()
                }
                Sakstype.UFØRE -> {
                    Vilkårsvurderinger.Søknadsbehandling.Uføre.ikkeVurdert()
                }
            },
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
            sakstype = sak.type,
        )

        // legg til for å oppdater stønadsperiode
        val førOppdatertStønadsperiode = sak.copy(
            søknader = sak.søknader.filterNot { it.id == søknad.id } + søknad, // replace hvis søknaden allerede er lagt til (f.eks hvis man først oppretter bare sak + søknad)
            søknadsbehandlinger = sak.søknadsbehandlinger + søknadsbehandling,
        )

        // oppdater stønadsperiode via sak for å sjekke gyldigheten
        val etterOppdatertStønadsperiode = førOppdatertStønadsperiode.oppdaterStønadsperiodeForSøknadsbehandling(
            søknadsbehandlingId = søknadsbehandling.id,
            stønadsperiode = stønadsperiode,
            clock = clock,
            formuegrenserFactory = formuegrenserFactoryTestPåDato(LocalDate.now(clock)),
        ).getOrFail()

        sak.copy(
            søknader = sak.søknader.filterNot { it.id == søknad.id } + søknad, // replace hvis søknaden allerede er lagt til (f.eks hvis man først oppretter bare sak + søknad)
            søknadsbehandlinger = sak.søknadsbehandlinger.filterNot { it.id == etterOppdatertStønadsperiode.id } + etterOppdatertStønadsperiode,
        ) to etterOppdatertStønadsperiode
    }
}

fun vilkårsvurdertSøknadsbehandlingAlder(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakAlder(
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    customBehandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        customBehandlingsinformasjon = customBehandlingsinformasjon,
    )
}

fun underkjentSøknadsbehandlingUføre(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    customBehandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
): Pair<Sak, Søknadsbehandling.Underkjent> {
    return underkjentSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        customBehandlingsinformasjon = customBehandlingsinformasjon,
    )
}

fun underkjentSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave>,
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    customBehandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
): Pair<Sak, Søknadsbehandling.Underkjent> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        customBehandlingsinformasjon = customBehandlingsinformasjon,
    ).let { (sak, tilAttestering) ->
        val underkjent = tilAttestering.tilUnderkjent(attestering = attesteringUnderkjent(clock))
        sak.copy(søknadsbehandlinger = sak.søknadsbehandlinger.filterNot { it.id == tilAttestering.id } + underkjent) to underkjent
    }
}

fun tilAttesteringSøknadsbehandlingUføre(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    customBehandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
): Pair<Sak, Søknadsbehandling.TilAttestering> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        customBehandlingsinformasjon = customBehandlingsinformasjon,
    )
}

fun tilAttesteringSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave>,
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    customBehandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
): Pair<Sak, Søknadsbehandling.TilAttestering> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        customBehandlingsinformasjon = customBehandlingsinformasjon,
    ).let { (sak, vilkårsvurdert) ->
        val tilAttestering = when (vilkårsvurdert) {
            // avslag for vilkår går rett til attestering
            is Søknadsbehandling.Vilkårsvurdert.Avslag -> {
                vilkårsvurdert.tilAttestering(
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "",
                )
            }
            is Søknadsbehandling.Vilkårsvurdert.Innvilget -> {
                beregnetSøknadsbehandling(
                    clock = clock,
                    stønadsperiode = stønadsperiode,
                    sakOgSøknad = sakOgSøknad,
                    customGrunnlag = customGrunnlag,
                    customVilkår = customVilkår,
                    customBehandlingsinformasjon = customBehandlingsinformasjon,
                ).let { (_, beregnet) ->
                    when (beregnet) {
                        // beregnet avslag går til attestering
                        is Søknadsbehandling.Beregnet.Avslag -> {
                            beregnet.tilAttestering(
                                saksbehandler = saksbehandler,
                                fritekstTilBrev = "",
                            )
                        }
                        is Søknadsbehandling.Beregnet.Innvilget -> {
                            // simuler og send til attestering hvis innvilget
                            simulertSøknadsbehandling(
                                clock = clock,
                                stønadsperiode = stønadsperiode,
                                sakOgSøknad = sakOgSøknad,
                                customGrunnlag = customGrunnlag,
                                customVilkår = customVilkår,
                                customBehandlingsinformasjon = customBehandlingsinformasjon,
                            ).let { (_, simulert) ->
                                simulert.tilAttestering(
                                    saksbehandler = saksbehandler,
                                    fritekstTilBrev = "",
                                )
                            }
                        }
                    }
                }
            }
            is Søknadsbehandling.Vilkårsvurdert.Uavklart -> {
                throw IllegalStateException("Kan ikke attestere uavklart")
            }
        }
        sak.copy(søknadsbehandlinger = sak.søknadsbehandlinger.filterNot { it.id == vilkårsvurdert.id } + tilAttestering) to tilAttestering
    }
}

fun simulertSøknadsbehandlingUføre(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    customBehandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
): Pair<Sak, Søknadsbehandling.Simulert> {
    return simulertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        customBehandlingsinformasjon = customBehandlingsinformasjon,
    )
}

fun simulertSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave>,
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    customBehandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
): Pair<Sak, Søknadsbehandling.Simulert> {
    return beregnetSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        customBehandlingsinformasjon = customBehandlingsinformasjon,
    ).let { (sak, beregnet) ->
        beregnet.simuler(
            saksbehandler = saksbehandler,
            simuler = {
                simulerNyUtbetaling(
                    sak = sak,
                    request = it,
                    clock = clock,
                )
            },
        ).getOrFail().let { simulert ->
            sak.copy(søknadsbehandlinger = sak.søknadsbehandlinger.filterNot { it.id == beregnet.id } + simulert) to simulert
        }
    }
}

fun beregnetSøknadsbehandlingUføre(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    customBehandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
): Pair<Sak, Søknadsbehandling.Beregnet> {
    return beregnetSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        customBehandlingsinformasjon = customBehandlingsinformasjon,
    )
}

fun beregnetSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave>,
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    customBehandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
): Pair<Sak, Søknadsbehandling.Beregnet> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        customBehandlingsinformasjon = customBehandlingsinformasjon,
    ).let { (sak, vilkårsvurdert) ->
        vilkårsvurdert.beregn(
            begrunnelse = null,
            clock = clock,
            satsFactory = satsFactoryTestPåDato(vilkårsvurdert.opprettet.toLocalDate(zoneIdOslo)),
        ).getOrFail().let { beregnet ->
            sak.copy(søknadsbehandlinger = sak.søknadsbehandlinger.filterNot { it.id == vilkårsvurdert.id } + beregnet) to beregnet
        }
    }
}

fun vilkårsvurdertSøknadsbehandlingUføre(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    customBehandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        customBehandlingsinformasjon = customBehandlingsinformasjon,
    )
}

/**
 * Returnerer en [Søknadsbehandling.Vilkårsvurdert] baset på [sakOgSøknad]. Støtter både uføre og alder.
 * Default er at det opprettes en [Søknadsbehandling.Vilkårsvurdert.Innvilget], men funkjsonen støtter også opprettelse
 * av alle typer [Søknadsbehandling.Vilkårsvurdert] - hvilken man ender opp med til slutt avhenger av utfallet av
 * vilkårsvurderingen.
 *
 * @param sakOgSøknad sak og søknad det skal opprettes søknadsbehandling for
 * @param customGrunnlag brukes for å spesifisere grunnlag som skal overstyre default
 * @param customVilkår brukes for å overstyre vilkår som skal overstyre default
 */
fun vilkårsvurdertSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave>,
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    customBehandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    customVilkår.ifNotEmpty {
        require(this.groupBy { it::class }.values.count() == 1) { "Tillater bare et vilkår av hver type" }
    }

    val vilkårFraBehandlingsinformasjon = customBehandlingsinformasjon
    val (grunnlagsdata, vilkår) = GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
        grunnlagsdata = grunnlagsdataEnsligUtenFradrag(
            periode = stønadsperiode.periode,
        ),
        vilkårsvurderinger = when (sakOgSøknad.first.type) {
            Sakstype.ALDER -> {
                vilkårsvurderingerAlderInnvilget(
                    stønadsperiode = stønadsperiode,
                    behandlingsinformasjon = vilkårFraBehandlingsinformasjon,
                )
            }
            Sakstype.UFØRE -> {
                vilkårsvurderingerSøknadsbehandlingInnvilget(
                    periode = stønadsperiode.periode,
                    behandlingsinformasjon = vilkårFraBehandlingsinformasjon,
                )
            }
        },
    )
    return nySøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
    ).let { (sak, søknadsbehandling) ->
        val etterOppdaterFraBehandlingsinformasjon = søknadsbehandling.leggTilVilkårFraBehandlingsinformasjon(
            behandlingsinformasjon = vilkårFraBehandlingsinformasjon,
            clock = clock,
        ).getOrFail()

        val vilkårsvurdert = when (vilkår) {
            is Vilkårsvurderinger.Søknadsbehandling.Alder -> {
                etterOppdaterFraBehandlingsinformasjon.oppdaterBosituasjon(
                    bosituasjon = customGrunnlag.customOrDefault { grunnlagsdata.bosituasjon }.single(),
                    clock = clock,
                )
                    .getOrFail()
                    .leggTilFormuevilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.formue as Vilkår.Formue.Vurdert },
                        clock = clock,
                    ).getOrFail()
                    .leggTilUtenlandsopphold(
                        utenlandsopphold = customVilkår.customOrDefault { vilkår.utenlandsopphold as UtenlandsoppholdVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .leggTilOpplysningspliktVilkår(
                        opplysningspliktVilkår = customVilkår.customOrDefault { vilkår.opplysningsplikt as OpplysningspliktVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .leggTilPensjonsVilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.pensjon as PensjonsVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .leggTilFamiliegjenforeningvilkår(
                        familiegjenforening = customVilkår.customOrDefault { vilkår.familiegjenforening as FamiliegjenforeningVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
            }
            is Vilkårsvurderinger.Søknadsbehandling.Uføre -> {
                etterOppdaterFraBehandlingsinformasjon.oppdaterBosituasjon(
                    bosituasjon = customGrunnlag.customOrDefault { grunnlagsdata.bosituasjon }.single(),
                    clock = clock,
                )
                    .getOrFail()
                    .leggTilFormuevilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.formue as Vilkår.Formue.Vurdert },
                        clock = clock,
                    ).getOrFail()
                    .leggTilUtenlandsopphold(
                        utenlandsopphold = customVilkår.customOrDefault { vilkår.utenlandsopphold as UtenlandsoppholdVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .leggTilOpplysningspliktVilkår(
                        opplysningspliktVilkår = customVilkår.customOrDefault { vilkår.opplysningsplikt as OpplysningspliktVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .leggTilUførevilkår(
                        uførhet = customVilkår.customOrDefault { vilkår.uføre as Vilkår.Uførhet.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
            }
        }

        val medFradrag = if (customGrunnlag.customOrDefault { grunnlagsdata.fradragsgrunnlag }.isNotEmpty()) {
            vilkårsvurdert.leggTilFradragsgrunnlag(
                fradragsgrunnlag = customGrunnlag.customOrDefault { grunnlagsdata.fradragsgrunnlag },
                clock = clock,
            )
                .getOrFail()
        } else {
            vilkårsvurdert
        }

        sak.copy(søknadsbehandlinger = sak.søknadsbehandlinger.filterNot { it.id == medFradrag.id } + medFradrag) to medFradrag
    }
}

private inline fun <reified T : Vilkår> List<Vilkår>.customOrDefault(default: () -> T): T {
    return filterIsInstance<T>().singleOrNull() ?: default()
}

private inline fun <reified T : Grunnlag> List<Grunnlag>.customOrDefault(default: () -> List<T>): List<T> {
    return filterIsInstance<T>().ifEmpty { default() }
}
