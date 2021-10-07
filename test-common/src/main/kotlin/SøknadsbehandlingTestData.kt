package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import arrow.core.orNull
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

val behandlingsinformasjonAlleVilkårUavklart = Behandlingsinformasjon
    .lagTomBehandlingsinformasjon()

val behandlingsinformasjonAlleVilkårInnvilget = Behandlingsinformasjon
    .lagTomBehandlingsinformasjon()
    .withAlleVilkårOppfylt()

val behandlingsinformasjonAlleVilkårAvslått = Behandlingsinformasjon
    .lagTomBehandlingsinformasjon()
    .withVilkårAvslått()

/**
 * Skal tilsvare en ny søknadsbehandling.
 * TODO jah: Vi bør kunne gjøre dette via NySøknadsbehandling og en funksjon som tar inn saksnummer og gir oss Søknadsbehandling.Vilkårsvurdert.Uavklart
 */
fun søknadsbehandlingVilkårsvurdertUavklart(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Uavklart> {
    return nySakMedjournalførtSøknadOgOppgave(
        sakId = sakId,
        saksnummer = saksnummer,
        oppgaveId = oppgaveIdSøknad,
        fnr = fnr,
    ).let { (sak, journalførtSøknadMedOppgave) ->
        val søknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            søknad = journalførtSøknadMedOppgave,
            oppgaveId = journalførtSøknadMedOppgave.oppgaveId,
            behandlingsinformasjon = behandlingsinformasjonAlleVilkårUavklart,
            fnr = sak.fnr,
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
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
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Innvilget> {
    return søknadsbehandlingVilkårsvurdertUavklart(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = (
            søknadsbehandling.tilVilkårsvurdert(
                behandlingsinformasjon,
                // TODO jah: Bytt til å bruke en mer spesifikk funksjon på søknadsbehandling når/hvis det kommer.
            ) as Søknadsbehandling.Vilkårsvurdert.Innvilget
            ).copy(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        )
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingVilkårsvurdertAvslag(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårAvslått,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Avslag> {
    return søknadsbehandlingVilkårsvurdertUavklart(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = (
            // TODO jah: Bytt til å bruke en mer spesifikk funksjon på søknadsbehandling når/hvis det kommer.
            søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon) as Søknadsbehandling.Vilkårsvurdert.Avslag
            )
            .copy(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
            )
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
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
): Pair<Sak, Søknadsbehandling.Beregnet.Innvilget> {
    return søknadsbehandlingVilkårsvurdertInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling =
            søknadsbehandling.tilBeregnet(beregning) as Søknadsbehandling.Beregnet.Innvilget
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
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(
        uføregrunnlag = nonEmptyListOf(
            uføregrunnlagForventetInntekt(
                periode = stønadsperiode.periode,
                forventetInntekt = 1_000_000,
            ),
        ),
    ),
): Pair<Sak, Søknadsbehandling.Beregnet.Avslag> {
    return søknadsbehandlingVilkårsvurdertInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilBeregnet(beregning) as Søknadsbehandling.Beregnet.Avslag
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
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
): Pair<Sak, Søknadsbehandling.Simulert> {
    return søknadsbehandlingBeregnetInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling =
            søknadsbehandling.tilSimulert(simulering = simuleringNy(eksisterendeUtbetalinger = sak.utbetalinger))
        Pair(
            sak.copy(
                søknadsbehandlinger = nonEmptyListOf(oppdatertSøknadsbehandling),
            ),
            oppdatertSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingTilAttesteringInnvilget(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
): Pair<Sak, Søknadsbehandling.TilAttestering.Innvilget> {
    return søknadsbehandlingSimulert(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
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
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
): Pair<Sak, Søknadsbehandling.TilAttestering.Avslag.MedBeregning> {
    return søknadsbehandlingBeregnetAvslag(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
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
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
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
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
    attestering: Attestering = attesteringUnderkjent,
): Pair<Sak, Søknadsbehandling.Underkjent.Innvilget> {
    return søknadsbehandlingTilAttesteringInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
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
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
): Pair<Sak, Søknadsbehandling.Iverksatt.Innvilget> {
    return søknadsbehandlingTilAttesteringInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilIverksatt(
            attestering = attesteringIverksatt,
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
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregningAvslag(stønadsperiode.periode),
): Pair<Sak, Søknadsbehandling.Iverksatt.Avslag.MedBeregning> {
    return søknadsbehandlingTilAttesteringAvslagMedBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilIverksatt(
            attestering = attesteringIverksatt,
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
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
): Pair<Sak, Søknadsbehandling.Iverksatt.Avslag.UtenBeregning> {
    return søknadsbehandlingTilAttesteringAvslagUtenBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilIverksatt(
            attestering = attesteringIverksatt,
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
