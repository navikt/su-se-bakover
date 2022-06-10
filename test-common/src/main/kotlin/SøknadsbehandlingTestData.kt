package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårAvslått
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
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
        val oppdatertSøknadsbehandling = søknadsbehandling
            .copy(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
            ).tilVilkårsvurdert(
                behandlingsinformasjon = behandlingsinformasjon,
                clock = fixedClock,
            ) as Søknadsbehandling.Vilkårsvurdert.Innvilget
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

/**
 * @param grunnlagsdata defaults to enslig uten fradrag
 * @param vilkårsvurderinger alle vilkår gir avslag
 */
fun søknadsbehandlingVilkårsvurdertAvslag(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårAvslått,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Avslag> {
    return søknadsbehandlingVilkårsvurdertUavklart(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling
            .copy(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
            ).tilVilkårsvurdert(
                behandlingsinformasjon = behandlingsinformasjon,
                clock = fixedClock,
            ) as Søknadsbehandling.Vilkårsvurdert.Avslag
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
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
            formuegrenserFactory = formuegrenserFactoryTest,
            satsFactory = satsFactoryTest,
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
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
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
            formuegrenserFactory = formuegrenserFactoryTest,
            satsFactory = satsFactoryTest,
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
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
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
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
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
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
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
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
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
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
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
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
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
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
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
