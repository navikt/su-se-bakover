package no.nav.su.se.bakover.test

import arrow.core.flatten
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.IverksattAvslåttSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.innvilg.IverksattInnvilgetSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.iverksettSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
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
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknad.oppgaveIdSøknad
import no.nav.su.se.bakover.test.søknad.søknadsinnholdAlder
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
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    clock: Clock = fixedClock,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Uavklart> {
    val sakOgSøknad = nySakMedjournalførtSøknadOgOppgave(
        sakId = sakId,
        saksnummer = saksnummer,
        oppgaveId = oppgaveIdSøknad,
        fnr = fnr,
    )
    return nySøknadsbehandlingMedStønadsperiode(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        saksbehandler = saksbehandler,
    )
}

fun søknadsbehandlingVilkårsvurdertInnvilget(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Innvilget> {
    return søknadsbehandlingVilkårsvurdertUavklart(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ).let { (sak, søknadsbehandling) ->
        søknadsbehandling.copy(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        ).vilkårsvurder(saksbehandler).let { vilkårsvurdert ->
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Avslag> {
    return søknadsbehandlingVilkårsvurdertUavklart(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ).let { (sak, søknadsbehandling) ->
        søknadsbehandling.copy(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        ).vilkårsvurder(saksbehandler).let { vilkårsvurdert ->
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
    clock: Clock = fixedClock,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Beregnet.Innvilget> {
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
            nySaksbehandler = saksbehandler,
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
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
            nySaksbehandler = saksbehandler,
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
    clock: Clock = tikkendeFixedClock,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Simulert> {
    return søknadsbehandlingBeregnetInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        clock = clock,
        saksbehandler = saksbehandler,
    ).let { (sak, søknadsbehandling) ->
        søknadsbehandling.simuler(
            saksbehandler = saksbehandler,
        ) { beregning, uføregrunnlag ->
            sak.lagNyUtbetaling(
                saksbehandler = saksbehandler,
                beregning = beregning,
                clock = clock,
                utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                uføregrunnlag = uføregrunnlag,
            ).let {
                sak.simulerUtbetaling(
                    utbetalingForSimulering = it,
                    periode = søknadsbehandling.periode,
                    simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode ->
                        simulerUtbetaling(
                            sak = sak,
                            utbetaling = utbetalingForSimulering,
                            simuleringsperiode = periode,
                        )
                    },
                    kontrollerMotTidligereSimulering = null,
                    clock = clock,
                ).map { simulertUtbetaling ->
                    simulertUtbetaling.simulering
                }
            }
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
    clock: Clock = fixedClock,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.TilAttestering.Innvilget> {
    return søknadsbehandlingSimulert(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        clock = clock,
        saksbehandler = saksbehandler,
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.TilAttestering.Avslag.MedBeregning> {
    return søknadsbehandlingBeregnetAvslag(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        saksbehandler = saksbehandler,
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.TilAttestering.Avslag.UtenBeregning> {
    return søknadsbehandlingVilkårsvurdertAvslag(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        saksbehandler = saksbehandler,
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Underkjent.Innvilget> {
    return søknadsbehandlingTilAttesteringInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        saksbehandler = saksbehandler,
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Underkjent.Avslag.UtenBeregning> {
    return søknadsbehandlingTilAttesteringAvslagUtenBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        saksbehandler = saksbehandler,
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Underkjent.Avslag.MedBeregning> {
    return søknadsbehandlingTilAttesteringAvslagMedBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        saksbehandler = saksbehandler,
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    sakstype: Sakstype = Sakstype.UFØRE,
): Pair<Sak, Søknadsbehandling.Iverksatt.Innvilget> {
    return iverksattSøknadsbehandling(
        sakOgSøknad = nySakUføre(
            clock = clock,
            sakInfo = SakInfo(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                type = sakstype,
            ),
        ),
        stønadsperiode = stønadsperiode,
        customVilkår = vilkårsvurderinger.vilkår.toList(),
        customGrunnlag = grunnlagsdata.let {
            listOf(it.bosituasjon, it.fradragsgrunnlag).flatten()
        },
        saksbehandler = saksbehandler,
    ).let { Pair(it.first, it.second as Søknadsbehandling.Iverksatt.Innvilget) }
}

fun søknadsbehandlingIverksattAvslagMedBeregning(
    sakId: UUID = UUID.randomUUID(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakstype: Sakstype = Sakstype.UFØRE,
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Iverksatt.Avslag.MedBeregning> {
    return iverksattSøknadsbehandling(
        sakOgSøknad = nySakUføre(
            clock = clock,
            sakInfo = SakInfo(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                type = sakstype,
            ),
        ),
        stønadsperiode = stønadsperiode,
        customVilkår = vilkårsvurderinger.vilkår.toList(),
        customGrunnlag = grunnlagsdata.let {
            listOf(it.bosituasjon, it.fradragsgrunnlag).flatten()
        },
        saksbehandler = saksbehandler,
    ).let { Pair(it.first, it.second as Søknadsbehandling.Iverksatt.Avslag.MedBeregning) }
}

fun søknadsbehandlingIverksattAvslagUtenBeregning(
    sakId: UUID = UUID.randomUUID(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakstype: Sakstype = Sakstype.UFØRE,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
    clock: Clock = fixedClock,
): Pair<Sak, Søknadsbehandling.Iverksatt.Avslag.UtenBeregning> {
    return iverksattSøknadsbehandling(
        sakOgSøknad = nySakUføre(
            clock = clock,
            sakInfo = SakInfo(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                type = sakstype,
            ),
        ),
        stønadsperiode = stønadsperiode,
        customVilkår = vilkårsvurderinger.vilkår.toList(),
        customGrunnlag = grunnlagsdata.let {
            listOf(it.bosituasjon, it.fradragsgrunnlag).flatten()
        },
    ).let { Pair(it.first, it.second as Søknadsbehandling.Iverksatt.Avslag.UtenBeregning) }
}

/**
 * En lukket uavklart vilkårsvurdert søknadsbehandling
 */
fun søknadsbehandlingTrukket(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    saksbehandlerSomLukket: NavIdentBruker.Saksbehandler = saksbehandler,
    clock: Clock = fixedClock,
): Pair<Sak, LukketSøknadsbehandling> {
    return søknadsbehandlingVilkårsvurdertUavklart(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ).let { (sak, søknadsbehandling) ->
        sak.lukkSøknadOgSøknadsbehandling(
            lukkSøknadCommand = trekkSøknad(
                søknadId = søknadsbehandling.søknad.id,
                saksbehandler = saksbehandlerSomLukket,
                lukketTidspunkt = fixedTidspunkt.plus(1, ChronoUnit.SECONDS),
            ),
            hentPerson = { person().right() },
            clock = clock,
            hentSaksbehandlerNavn = { "Saksbehandlers Navn".right() },
            saksbehandler = saksbehandler,
        )
    }.let {
        Pair(it.sak, it.søknadsbehandling!!)
    }
}

@Suppress("unused")
fun nySøknadsbehandlingAlder(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakAlder(søknadsInnhold = søknadsinnholdAlder()),
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    require(sakOgSøknad.first.type == Sakstype.ALDER) { "Bruk nySøknadsbehandlingUføre dersom du ønsker deg en uføresak." }
    return nySøknadsbehandlingMedStønadsperiode(
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
    return nySøknadsbehandlingMedStønadsperiode(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
    )
}

/**
 * Oppretter en søknadsbehandling med bagrunn i [sakOgSøknad]. Støtter både uføre og alder.
 */
fun nySøknadsbehandlingUtenStønadsperiode(
    clock: Clock = fixedClock,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakMedjournalførtSøknadOgOppgave(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Uavklart> {
    require(sakOgSøknad.first.type == sakOgSøknad.first.type) {
        "Støtter ikke å ha forskjellige typer (uføre, alder) på en og samme sak."
    }
    val (sak, søknad) = sakOgSøknad
    return sak.copy(
        søknader = sak.søknader.filterNot { it.id == søknad.id } + søknad, // replace hvis søknaden allerede er lagt til (f.eks hvis man først oppretter bare sak + søknad)
    ).opprettNySøknadsbehandling(
        søknadId = søknad.id,
        clock = clock,
        saksbehandler = saksbehandler,
    ).getOrFail().let { (sak, _, behandling) ->
        Pair(sak, behandling)
    }
}

/**
 * Oppretter en søknadsbehandling med bagrunn i [sakOgSøknad]. Støtter både uføre og alder.
 */
fun nySøknadsbehandlingMedStønadsperiode(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Uavklart> {
    return nySøknadsbehandlingUtenStønadsperiode(
        clock = clock,
        sakOgSøknad = sakOgSøknad,
        saksbehandler = saksbehandler,
    ).let { (sak, søknadsbehandlingUtenPeriode) ->
        @Suppress("UNCHECKED_CAST")
        sak.oppdaterStønadsperiodeForSøknadsbehandling(
            søknadsbehandlingId = søknadsbehandlingUtenPeriode.id,
            stønadsperiode = stønadsperiode,
            clock = clock,
            formuegrenserFactory = formuegrenserFactoryTestPåDato(LocalDate.now(clock)),
            saksbehandler = saksbehandler,
        ).getOrFail() as Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Uavklart>
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
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
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
    fritekstTilBrev: String = "",
): Pair<Sak, Søknadsbehandling.Underkjent> {
    return underkjentSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        fritekstTilBrev = fritekstTilBrev,
    )
}

fun underkjentSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    fritekstTilBrev: String = "",
): Pair<Sak, Søknadsbehandling.Underkjent> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        fritekstTilBrev = fritekstTilBrev,
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Triple<Sak, Søknadsbehandling.Iverksatt, Stønadsvedtak> {
    return iverksattSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        saksbehandler = saksbehandler,
    )
}

fun iverksattSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    attestering: Attestering.Iverksatt = attesteringIverksatt(clock),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Triple<Sak, Søknadsbehandling.Iverksatt, Stønadsvedtak> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
    ).let { (sak, tilAttestering) ->
        sak.iverksettSøknadsbehandling(
            command = IverksettSøknadsbehandlingCommand(
                behandlingId = tilAttestering.id,
                attestering = attestering,
            ),
            lagDokument = {
                Dokument.UtenMetadata.Vedtak(
                    opprettet = Tidspunkt.now(clock),
                    tittel = "TODO: BrevRequesten bør lages i domenet",
                    generertDokument = "".toByteArray(),
                    generertDokumentJson = "{}",
                ).right()
            },
            simulerUtbetaling = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode ->
                simulerUtbetaling(
                    sak = sak,
                    utbetaling = utbetalingForSimulering,
                    simuleringsperiode = periode,
                    clock = clock,
                ).getOrFail().right()
            },
            clock = clock,
        ).getOrFail().let { response ->
            /**
             * TODO
             * se om vi får til noe som oppfører seg som [no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksattSøknadsbehandlingResponse.ferdigstillIverksettelseITransaksjon]?
             */
            Triple(
                when (response) {
                    is IverksattAvslåttSøknadsbehandlingResponse -> response.sak
                    is IverksattInnvilgetSøknadsbehandlingResponse -> response.sak.copy(
                        utbetalinger = response.sak.utbetalinger.filterNot { it.id == response.utbetaling.id }
                            .plus(response.utbetaling.toOversendtUtbetaling(UtbetalingStub.generateRequest(response.utbetaling))),
                    )

                    else -> TODO("Ingen andre nåværende implementasjoner")
                },
                response.søknadsbehandling,
                response.vedtak,
            )
        }
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    fritekstTilBrev: String = "",
): Pair<Sak, Søknadsbehandling.TilAttestering> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        saksbehandler = saksbehandler,
        fritekstTilBrev = fritekstTilBrev,
    )
}

fun tilAttesteringSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.TilAttestering> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        saksbehandler = saksbehandler,
    ).let { (sak, vilkårsvurdert) ->
        val tilAttestering = when (vilkårsvurdert) {
            // avslag for vilkår går rett til attestering
            is Søknadsbehandling.Vilkårsvurdert.Avslag -> {
                vilkårsvurdert.tilAttestering(
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = fritekstTilBrev,
                )
            }

            is Søknadsbehandling.Vilkårsvurdert.Innvilget -> {
                beregnetSøknadsbehandling(
                    clock = clock,
                    stønadsperiode = stønadsperiode,
                    sakOgSøknad = sakOgSøknad,
                    customGrunnlag = customGrunnlag,
                    customVilkår = customVilkår,
                    saksbehandler = saksbehandler,
                ).let { (_, beregnet) ->
                    when (beregnet) {
                        // beregnet avslag går til attestering
                        is Søknadsbehandling.Beregnet.Avslag -> {
                            beregnet.tilAttestering(
                                saksbehandler = saksbehandler,
                                fritekstTilBrev = fritekstTilBrev,
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
                                saksbehandler = saksbehandler,
                            ).let { (_, simulert) ->
                                simulert.tilAttestering(
                                    saksbehandler = saksbehandler,
                                    fritekstTilBrev = fritekstTilBrev,
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
        sak.copy(
            søknadsbehandlinger = sak.søknadsbehandlinger.filterNot { it.id == vilkårsvurdert.id } + tilAttestering,
        ) to tilAttestering
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Simulert> {
    return simulertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        saksbehandler = saksbehandler,
    )
}

fun simulertSøknadsbehandling(
    clock: Clock = tikkendeFixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Simulert> {
    return beregnetSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        saksbehandler = saksbehandler,
    ).let { (sak, beregnet) ->
        beregnet.simuler(
            saksbehandler = saksbehandler,
        ) { beregning, uføregrunnlag ->
            sak.lagNyUtbetaling(
                saksbehandler = saksbehandler,
                beregning = beregning,
                clock = clock,
                utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                uføregrunnlag = uføregrunnlag,
            ).let {
                sak.simulerUtbetaling(
                    utbetalingForSimulering = it,
                    periode = beregnet.periode,
                    simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode ->
                        simulerUtbetaling(
                            sak = sak,
                            utbetaling = utbetalingForSimulering,
                            simuleringsperiode = periode,
                            clock = clock,
                            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                        )
                    },
                    kontrollerMotTidligereSimulering = null,
                    clock = clock,
                ).map { simulertUtbetaling ->
                    simulertUtbetaling.simulering
                }
            }
        }.getOrFail()
            .let { simulert ->
                sak.copy(søknadsbehandlinger = sak.søknadsbehandlinger.filterNot { it.id == beregnet.id } + simulert) to simulert
            }
    }
}

fun beregnetSøknadsbehandlingUføre(
    clock: Clock = tikkendeFixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Beregnet> {
    return beregnetSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        saksbehandler = saksbehandler,
    )
}

fun beregnetSøknadsbehandling(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Beregnet> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        saksbehandler = saksbehandler,
    ).let { (sak, vilkårsvurdert) ->
        vilkårsvurdert.beregn(
            begrunnelse = null,
            clock = clock,
            satsFactory = satsFactoryTestPåDato(vilkårsvurdert.opprettet.toLocalDate(zoneIdOslo)),
            nySaksbehandler = saksbehandler,
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        saksbehandler = saksbehandler,
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
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
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
    return nySøknadsbehandlingMedStønadsperiode(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        saksbehandler = saksbehandler,
    ).let { (sak, søknadsbehandling) ->
        val vilkårsvurdert = when (vilkår) {
            is Vilkårsvurderinger.Søknadsbehandling.Alder -> {
                søknadsbehandling.oppdaterBosituasjon(
                    saksbehandler = saksbehandler,
                    bosituasjon = customGrunnlag.customOrDefault { grunnlagsdata.bosituasjon }.single(),
                )
                    .getOrFail()
                    .leggTilFormuevilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.formue as FormueVilkår.Vurdert },
                    ).getOrFail()
                    .leggTilLovligOpphold(
                        saksbehandler = saksbehandler,
                        lovligOppholdVilkår = customVilkår.customOrDefault { vilkår.lovligOpphold as LovligOppholdVilkår.Vurdert },
                    ).getOrFail()
                    .leggTilUtenlandsopphold(
                        saksbehandler = saksbehandler,
                        utenlandsopphold = customVilkår.customOrDefault { vilkår.utenlandsopphold as UtenlandsoppholdVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilOpplysningspliktVilkår(
                        saksbehandler = saksbehandler,
                        opplysningspliktVilkår = customVilkår.customOrDefault { vilkår.opplysningsplikt as OpplysningspliktVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilPensjonsVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.pensjon as PensjonsVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilFamiliegjenforeningvilkår(
                        saksbehandler = saksbehandler,
                        familiegjenforening = customVilkår.customOrDefault { vilkår.familiegjenforening as FamiliegjenforeningVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilFastOppholdINorgeVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.fastOpphold as FastOppholdINorgeVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilPersonligOppmøteVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.personligOppmøte as PersonligOppmøteVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilInstitusjonsoppholdVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.institusjonsopphold as InstitusjonsoppholdVilkår.Vurdert },
                    )
                    .getOrFail()
            }

            is Vilkårsvurderinger.Søknadsbehandling.Uføre -> {
                søknadsbehandling.oppdaterBosituasjon(
                    saksbehandler = saksbehandler,
                    bosituasjon = customGrunnlag.customOrDefault { grunnlagsdata.bosituasjon }.single(),
                )
                    .getOrFail()
                    .leggTilFormuevilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.formue as FormueVilkår.Vurdert },
                    ).getOrFail()
                    .leggTilLovligOpphold(
                        saksbehandler = saksbehandler,
                        lovligOppholdVilkår = customVilkår.customOrDefault { vilkår.lovligOpphold as LovligOppholdVilkår.Vurdert },
                    ).getOrFail()
                    .leggTilUtenlandsopphold(
                        saksbehandler = saksbehandler,
                        utenlandsopphold = customVilkår.customOrDefault { vilkår.utenlandsopphold as UtenlandsoppholdVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilOpplysningspliktVilkår(
                        saksbehandler = saksbehandler,
                        opplysningspliktVilkår = customVilkår.customOrDefault { vilkår.opplysningsplikt as OpplysningspliktVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilUførevilkår(
                        saksbehandler = saksbehandler,
                        uførhet = customVilkår.customOrDefault { vilkår.uføre as UføreVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilFlyktningVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.flyktning as FlyktningVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilFastOppholdINorgeVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.fastOpphold as FastOppholdINorgeVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilPersonligOppmøteVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.personligOppmøte as PersonligOppmøteVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilInstitusjonsoppholdVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.institusjonsopphold as InstitusjonsoppholdVilkår.Vurdert },
                    )
                    .getOrFail()
            }
        }

        val medFradrag = if (customGrunnlag.customOrDefault { grunnlagsdata.fradragsgrunnlag }.isNotEmpty()) {
            vilkårsvurdert.leggTilFradragsgrunnlag(
                saksbehandler = saksbehandler,
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
