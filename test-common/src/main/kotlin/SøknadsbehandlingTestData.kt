package no.nav.su.se.bakover.test

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.right
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.VilkårsvurderingerSøknadsbehandling
import dokument.domain.Dokument
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.mapFirst
import no.nav.su.se.bakover.common.extensions.mapSecond
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.simulering.simulerUtbetaling
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.oppdaterSøknadsbehandling
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.IverksattAvslåttSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.innvilg.IverksattInnvilgetSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.iverksettSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.opprettNySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.SaksbehandlersAvgjørelse
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.oppdaterStønadsperiodeForSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakIverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.test.eksterneGrunnlag.eksternGrunnlagHentet
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.søknad.journalpostIdSøknad
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknad.oppgaveIdSøknad
import no.nav.su.se.bakover.test.søknad.personopplysninger
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.utbetaling.kvittering
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt0
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import vilkår.common.domain.IkkeVurdertVilkår
import vilkår.common.domain.Vilkår
import vilkår.common.domain.grunnlag.Grunnlag
import vilkår.familiegjenforening.domain.FamiliegjenforeningVilkår
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.formue.domain.FormueVilkår
import vilkår.formue.domain.Formuegrunnlag
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.uføre.domain.UføreVilkår
import vilkår.vurderinger.domain.EksterneGrunnlag
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Baserer seg på at alt er innvilget, og utfylt til og med fradrag.
 */
fun søknadsbehandlingVilkårsvurdertInnvilget(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, VilkårsvurdertSøknadsbehandling.Innvilget> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        saksbehandler = saksbehandler,
    ).mapSecond { it as VilkårsvurdertSøknadsbehandling.Innvilget }
}

/**
 * @param sakOgSøknad sak og søknad det skal opprettes søknadsbehandling for
 * @param customGrunnlag brukes for å spesifisere grunnlag som skal overstyre default
 * @param customVilkår brukes for å overstyre vilkår som skal overstyre default
 */
fun søknadsbehandlingVilkårsvurdertAvslag(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = listOf(institusjonsoppholdvilkårAvslag()),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, VilkårsvurdertSøknadsbehandling.Avslag> {
    return vilkårsvurdertSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        saksbehandler = saksbehandler,
    ).mapSecond { it as VilkårsvurdertSøknadsbehandling.Avslag }
}

@Suppress("unused")
fun søknadsbehandlingBeregnetInnvilget(
    clock: Clock = TikkendeKlokke(),
    beregnetClock: Clock = clock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    søknadsbehandling: VilkårsvurdertSøknadsbehandling? = null,
): Pair<Sak, BeregnetSøknadsbehandling.Innvilget> {
    return beregnetSøknadsbehandling(
        clock = clock,
        beregnetClock = beregnetClock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        saksbehandler = saksbehandler,
        søknadsbehandling = søknadsbehandling,
    ).mapSecond { it as BeregnetSøknadsbehandling.Innvilget }
}

/**
 * Defaultverdier:
 * - Forventet inntekt: 1_000_000
 *
 *  [customVilkår] og/eller [customGrunnlag] må gi beregningsavslag, hvis ikke får man en runtime exception
 */
fun søknadsbehandlingBeregnetAvslag(
    clock: Clock = TikkendeKlokke(),
    beregnetClock: Clock = clock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = listOf(
        innvilgetUførevilkårForventetInntekt0(
            id = UUID.randomUUID(),
            periode = stønadsperiode.periode,
            uføregrunnlag = uføregrunnlagForventetInntekt(
                periode = stønadsperiode.periode,
                forventetInntekt = 1_000_000,
            ),
        ),
    ),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    søknadsbehandling: VilkårsvurdertSøknadsbehandling? = null,
): Pair<Sak, BeregnetSøknadsbehandling.Avslag> {
    return beregnetSøknadsbehandling(
        clock = clock,
        beregnetClock = beregnetClock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        saksbehandler = saksbehandler,
        søknadsbehandling = søknadsbehandling,
    ).mapSecond { it as BeregnetSøknadsbehandling.Avslag }
}

fun søknadsbehandlingTilAttesteringInnvilget(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
        sakInfo = SakInfo(
            sakId = UUID.randomUUID(),
            saksnummer = saksnummer,
            fnr = Fnr.generer(),
            type = Sakstype.UFØRE,
        ),
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, SøknadsbehandlingTilAttestering.Innvilget> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
    ).mapSecond { it as SøknadsbehandlingTilAttestering.Innvilget }
}

fun søknadsbehandlingTilAttesteringAvslagMedBeregning(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = listOf(
        fradragsgrunnlagArbeidsinntekt1000(),
    ),
    customVilkår: List<Vilkår> = listOf(
        innvilgetUførevilkårForventetInntekt0(
            periode = stønadsperiode.periode,
            uføregrunnlag = uføregrunnlagForventetInntekt(
                periode = stønadsperiode.periode,
                forventetInntekt = 1_000_000,
            ),
        ),
    ),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, SøknadsbehandlingTilAttestering.Avslag.MedBeregning> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
    ).mapSecond { it as SøknadsbehandlingTilAttestering.Avslag.MedBeregning }
}

fun søknadsbehandlingTilAttesteringAvslagUtenBeregning(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = listOf(institusjonsoppholdvilkårAvslag()),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, SøknadsbehandlingTilAttestering.Avslag.UtenBeregning> {
    return tilAttesteringSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
    ).mapSecond { it as SøknadsbehandlingTilAttestering.Avslag.UtenBeregning }
}

fun søknadsbehandlingUnderkjentInnvilget(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
        sakInfo = SakInfo(
            sakId = UUID.randomUUID(),
            saksnummer = saksnummer,
            fnr = Fnr.generer(),
            type = Sakstype.UFØRE,
        ),
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, UnderkjentSøknadsbehandling.Innvilget> {
    return underkjentSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
    ).mapSecond { it as UnderkjentSøknadsbehandling.Innvilget }
}

fun søknadsbehandlingUnderkjentAvslagUtenBeregning(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = listOf(institusjonsoppholdvilkårAvslag()),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, UnderkjentSøknadsbehandling.Avslag.UtenBeregning> {
    return underkjentSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
    ).mapSecond { it as UnderkjentSøknadsbehandling.Avslag.UtenBeregning }
}

fun søknadsbehandlingUnderkjentAvslagMedBeregning(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(clock = clock),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = listOf(
        innvilgetUførevilkårForventetInntekt0(
            id = UUID.randomUUID(),
            periode = stønadsperiode.periode,
            uføregrunnlag = uføregrunnlagForventetInntekt(
                periode = stønadsperiode.periode,
                forventetInntekt = 1_000_000,
            ),
        ),
    ),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, UnderkjentSøknadsbehandling.Avslag.MedBeregning> {
    return underkjentSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
    ).mapSecond { it as UnderkjentSøknadsbehandling.Avslag.MedBeregning }
}

/**
 * Merk at denne gir deg et vedtak også.
 */
fun søknadsbehandlingIverksattInnvilget(
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
): Triple<Sak, IverksattSøknadsbehandling.Innvilget, VedtakInnvilgetSøknadsbehandling> {
    return iverksattSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        attestering = attestering,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
        kvittering = kvittering,
    ).let {
        Triple(
            it.first,
            it.second as IverksattSøknadsbehandling.Innvilget,
            it.third as VedtakInnvilgetSøknadsbehandling,
        )
    }
}

fun søknadsbehandlingIverksattAvslagMedBeregning(
    clock: Clock = TikkendeKlokke(),
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = listOf(
        innvilgetUførevilkårForventetInntekt0(
            id = UUID.randomUUID(),
            periode = stønadsperiode.periode,
            uføregrunnlag = uføregrunnlagForventetInntekt(
                periode = stønadsperiode.periode,
                forventetInntekt = 1_000_000,
            ),
        ),
    ),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    attestering: Attestering.Iverksatt = attesteringIverksatt(clock),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    kvittering: Kvittering? = kvittering(clock = clock),
): Triple<Sak, IverksattSøknadsbehandling.Avslag.MedBeregning, Avslagsvedtak> {
    return iverksattSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        attestering = attestering,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
        kvittering = kvittering,
    ).let {
        Triple(
            it.first,
            it.second as IverksattSøknadsbehandling.Avslag.MedBeregning,
            it.third as Avslagsvedtak,
        )
    }
}

fun søknadsbehandlingIverksattAvslagUtenBeregning(
    clock: Clock = TikkendeKlokke(),
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = listOf(institusjonsoppholdvilkårAvslag()),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    attestering: Attestering.Iverksatt = attesteringIverksatt(clock),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    kvittering: Kvittering? = kvittering(clock = clock),
): Triple<Sak, IverksattSøknadsbehandling.Avslag.UtenBeregning, Avslagsvedtak> {
    return iverksattSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        attestering = attestering,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
        kvittering = kvittering,
    ).let {
        Triple(
            it.first,
            it.second as IverksattSøknadsbehandling.Avslag.UtenBeregning,
            it.third as Avslagsvedtak,
        )
    }
}

/**
 * En lukket uavklart vilkårsvurdert søknadsbehandling
 */
fun søknadsbehandlingTrukket(
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    saksbehandlerSomLukket: NavIdentBruker.Saksbehandler = saksbehandler,
    clock: Clock = fixedClock,
): Pair<Sak, LukketSøknadsbehandling> {
    return nySøknadsbehandlingMedStønadsperiode(
        clock = clock,
        stønadsperiode = stønadsperiode,
    ).let { (sak, søknadsbehandling) ->
        sak.lukkSøknadOgSøknadsbehandling(
            lukkSøknadCommand = trekkSøknad(
                søknadId = søknadsbehandling.søknad.id,
                saksbehandler = saksbehandlerSomLukket,
                lukketTidspunkt = fixedTidspunkt.plus(1, ChronoUnit.SECONDS),
            ),
            saksbehandler = saksbehandler,
        )
    }.let {
        Pair(it.sak, it.søknadsbehandling!!)
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
    id: SøknadsbehandlingId = SøknadsbehandlingId.generer(),
    clock: Clock = fixedClock,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakId: UUID = UUID.randomUUID(),
    søknadId: UUID = UUID.randomUUID(),
    fnr: Fnr = Fnr.generer(),
    journalpostId: JournalpostId = journalpostIdSøknad,
    oppgaveId: OppgaveId = oppgaveIdSøknad,
    søknadInnhold: SøknadInnhold = søknadinnholdUføre(personopplysninger = personopplysninger(fnr)),
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakMedjournalførtSøknadOgOppgave(
        sakId = sakId,
        saksnummer = saksnummer,
        søknadId = søknadId,
        journalpostId = journalpostId,
        oppgaveId = oppgaveId,
        fnr = fnr,
        clock = clock,
        søknadInnhold = søknadInnhold,
    ),
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
        // replace hvis søknaden allerede er lagt til (f.eks hvis man først oppretter bare sak + søknad)
        søknader = sak.søknader.filterNot { it.id == søknad.id } + søknad,
    ).opprettNySøknadsbehandling(
        søknadsbehandlingId = id,
        søknadId = søknad.id,
        clock = clock,
        saksbehandler = saksbehandler,
        oppdaterOppgave = null,
    ).getOrFail().let { (sak, behandling) ->
        Pair(sak, behandling)
    }
}

/**
 * Oppretter en søknadsbehandling med bagrunn i [sakOgSøknad]. Støtter både uføre og alder.
 */
fun nySøknadsbehandlingMedStønadsperiode(
    clock: Clock = fixedClock,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakId: UUID = UUID.randomUUID(),
    fnr: Fnr = Fnr.generer(),
    sakstype: Sakstype = Sakstype.UFØRE,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = when (sakstype) {
        Sakstype.ALDER -> nySakAlder(
            clock = clock,
            sakInfo = SakInfo(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                type = Sakstype.ALDER,
            ),
        )

        Sakstype.UFØRE -> nySakUføre(
            clock = clock,
            sakInfo = SakInfo(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                type = Sakstype.UFØRE,
            ),
        )
    },
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
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Pair<Sak, UnderkjentSøknadsbehandling> {
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
        val underkjent = tilAttestering.tilUnderkjent(attestering = attesteringUnderkjent(clock)).getOrFail()
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

/**
 * Lager vedtak, men journalfører/distriburer ikke brev.
 */
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
): Triple<Sak, IverksattSøknadsbehandling, VedtakIverksattSøknadsbehandling> {
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
            genererPdf = {
                Dokument.UtenMetadata.Vedtak(
                    opprettet = Tidspunkt.now(clock),
                    tittel = "TODO: BrevRequesten bør lages i domenet",
                    generertDokument = pdfATom(),
                    generertDokumentJson = "{}",
                ).right()
            },
            simulerUtbetaling = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering ->
                simulerUtbetaling(
                    utbetalingerPåSak = sak.utbetalinger,
                    utbetalingForSimulering = utbetalingForSimulering,
                    clock = clock,
                ).getOrFail().right()
            },
            clock = clock,
            satsFactory = satsFactoryTestPåDato(),
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
                vilkårsvurdert.tilAttestering(
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
    utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    søknadsbehandling: BeregnetSøknadsbehandling.Innvilget? = null,
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
        ).mapSecond { it as BeregnetSøknadsbehandling.Innvilget }
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
                simulerUtbetaling(
                    tidligereUtbetalinger = sak.utbetalinger,
                    utbetalingForSimulering = it,
                    simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering ->
                        simulerUtbetaling(
                            utbetalingerPåSak = sak.utbetalinger,
                            utbetalingForSimulering = utbetalingForSimulering,
                            clock = clock,
                            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                        )
                    },
                ).map { simuleringsresultat ->
                    simuleringsresultat.simulertUtbetaling.simulering
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
    clock: Clock = TikkendeKlokke(),
    beregnetClock: Clock = clock,
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
            clock = beregnetClock,
            satsFactory = satsFactoryTest.gjeldende(Tidspunkt.now(beregnetClock)),
            nySaksbehandler = saksbehandler,

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
        require(this.filterIsInstance<IkkeVurdertVilkår>().isEmpty()) {
            "Vi støtter ikke delvis vurderte vilkår i søknadsbehandlingen (enda), da må denne funksjonen endres"
        }
    }
    customVilkår.filterIsInstance<OpplysningspliktVilkår>().ifNotEmpty {
        throw IllegalArgumentException("Vi støtter ikke å manuelt legge til opplysningsplikt-vilkår i søknadsbehandlingen (enda)")
    }

    val (defaultGrunnlagsdata, defaultVilkår) = GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling(
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
        val vilkårsvurdert = when (defaultVilkår) {
            is VilkårsvurderingerSøknadsbehandling.Alder -> {
                søknadsbehandling
                    .leggTilPensjonsVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { defaultVilkår.pensjon as PensjonsVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilFamiliegjenforeningvilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { defaultVilkår.familiegjenforening as FamiliegjenforeningVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilLovligOpphold(
                        saksbehandler = saksbehandler,
                        lovligOppholdVilkår = customVilkår.customOrDefault { defaultVilkår.lovligOpphold as LovligOppholdVilkår.Vurdert },
                    ).getOrFail()
                    .leggTilFastOppholdINorgeVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { defaultVilkår.fastOpphold as FastOppholdINorgeVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilInstitusjonsoppholdVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { defaultVilkår.institusjonsopphold as InstitusjonsoppholdVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilUtenlandsopphold(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { defaultVilkår.utenlandsopphold as UtenlandsoppholdVilkår.Vurdert },
                    )
                    .getOrFail()
                    .oppdaterBosituasjon(
                        saksbehandler = saksbehandler,
                        bosituasjon = customGrunnlag.customOrDefault { defaultGrunnlagsdata.bosituasjonSomFullstendig() }
                            .single(),
                    )
                    .getOrFail()
                    .leggTilSkatt(
                        skatt = eksterneGrunnlag.skatt,
                    )
                    .getOrFail()
                    .let {
                        it is VilkårsvurdertSøknadsbehandling.Innvilget
                        if (!customVilkår.any { it is FormueVilkår.IkkeVurdert }) {
                            it.leggTilFormuegrunnlag(
                                request = LeggTilFormuevilkårRequest(
                                    behandlingId = søknadsbehandling.id,
                                    formuegrunnlag = customVilkår.customOrDefault { defaultVilkår.formue }.grunnlag.toFormueRequestGrunnlag(),
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
                        vilkår = customVilkår.customOrDefault { defaultVilkår.personligOppmøte as PersonligOppmøteVilkår.Vurdert },
                    )
                    .getOrFail()
            }

            is VilkårsvurderingerSøknadsbehandling.Uføre -> {
                søknadsbehandling.leggTilUførevilkår(
                    saksbehandler = saksbehandler,
                    vilkår = customVilkår.customOrDefault { defaultVilkår.uføre as UføreVilkår.Vurdert },
                ).getOrFail()
                    .leggTilFlyktningVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { defaultVilkår.flyktning as FlyktningVilkår.Vurdert },
                        clock = clock,
                    )
                    .getOrFail()
                    .leggTilLovligOpphold(
                        saksbehandler = saksbehandler,
                        lovligOppholdVilkår = customVilkår.customOrDefault { defaultVilkår.lovligOpphold as LovligOppholdVilkår.Vurdert },
                    ).getOrFail()
                    .leggTilFastOppholdINorgeVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { defaultVilkår.fastOpphold as FastOppholdINorgeVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilInstitusjonsoppholdVilkår(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { defaultVilkår.institusjonsopphold as InstitusjonsoppholdVilkår.Vurdert },
                    )
                    .getOrFail()
                    .leggTilUtenlandsopphold(
                        saksbehandler = saksbehandler,
                        vilkår = customVilkår.customOrDefault { defaultVilkår.utenlandsopphold as UtenlandsoppholdVilkår.Vurdert },
                    )
                    .getOrFail()
                    .oppdaterBosituasjon(
                        saksbehandler = saksbehandler,
                        bosituasjon = customGrunnlag.customOrDefault { defaultGrunnlagsdata.bosituasjonSomFullstendig() }
                            .single(),
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
                                    formuegrunnlag = customVilkår.customOrDefault { defaultVilkår.formue }.grunnlag.toFormueRequestGrunnlag(),
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
                        vilkår = customVilkår.customOrDefault { defaultVilkår.personligOppmøte as PersonligOppmøteVilkår.Vurdert },
                    )
                    .getOrFail()
            }
        }

        val medFradrag = if (customGrunnlag.customOrDefault { defaultGrunnlagsdata.fradragsgrunnlag }
                .isNotEmpty() && vilkårsvurdert is VilkårsvurdertSøknadsbehandling.Innvilget
        ) {
            vilkårsvurdert.oppdaterFradragsgrunnlag(
                saksbehandler = saksbehandler,
                fradragsgrunnlag = customGrunnlag.customOrDefault { defaultGrunnlagsdata.fradragsgrunnlag },
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
