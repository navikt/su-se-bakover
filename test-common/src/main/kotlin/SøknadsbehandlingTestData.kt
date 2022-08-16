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
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsinnholdAlder
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
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

/**
 * Skal tilsvare en ny søknadsbehandling.
 * TODO jah: Vi bør kunne gjøre dette via NySøknadsbehandling og en funksjon som tar inn saksnummer og gir oss Søknadsbehandling.Vilkårsvurdert.Uavklart
 */
fun søknadsbehandlingVilkårsvurdertUavklart(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode? = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = Grunnlagsdata.IkkeVurdert,
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
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        ).vilkårsvurder().let { vilkårsvurdert ->
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
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Avslag> {
    return søknadsbehandlingVilkårsvurdertUavklart(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ).let { (sak, søknadsbehandling) ->
        søknadsbehandling.copy(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        ).vilkårsvurder().let { vilkårsvurdert ->
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
 * @param grunnlagsdata må gi avslag, hvis ikke får man en runtime exception
 */
fun søknadsbehandlingBeregnetAvslag(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
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
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Simulert> {
    return søknadsbehandlingBeregnetInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
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
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.TilAttestering.Innvilget> {
    return søknadsbehandlingSimulert(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
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
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
): Pair<Sak, Søknadsbehandling.TilAttestering.Avslag.UtenBeregning> {
    return søknadsbehandlingVilkårsvurdertAvslag(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
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
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
    clock: Clock = fixedClock,
    attestering: Attestering = attesteringUnderkjent(clock = clock),
): Pair<Sak, Søknadsbehandling.Underkjent.Avslag.UtenBeregning> {
    return søknadsbehandlingTilAttesteringAvslagUtenBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
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
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
    clock: Clock = fixedClock,
): Pair<Sak, Søknadsbehandling.Iverksatt.Avslag.UtenBeregning> {
    return søknadsbehandlingTilAttesteringAvslagUtenBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
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
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    require(sakOgSøknad.first.type == Sakstype.ALDER) { "Bruk nySøknadsbehandlingUføre dersom du ønsker deg en uføresak." }
    return nySøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        avkorting = avkorting,
    )
}

fun nySøknadsbehandlingUføre(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    require(sakOgSøknad.first.type == Sakstype.UFØRE) { "Bruk nySøknadsbehandlingAlder dersom du ønsker deg en alderssak." }
    return nySøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        avkorting = avkorting,
    )
}

/**
 * Oppretter en søknadsbehandling med bagrunn i [sakOgSøknad]. Støtter både uføre og alder.
 */
fun nySøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave>,
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    return sakOgSøknad.let { (sak, søknad) ->
        val søknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            søknad = søknad,
            oppgaveId = søknad.oppgaveId,
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
            avkorting = avkorting.kanIkke(),
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
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        avkorting = avkorting,
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
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Underkjent> {
    return underkjentSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        avkorting = avkorting,
    )
}

fun underkjentSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave>,
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Underkjent> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        avkorting = avkorting,
    ).let { (sak, tilAttestering) ->
        val underkjent = tilAttestering.tilUnderkjent(attestering = attesteringUnderkjent(clock))
        sak.copy(søknadsbehandlinger = sak.søknadsbehandlinger.filterNot { it.id == tilAttestering.id } + underkjent) to underkjent
    }
}

fun iverksattSøknadsbehandlingUføre(
    clock: Clock = fixedClock,
    sakInfo: SakInfo = SakInfo(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        type = Sakstype.UFØRE,
    ),
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
        sakInfo = sakInfo,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Triple<Sak, Søknadsbehandling.Iverksatt, Stønadsvedtak> {
    return iverksattSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        avkorting = avkorting,
    )
}

fun iverksattSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave>,
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    attestering: Attestering.Iverksatt = attesteringIverksatt(clock),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert,
): Triple<Sak, Søknadsbehandling.Iverksatt, Stønadsvedtak> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        avkorting = avkorting,
    ).let { (sak, tilAttestering) ->
        val (iverksatt, vedtak, utbetaling) = when (tilAttestering) {
            is Søknadsbehandling.TilAttestering.Avslag.MedBeregning -> {
                tilAttestering.tilIverksatt(attestering).let {
                    Triple(
                        it,
                        Avslagsvedtak.fromSøknadsbehandlingMedBeregning(
                            avslag = it,
                            clock = clock,
                        ),
                        null,
                    )
                }
            }
            is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning -> {
                tilAttestering.tilIverksatt(attestering).let {
                    Triple(
                        it,
                        Avslagsvedtak.fromSøknadsbehandlingUtenBeregning(
                            avslag = it,
                            clock = clock,
                        ),
                        null,
                    )
                }
            }
            is Søknadsbehandling.TilAttestering.Innvilget -> {
                val utbetaling = nyUtbetalingOversendtMedKvittering(
                    sakOgBehandling = sak to tilAttestering,
                    beregning = tilAttestering.beregning,
                    clock = clock,
                )
                tilAttestering.tilIverksatt(attestering).let {
                    Triple(
                        it,
                        VedtakSomKanRevurderes.fromSøknadsbehandling(
                            søknadsbehandling = it,
                            clock = clock,
                            utbetalingId = utbetaling.id,
                        ),
                        utbetaling,
                    )
                }
            }
        }
        Triple(
            sak.copy(
                søknadsbehandlinger = sak.søknadsbehandlinger.filterNot { it.id == iverksatt.id } + iverksatt,
                vedtakListe = sak.vedtakListe + vedtak,
                utbetalinger = utbetaling?.let { sak.utbetalinger + it } ?: sak.utbetalinger,
            ),
            iverksatt, vedtak,
        )
    }
}

fun tilAttesteringSøknadsbehandlingUføre(
    clock: Clock = fixedClock,
    sakInfo: SakInfo = SakInfo(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        type = Sakstype.UFØRE,
    ),
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
        sakInfo = sakInfo,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.TilAttestering> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        avkorting = avkorting,
    )
}

fun tilAttesteringSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave>,
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.TilAttestering> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        avkorting = avkorting,
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
                    avkorting = avkorting,
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
                                avkorting = avkorting,
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
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Simulert> {
    return simulertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        avkorting = avkorting,
    )
}

fun simulertSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave>,
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Simulert> {
    return beregnetSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        avkorting = avkorting,
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
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Beregnet> {
    return beregnetSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        avkorting = avkorting,
    )
}

fun beregnetSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave>,
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Beregnet> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        avkorting = avkorting,
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
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        avkorting = avkorting,
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
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    customVilkår.ifNotEmpty {
        require(this.groupBy { it::class }.all { it.value.count() == 1 }) { "Tillater bare et vilkår av hver type" }
    }

    val (grunnlagsdata, vilkår) = GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
        grunnlagsdata = grunnlagsdataEnsligUtenFradrag(
            periode = stønadsperiode.periode,
        ),
        vilkårsvurderinger = when (sakOgSøknad.first.type) {
            Sakstype.ALDER -> {
                vilkårsvurderingerAlderInnvilget(
                    stønadsperiode = stønadsperiode,
                )
            }
            Sakstype.UFØRE -> {
                vilkårsvurderingerSøknadsbehandlingInnvilget(
                    periode = stønadsperiode.periode,
                )
            }
        },
    )
    return nySøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        avkorting = avkorting,
    ).let { (sak, søknadsbehandling) ->
        val vilkårsvurdert = when (vilkår) {
            is Vilkårsvurderinger.Søknadsbehandling.Alder -> {
                søknadsbehandling.oppdaterBosituasjon(
                    bosituasjon = customGrunnlag.customOrDefault { grunnlagsdata.bosituasjon }.single(),
                )
                    .getOrFail()
                    .leggTilFormuevilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.formue as FormueVilkår.Vurdert },
                    ).getOrFail()
                    .leggTilLovligOpphold(
                        lovligOppholdVilkår = customVilkår.customOrDefault { vilkår.lovligOpphold as LovligOppholdVilkår.Vurdert },
                    ).getOrFail()
                    .leggTilUtenlandsopphold(
                        utenlandsopphold = customVilkår.customOrDefault { vilkår.utenlandsopphold as UtenlandsoppholdVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilOpplysningspliktVilkår(
                        opplysningspliktVilkår = customVilkår.customOrDefault { vilkår.opplysningsplikt as OpplysningspliktVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilPensjonsVilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.pensjon as PensjonsVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilFamiliegjenforeningvilkår(
                        familiegjenforening = customVilkår.customOrDefault { vilkår.familiegjenforening as FamiliegjenforeningVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilFastOppholdINorgeVilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.fastOpphold as FastOppholdINorgeVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilPersonligOppmøteVilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.personligOppmøte as PersonligOppmøteVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilInstitusjonsoppholdVilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.institusjonsopphold as InstitusjonsoppholdVilkår.Vurdert },
                    )
                    .getOrFail()
            }
            is Vilkårsvurderinger.Søknadsbehandling.Uføre -> {
                søknadsbehandling.oppdaterBosituasjon(
                    bosituasjon = customGrunnlag.customOrDefault { grunnlagsdata.bosituasjon }.single(),
                )
                    .getOrFail()
                    .leggTilFormuevilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.formue as FormueVilkår.Vurdert },
                    ).getOrFail()
                    .leggTilLovligOpphold(
                        lovligOppholdVilkår = customVilkår.customOrDefault { vilkår.lovligOpphold as LovligOppholdVilkår.Vurdert },
                    ).getOrFail()
                    .leggTilUtenlandsopphold(
                        utenlandsopphold = customVilkår.customOrDefault { vilkår.utenlandsopphold as UtenlandsoppholdVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilOpplysningspliktVilkår(
                        opplysningspliktVilkår = customVilkår.customOrDefault { vilkår.opplysningsplikt as OpplysningspliktVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilUførevilkår(
                        uførhet = customVilkår.customOrDefault { vilkår.uføre as UføreVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilFlyktningVilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.flyktning as FlyktningVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilFastOppholdINorgeVilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.fastOpphold as FastOppholdINorgeVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilPersonligOppmøteVilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.personligOppmøte as PersonligOppmøteVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilInstitusjonsoppholdVilkår(
                        vilkår = customVilkår.customOrDefault { vilkår.institusjonsopphold as InstitusjonsoppholdVilkår.Vurdert },
                    )
                    .getOrFail()
            }
        }

        val medFradrag = if (customGrunnlag.customOrDefault { grunnlagsdata.fradragsgrunnlag }.isNotEmpty()) {
            vilkårsvurdert.leggTilFradragsgrunnlag(
                fradragsgrunnlag = customGrunnlag.customOrDefault { grunnlagsdata.fradragsgrunnlag },
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
