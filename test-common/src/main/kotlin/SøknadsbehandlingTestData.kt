package no.nav.su.se.bakover.test

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatten
import arrow.core.right
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.common.extensions.mapFirst
import no.nav.su.se.bakover.common.extensions.mapSecond
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.oppdaterSøknadsbehandling
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshistorikk
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.IverksattAvslåttSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.innvilg.IverksattInnvilgetSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.iverksettSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.opprettNySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.SaksbehandlersAvgjørelse
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.oppdaterStønadsperiodeForSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
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
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.test.eksterneGrunnlag.eksternGrunnlagHentet
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknad.oppgaveIdSøknad
import no.nav.su.se.bakover.test.søknad.søknadsinnholdAlder
import no.nav.su.se.bakover.test.utbetaling.kvittering
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt0
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Skal tilsvare en ny søknadsbehandling.
 * TODO jah: Vi bør kunne gjøre dette via NySøknadsbehandling og en funksjon som tar inn saksnummer og gir oss VilkårsvurdertSøknadsbehandling.Uavklart
 */
fun søknadsbehandlingVilkårsvurdertUavklart(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    clock: Clock = fixedClock,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart> {
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

/**
 * Baserer seg på at alt er innvilget, og utfylt til og med fradrag.
 */
fun søknadsbehandlingVilkårsvurdertInnvilget(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    clock: Clock = fixedClock,
    søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikkAlleVilkårMedBosituasjonOgFradrag(
        clock = clock,
        saksbehandler = saksbehandler,
    ),
): Pair<Sak, VilkårsvurdertSøknadsbehandling.Innvilget> {
    return søknadsbehandlingVilkårsvurdertUavklart(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ).let { (sak, søknadsbehandling) ->
        søknadsbehandling.copy(
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                eksterneGrunnlag = søknadsbehandling.eksterneGrunnlag,
            ),
            søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
        ).vilkårsvurder(saksbehandler).let { vilkårsvurdert ->
            vilkårsvurdert.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>().let {
                Pair(
                    sak.oppdaterSøknadsbehandling(it),
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
    clock: Clock = fixedClock,
    søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikkAlleVilkår(
        clock = clock,
        saksbehandler = saksbehandler,
    ),
): Pair<Sak, VilkårsvurdertSøknadsbehandling.Avslag> {
    return søknadsbehandlingVilkårsvurdertUavklart(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ).let { (sak, søknadsbehandling) ->
        søknadsbehandling.copy(
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                eksterneGrunnlag = søknadsbehandling.eksterneGrunnlag,
            ),
            søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
        ).vilkårsvurder(saksbehandler).let { vilkårsvurdert ->
            vilkårsvurdert.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>().let {
                Pair(
                    sak.oppdaterSøknadsbehandling(it),
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
    søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikkAlleVilkårMedBosituasjonOgFradrag(
        clock = clock,
        saksbehandler = saksbehandler,
    ),
): Pair<Sak, BeregnetSøknadsbehandling.Innvilget> {
    return søknadsbehandlingVilkårsvurdertInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.beregn(
            begrunnelse = null,
            clock = clock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
            uteståendeAvkortingPåSak = sak.uteståendeAvkortingSkalAvkortes,
        ).getOrFail() as BeregnetSøknadsbehandling.Innvilget
        Pair(
            sak.oppdaterSøknadsbehandling(oppdatertSøknadsbehandling),
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
    clock: Clock = fixedClock,
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
    søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikkBeregnet(
        clock = clock,
        saksbehandler = saksbehandler,
    ),
): Pair<Sak, BeregnetSøknadsbehandling.Avslag> {
    return søknadsbehandlingVilkårsvurdertInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.beregn(
            begrunnelse = null,
            clock = clock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
            uteståendeAvkortingPåSak = sak.uteståendeAvkortingSkalAvkortes,
        ).getOrFail() as BeregnetSøknadsbehandling.Avslag
        Pair(
            sak.oppdaterSøknadsbehandling(oppdatertSøknadsbehandling),
            oppdatertSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingSimulert(
    clock: Clock = tikkendeFixedClock(),
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikkBeregnet(
        clock = clock,
        saksbehandler = saksbehandler,
    ),
): Pair<Sak, SimulertSøknadsbehandling> {
    return søknadsbehandlingBeregnetInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        clock = clock,
        saksbehandler = saksbehandler,
        søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
    ).let { (sak, søknadsbehandling) ->
        søknadsbehandling.simuler(
            saksbehandler = saksbehandler,
            clock = clock,
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
                ).map { simulertUtbetaling ->
                    simulertUtbetaling.simulering
                }
            }
        }.getOrFail()
            .let { simulert ->
                Pair(
                    sak.oppdaterSøknadsbehandling(simulert),
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
    søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikkSimulert(
        clock = clock,
        saksbehandler = saksbehandler,
    ),
): Pair<Sak, SøknadsbehandlingTilAttestering.Innvilget> {
    return søknadsbehandlingSimulert(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        clock = clock,
        saksbehandler = saksbehandler,
        søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            clock = clock,
        ).getOrFail()
        Pair(
            sak.oppdaterSøknadsbehandling(oppdatertSøknadsbehandling),
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
    clock: Clock = fixedClock,
    søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikkSendtTilAttesteringAvslåttBeregning(
        clock = clock,
        saksbehandler = saksbehandler,
    ),
): Pair<Sak, SøknadsbehandlingTilAttestering.Avslag.MedBeregning> {
    return søknadsbehandlingBeregnetAvslag(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        saksbehandler = saksbehandler,
        clock = clock,
        søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            clock = clock,
        ).getOrFail()
        Pair(
            sak.oppdaterSøknadsbehandling(oppdatertSøknadsbehandling),
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
    clock: Clock = fixedClock,
    søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikkSendtTilAttesteringAvslått(
        clock = clock,
        saksbehandler = saksbehandler,
    ),
): Pair<Sak, SøknadsbehandlingTilAttestering.Avslag.UtenBeregning> {
    return søknadsbehandlingVilkårsvurdertAvslag(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        saksbehandler = saksbehandler,
        søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
        clock = clock,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilAttesteringForSaksbehandler(
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            clock = clock,
        ).getOrFail()
        Pair(
            sak.oppdaterSøknadsbehandling(oppdatertSøknadsbehandling),
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
    søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikkSendtTilAttestering(
        clock = clock,
        saksbehandler = saksbehandler,
    ),
): Pair<Sak, UnderkjentSøknadsbehandling.Innvilget> {
    return søknadsbehandlingTilAttesteringInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        saksbehandler = saksbehandler,
        søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilUnderkjent(
            attestering = attestering,
        )
        Pair(
            sak.oppdaterSøknadsbehandling(oppdatertSøknadsbehandling),
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
    søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikkSendtTilAttesteringAvslått(
        clock = clock,
        saksbehandler = saksbehandler,
    ),
): Pair<Sak, UnderkjentSøknadsbehandling.Avslag.UtenBeregning> {
    return søknadsbehandlingTilAttesteringAvslagUtenBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        saksbehandler = saksbehandler,
        søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilUnderkjent(
            attestering = attestering,
        )
        Pair(
            sak.oppdaterSøknadsbehandling(oppdatertSøknadsbehandling),
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
    søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikkSendtTilAttestering(
        clock = clock,
        saksbehandler = saksbehandler,
    ),
): Pair<Sak, UnderkjentSøknadsbehandling.Avslag.MedBeregning> {
    return søknadsbehandlingTilAttesteringAvslagMedBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        saksbehandler = saksbehandler,
        søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
    ).let { (sak, søknadsbehandling) ->
        val oppdatertSøknadsbehandling = søknadsbehandling.tilUnderkjent(
            attestering = attestering,
        )
        Pair(
            sak.oppdaterSøknadsbehandling(oppdatertSøknadsbehandling),
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
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    clock: Clock = fixedClock,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    sakstype: Sakstype = Sakstype.UFØRE,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
        sakInfo = SakInfo(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            type = sakstype,
        ),
    ),
): Triple<Sak, IverksattSøknadsbehandling.Innvilget, VedtakInnvilgetSøknadsbehandling> {
    return iverksattSøknadsbehandling(
        sakOgSøknad = sakOgSøknad,
        stønadsperiode = stønadsperiode,
        customVilkår = vilkårsvurderinger.vilkår.toList(),
        customGrunnlag = grunnlagsdata.let {
            listOf(it.bosituasjon, it.fradragsgrunnlag).flatten()
        },
        eksterneGrunnlag = eksterneGrunnlag,
        saksbehandler = saksbehandler,
        clock = clock,
    ).let {
        Triple(
            it.first,
            it.second as IverksattSøknadsbehandling.Innvilget,
            it.third as VedtakInnvilgetSøknadsbehandling,
        )
    }
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
): Triple<Sak, IverksattSøknadsbehandling.Avslag.MedBeregning, Avslagsvedtak> {
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
        clock = clock,
    ).let { Triple(it.first, it.second as IverksattSøknadsbehandling.Avslag.MedBeregning, it.third as Avslagsvedtak) }
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
): Triple<Sak, IverksattSøknadsbehandling.Avslag.UtenBeregning, Avslagsvedtak> {
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
        clock = clock,
    ).let { Triple(it.first, it.second as IverksattSøknadsbehandling.Avslag.UtenBeregning, it.third as Avslagsvedtak) }
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
): Pair<Sak, VilkårsvurdertSøknadsbehandling> {
    require(sakOgSøknad.first.type == Sakstype.ALDER) { "Bruk nySøknadsbehandlingUføre dersom du ønsker deg en uføresak." }
    return nySøknadsbehandlingMedStønadsperiode(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
    ).let {
        it.first to it.second
    }
}

fun nySøknadsbehandlingUføre(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
): Pair<Sak, VilkårsvurdertSøknadsbehandling> {
    require(sakOgSøknad.first.type == Sakstype.UFØRE) { "Bruk nySøknadsbehandlingAlder dersom du ønsker deg en alderssak." }
    return nySøknadsbehandlingMedStønadsperiode(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
    ).let {
        it.first to it.second
    }
}

/**
 * Oppretter en søknadsbehandling med bagrunn i [sakOgSøknad]. Støtter både uføre og alder.
 */
fun nySøknadsbehandlingUtenStønadsperiode(
    clock: Clock = fixedClock,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakMedjournalførtSøknadOgOppgave(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart> {
    require(sakOgSøknad.first.type == sakOgSøknad.first.type) {
        "Støtter ikke å ha forskjellige typer (uføre, alder) på en og samme sak."
    }
    require(sakOgSøknad.first.fnr == sakOgSøknad.second.fnr) {
        "Sak (${sakOgSøknad.first.fnr}) og søknad (${sakOgSøknad.second.fnr}) må ha samme fnr"
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
    hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person> = { person(fnr = fnr).right() },
    saksbehandlersAvgjørelse: SaksbehandlersAvgjørelse? = null,
): Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart> {
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
            hentPerson = hentPerson,
            saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
        ).getOrFail() as Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart>
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
): Pair<Sak, VilkårsvurdertSøknadsbehandling> {
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
): Pair<Sak, UnderkjentSøknadsbehandling> {
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
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    fritekstTilBrev: String = "",
): Pair<Sak, UnderkjentSøknadsbehandling> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        fritekstTilBrev = fritekstTilBrev,
    ).let { (sak, tilAttestering) ->
        val underkjent = tilAttestering.tilUnderkjent(attestering = attesteringUnderkjent(clock))
        sak.oppdaterSøknadsbehandling(underkjent) to underkjent
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
    kvittering: Kvittering? = kvittering(clock = clock),
): Triple<Sak, IverksattSøknadsbehandling, Stønadsvedtak> {
    return iverksattSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        saksbehandler = saksbehandler,
        kvittering = kvittering,
    )
}

fun iverksattSøknadsbehandling(
    clock: Clock = TikkendeKlokke(),
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    attestering: Attestering.Iverksatt = attesteringIverksatt(clock),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    kvittering: Kvittering? = kvittering(clock = clock),
): Triple<Sak, IverksattSøknadsbehandling, Stønadsvedtak> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
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
                            .plus(
                                response.utbetaling.toOversendtUtbetaling(UtbetalingStub.generateRequest(response.utbetaling))
                                    .let {
                                        kvittering?.let { kvittering ->
                                            it.toKvittertUtbetaling(kvittering)
                                        } ?: it
                                    },
                            ),
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
): Pair<Sak, SøknadsbehandlingTilAttestering> {
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
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, SøknadsbehandlingTilAttestering> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        saksbehandler = saksbehandler,
    ).let { (sak, vilkårsvurdert) ->
        when (vilkårsvurdert) {
            // avslag for vilkår går rett til attestering
            is VilkårsvurdertSøknadsbehandling.Avslag -> {
                vilkårsvurdert.tilAttesteringForSaksbehandler(
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = fritekstTilBrev,
                    clock = clock,
                ).getOrFail().let {
                    sak.oppdaterSøknadsbehandling(it) to it
                }
            }

            is VilkårsvurdertSøknadsbehandling.Innvilget -> {
                beregnetSøknadsbehandling(
                    clock = clock,
                    stønadsperiode = stønadsperiode,
                    sakOgSøknad = sakOgSøknad,
                    customGrunnlag = customGrunnlag,
                    customVilkår = customVilkår,
                    saksbehandler = saksbehandler,
                    eksterneGrunnlag = eksterneGrunnlag,
                ).let { (sak, beregnet) ->
                    when (beregnet) {
                        // beregnet avslag går til attestering
                        is BeregnetSøknadsbehandling.Avslag -> {
                            beregnet.tilAttestering(
                                saksbehandler = saksbehandler,
                                fritekstTilBrev = fritekstTilBrev,
                                clock = clock,
                            ).getOrFail().let {
                                sak.oppdaterSøknadsbehandling(it) to it
                            }
                        }

                        is BeregnetSøknadsbehandling.Innvilget -> {
                            // simuler og send til attestering hvis innvilget
                            simulertSøknadsbehandling(
                                clock = clock,
                                stønadsperiode = stønadsperiode,
                                sakOgSøknad = sakOgSøknad.mapFirst { sak },
                                customGrunnlag = customGrunnlag,
                                customVilkår = customVilkår,
                                saksbehandler = saksbehandler,
                                eksterneGrunnlag = eksterneGrunnlag,
                                søknadsbehandling = beregnet,
                            ).let { (sak, simulert) ->
                                simulert.tilAttestering(
                                    saksbehandler = saksbehandler,
                                    fritekstTilBrev = fritekstTilBrev,
                                    clock = clock,
                                ).getOrFail().let {
                                    sak.oppdaterSøknadsbehandling(it) to it
                                }
                            }
                        }
                    }
                }
            }

            is VilkårsvurdertSøknadsbehandling.Uavklart -> {
                throw IllegalStateException("Kan ikke attestere uavklart")
            }
        }
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
): Pair<Sak, SimulertSøknadsbehandling> {
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
    clock: Clock = tikkendeFixedClock(),
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    søknadsbehandling: BeregnetSøknadsbehandling? = null,
): Pair<Sak, SimulertSøknadsbehandling> {
    return (
        søknadsbehandling?.let {
            require(sakOgSøknad.first.søknadsbehandlinger.any { it.id == søknadsbehandling.id }) {
                "Dersom man sender inn søknadsbehandling, må saken være oppdatert med søknadsbehandlingen"
            }
            sakOgSøknad.mapSecond { søknadsbehandling }
        } ?: beregnetSøknadsbehandling(
            clock = clock,
            stønadsperiode = stønadsperiode,
            sakOgSøknad = sakOgSøknad,
            customGrunnlag = customGrunnlag,
            customVilkår = customVilkår,
            eksterneGrunnlag = eksterneGrunnlag,
            saksbehandler = saksbehandler,
        )
        ).let { (sak, beregnet) ->
        beregnet.simuler(
            saksbehandler = saksbehandler,
            clock = clock,
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
                ).map { simulertUtbetaling ->
                    simulertUtbetaling.simulering
                }
            }
        }.getOrFail()
            .let { simulert ->
                sak.oppdaterSøknadsbehandling(simulert) to simulert
            }
    }
}

fun beregnetSøknadsbehandlingUføre(
    clock: Clock = tikkendeFixedClock(),
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, BeregnetSøknadsbehandling> {
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
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    søknadsbehandling: VilkårsvurdertSøknadsbehandling? = null,
): Pair<Sak, BeregnetSøknadsbehandling> {
    return (
        søknadsbehandling?.let { sakOgSøknad.mapSecond { søknadsbehandling } } ?: vilkårsvurdertSøknadsbehandling(
            clock = clock,
            stønadsperiode = stønadsperiode,
            sakOgSøknad = sakOgSøknad,
            customGrunnlag = customGrunnlag,
            customVilkår = customVilkår,
            eksterneGrunnlag = eksterneGrunnlag,
            saksbehandler = saksbehandler,
        )
        ).let { (sak, vilkårsvurdert) ->
        (vilkårsvurdert as VilkårsvurdertSøknadsbehandling.Innvilget).beregn(
            begrunnelse = null,
            clock = clock,
            satsFactory = satsFactoryTestPåDato(vilkårsvurdert.opprettet.toLocalDate(zoneIdOslo)),
            nySaksbehandler = saksbehandler,
            uteståendeAvkortingPåSak = sak.uteståendeAvkortingSkalAvkortes,
        ).getOrFail().let { beregnet ->
            sak.oppdaterSøknadsbehandling(beregnet) to beregnet
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
): Pair<Sak, VilkårsvurdertSøknadsbehandling> {
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
 * Returnerer en [VilkårsvurdertSøknadsbehandling] baset på [sakOgSøknad]. Støtter både uføre og alder.
 * Default er at det opprettes en [VilkårsvurdertSøknadsbehandling.Innvilget], men funkjsonen støtter også opprettelse
 * av alle typer [VilkårsvurdertSøknadsbehandling] - hvilken man ender opp med til slutt avhenger av utfallet av
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
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, VilkårsvurdertSøknadsbehandling> {
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
        eksterneGrunnlag = eksterneGrunnlag,
    )
    return nySøknadsbehandlingMedStønadsperiode(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        saksbehandler = saksbehandler,
    ).let { (sak, søknadsbehandling) ->
        val vilkårsvurdert = when (vilkår) {
            is Vilkårsvurderinger.Søknadsbehandling.Alder -> {
                søknadsbehandling
                    .leggTilPensjonsVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.pensjon as PensjonsVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilOpplysningspliktVilkår(
                        opplysningspliktVilkår = customVilkår.customOrDefault { vilkår.opplysningsplikt as OpplysningspliktVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilFamiliegjenforeningvilkår(
                        saksbehandler = saksbehandler,
                        familiegjenforening = customVilkår.customOrDefault { vilkår.familiegjenforening as FamiliegjenforeningVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilLovligOpphold(
                        saksbehandler = saksbehandler,
                        clock = clock,
                        lovligOppholdVilkår = customVilkår.customOrDefault { vilkår.lovligOpphold as LovligOppholdVilkår.Vurdert },
                    ).getOrFail()
                    .leggTilFastOppholdINorgeVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.fastOpphold as FastOppholdINorgeVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .leggTilInstitusjonsoppholdVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.institusjonsopphold as InstitusjonsoppholdVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .leggTilUtenlandsopphold(
                        saksbehandler = saksbehandler,
                        utenlandsopphold = customVilkår.customOrDefault { vilkår.utenlandsopphold as UtenlandsoppholdVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .oppdaterBosituasjon(
                        saksbehandler = saksbehandler,
                        bosituasjon = customGrunnlag.customOrDefault { grunnlagsdata.bosituasjonSomFullstendig() }
                            .single(),
                        hendelse = Søknadsbehandlingshendelse(
                            tidspunkt = Tidspunkt.now(clock),
                            saksbehandler = saksbehandler,
                            handling = SøknadsbehandlingsHandling.TattStillingTilEPS,
                        ),
                    )
                    .getOrFail()
                    .leggTilSkatt(
                        skatt = eksterneGrunnlag.skatt,
                    )
                    .getOrFail()
                    .let {
                        if (!customVilkår.any { it is FormueVilkår.IkkeVurdert }) {
                            it.leggTilFormuegrunnlag(
                                request = LeggTilFormuevilkårRequest(
                                    behandlingId = søknadsbehandling.id,
                                    formuegrunnlag = customVilkår.customOrDefault { vilkår.formue }.grunnlag.toFormueRequestGrunnlag(),
                                    saksbehandler = saksbehandler,
                                    tidspunkt = Tidspunkt.now(clock),
                                ),
                                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                            ).getOrFail()
                        } else {
                            it
                        }
                    }
                    .leggTilPersonligOppmøteVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.personligOppmøte as PersonligOppmøteVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
            }

            is Vilkårsvurderinger.Søknadsbehandling.Uføre -> {
                søknadsbehandling.leggTilUførevilkår(
                    saksbehandler = saksbehandler,
                    uførhet = customVilkår.customOrDefault { vilkår.uføre as UføreVilkår.Vurdert },
                    clock = clock,
                ).getOrFail()
                    .leggTilOpplysningspliktVilkår(
                        opplysningspliktVilkår = customVilkår.customOrDefault { vilkår.opplysningsplikt as OpplysningspliktVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilFlyktningVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.flyktning as FlyktningVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .leggTilLovligOpphold(
                        saksbehandler = saksbehandler,
                        clock = clock,
                        lovligOppholdVilkår = customVilkår.customOrDefault { vilkår.lovligOpphold as LovligOppholdVilkår.Vurdert },
                    ).getOrFail()
                    .leggTilFastOppholdINorgeVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.fastOpphold as FastOppholdINorgeVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .leggTilInstitusjonsoppholdVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.institusjonsopphold as InstitusjonsoppholdVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .leggTilUtenlandsopphold(
                        saksbehandler = saksbehandler,
                        utenlandsopphold = customVilkår.customOrDefault { vilkår.utenlandsopphold as UtenlandsoppholdVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .oppdaterBosituasjon(
                        saksbehandler = saksbehandler,
                        bosituasjon = customGrunnlag.customOrDefault { grunnlagsdata.bosituasjonSomFullstendig() }
                            .single(),
                        hendelse = Søknadsbehandlingshendelse(
                            tidspunkt = Tidspunkt.now(clock),
                            saksbehandler = saksbehandler,
                            handling = SøknadsbehandlingsHandling.TattStillingTilEPS,
                        ),
                    )
                    .getOrFail()
                    .leggTilSkatt(
                        skatt = eksterneGrunnlag.skatt,
                    )
                    .getOrFail()
                    .let {
                        if (!customVilkår.any { it is FormueVilkår.IkkeVurdert }) {
                            it.leggTilFormuegrunnlag(
                                request = LeggTilFormuevilkårRequest(
                                    behandlingId = søknadsbehandling.id,
                                    formuegrunnlag = customVilkår.customOrDefault { vilkår.formue }.grunnlag.toFormueRequestGrunnlag(),
                                    saksbehandler = saksbehandler,
                                    tidspunkt = Tidspunkt.now(clock),
                                ),
                                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                            ).getOrFail()
                        } else {
                            it
                        }
                    }
                    .leggTilPersonligOppmøteVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { vilkår.personligOppmøte as PersonligOppmøteVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
            }
        }

        val medFradrag = if (customGrunnlag.customOrDefault { grunnlagsdata.fradragsgrunnlag }.isNotEmpty()) {
            vilkårsvurdert.oppdaterFradragsgrunnlagForSaksbehandler(
                saksbehandler = saksbehandler,
                fradragsgrunnlag = customGrunnlag.customOrDefault { grunnlagsdata.fradragsgrunnlag },
                clock = clock,
            )
                .getOrFail()
        } else {
            vilkårsvurdert
        }

        sak.oppdaterSøknadsbehandling(medFradrag) to medFradrag
    }
}

private inline fun <reified T : Vilkår> List<Vilkår>.customOrDefault(default: () -> T): T {
    return filterIsInstance<T>().singleOrNull() ?: default()
}

private inline fun <reified T : Grunnlag> List<Grunnlag>.customOrDefault(default: () -> List<T>): List<T> {
    return filterIsInstance<T>().ifEmpty { default() }
}

fun List<Formuegrunnlag>.toFormueRequestGrunnlag(): NonEmptyList<LeggTilFormuevilkårRequest.Grunnlag> {
    return this.map {
        LeggTilFormuevilkårRequest.Grunnlag.Søknadsbehandling(
            periode = it.periode,
            epsFormue = it.epsFormue,
            søkersFormue = it.søkersFormue,
            begrunnelse = "",
            måInnhenteMerInformasjon = false,
        )
    }.toNonEmptyList()
}
