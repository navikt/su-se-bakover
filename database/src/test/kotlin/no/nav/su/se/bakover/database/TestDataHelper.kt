package no.nav.su.se.bakover.database

import FastOppholdINorgeVilkårsvurderingPostgresRepo
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.fail
import kotliquery.using
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.avstemming.AvstemmingPostgresRepo
import no.nav.su.se.bakover.database.dokument.DokumentPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.BosituasjongrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FamiliegjenforeningVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FlyktningVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormuegrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.InstitusjonsoppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.LovligOppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.LovligOppholdgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.OpplysningspliktGrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.OpplysningspliktVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.PensjonVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.PensjonsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.PersonligOppmøteGrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.PersonligOppmøteVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføregrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UtenlandsoppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UtenlandsoppholdgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.klage.klageinstans.KlageinstanshendelsePostgresRepo
import no.nav.su.se.bakover.database.nøkkeltall.NøkkeltallPostgresRepo
import no.nav.su.se.bakover.database.person.PersonPostgresRepo
import no.nav.su.se.bakover.database.personhendelse.PersonhendelsePostgresRepo
import no.nav.su.se.bakover.database.regulering.ReguleringPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.tilbakekreving.TilbakekrevingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.vedtak.VedtakPostgresRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.Hjemmel
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.UprosessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.enUkeEtterFixedTidspunkt
import no.nav.su.se.bakover.test.epsFnr
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.gjeldendeVedtaksdata
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.grunnlagsdataMedEpsMedFradrag
import no.nav.su.se.bakover.test.nyUtbetalingSimulert
import no.nav.su.se.bakover.test.opphørUtbetalingSimulert
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulerNyUtbetaling
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.test.vilkår.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt0
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttUføreOgAndreInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingerSøknadsbehandlingInnvilget
import java.time.Clock
import java.time.LocalDate
import java.util.LinkedList
import java.util.UUID
import javax.sql.DataSource

internal val oppgaveId = OppgaveId("oppgaveId")
internal val journalpostId = JournalpostId("journalpostId")
internal val innsender = NavIdentBruker.Veileder("navIdent")

internal fun innvilgetBeregning(
    periode: Periode = år(2021),
): Beregning {
    return no.nav.su.se.bakover.test.beregning(periode)
}

internal fun simulering(fnr: Fnr) = Simulering(
    gjelderId = fnr,
    gjelderNavn = "gjelderNavn",
    datoBeregnet = fixedLocalDate,
    nettoBeløp = 100,
    periodeList = emptyList(),
)

internal val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")
internal val veileder = NavIdentBruker.Veileder("veileder")
internal val underkjentAttestering =
    Attestering.Underkjent(
        attestant = attestant,
        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
        kommentar = "kommentar",
        opprettet = fixedTidspunkt,
    )
internal val iverksattAttestering = Attestering.Iverksatt(attestant, enUkeEtterFixedTidspunkt)
internal val avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt)

internal fun utbetalingslinje(
    periode: Periode = stønadsperiode2021.periode,
    kjøreplan: UtbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
): Utbetalingslinje.Ny {
    return no.nav.su.se.bakover.test.utbetalingslinje(
        periode = periode,
        beløp = 25000,
        kjøreplan = kjøreplan,
    )
}

internal fun oversendtUtbetalingUtenKvittering(
    id: UUID30 = UUID30.randomUUID(),
    søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(søknadsbehandling.periode)),
): Utbetaling.UtbetalingKlargjortForOversendelse.UtenKvittering {
    return oversendtUtbetalingUtenKvittering(
        id = id,
        fnr = søknadsbehandling.fnr,
        sakId = søknadsbehandling.sakId,
        saksnummer = søknadsbehandling.saksnummer,
        utbetalingslinjer = utbetalingslinjer,
        avstemmingsnøkkel = avstemmingsnøkkel,
    )
}

internal fun oversendtUtbetalingUtenKvittering(
    id: UUID30 = UUID30.randomUUID(),
    revurdering: RevurderingTilAttestering,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(revurdering.periode)),
): Utbetaling.UtbetalingKlargjortForOversendelse.UtenKvittering {
    return oversendtUtbetalingUtenKvittering(
        id = id,
        fnr = revurdering.fnr,
        sakId = revurdering.sakId,
        saksnummer = revurdering.saksnummer,
        utbetalingslinjer = utbetalingslinjer,
        avstemmingsnøkkel = avstemmingsnøkkel,
    )
}

internal fun oversendtUtbetalingUtenKvittering(
    id: UUID30 = UUID30.randomUUID(),
    fnr: Fnr,
    sakId: UUID,
    saksnummer: Saksnummer,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje()),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
): Utbetaling.UtbetalingKlargjortForOversendelse.UtenKvittering {
    return Utbetaling.UtbetalingForSimulering(
        id = id,
        opprettet = fixedTidspunkt,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer,
        behandler = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel,
        sakstype = Sakstype.UFØRE, // TODO("simulering_utbetaling_alder utled fra sak/behandling")
    ).toSimulertUtbetaling(
        simulering = simulering(fnr),
    ).toOversendtUtbetaling(
        oppdragsmelding = Utbetalingsrequest("<xml></xml>"),
    )
}

internal val kvitteringOk = Kvittering(
    utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
    originalKvittering = "hallo",
    mottattTidspunkt = fixedTidspunkt,
)

internal val dbMetricsStub: DbMetrics = object : DbMetrics {
    override fun <T> timeQuery(label: String, block: () -> T): T {
        return block()
    }
}

internal val sessionCounterStub: SessionCounter = SessionCounter {
    fail("Database sessions were over the threshold while running test.")
}

internal class TestDataHelper(
    internal val dataSource: DataSource,
    internal val dbMetrics: DbMetrics = dbMetricsStub,
    internal val clock: Clock = fixedClock,
    satsFactory: SatsFactoryForSupplerendeStønad = satsFactoryTest,
) {
    internal val sessionFactory: PostgresSessionFactory =
        PostgresSessionFactory(dataSource, dbMetricsStub, sessionCounterStub)
    internal val utbetalingRepo = UtbetalingPostgresRepo(
        sessionFactory = sessionFactory,
        dbMetrics = dbMetrics,
    )
    internal val søknadRepo = SøknadPostgresRepo(
        sessionFactory = sessionFactory,
        dbMetrics = dbMetrics,
    )
    internal val uføregrunnlagPostgresRepo = UføregrunnlagPostgresRepo(dbMetrics)
    internal val utenlandsoppholdgrunnlagPostgresRepo = UtenlandsoppholdgrunnlagPostgresRepo(dbMetrics)
    internal val fradragsgrunnlagPostgresRepo = FradragsgrunnlagPostgresRepo(
        dbMetrics = dbMetrics,
    )
    internal val bosituasjongrunnlagPostgresRepo = BosituasjongrunnlagPostgresRepo(
        dbMetrics = dbMetrics,
    )
    internal val uføreVilkårsvurderingRepo = UføreVilkårsvurderingPostgresRepo(
        dbMetrics = dbMetrics,
        uføregrunnlagRepo = uføregrunnlagPostgresRepo,
    )
    internal val lovligOppholdGrunnlagPostgresRepo = LovligOppholdgrunnlagPostgresRepo(dbMetrics)
    internal val utenlandsoppholdVilkårsvurderingRepo = UtenlandsoppholdVilkårsvurderingPostgresRepo(
        utenlandsoppholdgrunnlagRepo = utenlandsoppholdgrunnlagPostgresRepo,
        dbMetrics = dbMetrics,
    )
    internal val avkortingsvarselRepo = AvkortingsvarselPostgresRepo(sessionFactory, dbMetrics)
    internal val formuegrunnlagPostgresRepo = FormuegrunnlagPostgresRepo(dbMetrics)
    internal val formueVilkårsvurderingPostgresRepo = FormueVilkårsvurderingPostgresRepo(
        dbMetrics = dbMetrics,
        formuegrunnlagPostgresRepo = formuegrunnlagPostgresRepo,
    )
    internal val opplysningspliktGrunnlagPostgresRepo = OpplysningspliktGrunnlagPostgresRepo(dbMetrics)
    internal val opplysningspliktVilkårsvurderingPostgresRepo = OpplysningspliktVilkårsvurderingPostgresRepo(
        dbMetrics = dbMetrics,
        opplysningspliktGrunnlagRepo = opplysningspliktGrunnlagPostgresRepo,
    )
    internal val pensjonsgrunnlagPostgresRepo = PensjonsgrunnlagPostgresRepo(dbMetrics)
    internal val vilkårsvurderingPensjonPostgresRepo = PensjonVilkårsvurderingPostgresRepo(
        dbMetrics = dbMetrics,
        pensjonsgrunnlagPostgresRepo = pensjonsgrunnlagPostgresRepo,
    )
    internal val lovligOppholdVilkårsvurderingPostgresRepo = LovligOppholdVilkårsvurderingPostgresRepo(
        dbMetrics = dbMetrics,
        lovligOppholdGrunnlagPostgresRepo = lovligOppholdGrunnlagPostgresRepo,
    )
    internal val institusjonsoppholdVilkårsvurderingPostgresRepo = InstitusjonsoppholdVilkårsvurderingPostgresRepo(
        dbMetrics = dbMetrics,
    )
    internal val familiegjenforeningVilkårsvurderingPostgresRepo =
        FamiliegjenforeningVilkårsvurderingPostgresRepo(dbMetrics)
    internal val flyktningVilkårsvurderingPostgresRepo = FlyktningVilkårsvurderingPostgresRepo(
        dbMetrics = dbMetrics,
    )
    internal val fastOppholdVilkårsvurderingRepo = FastOppholdINorgeVilkårsvurderingPostgresRepo(
        dbMetrics = dbMetrics,
    )

    internal val personligOppmøteGrunnlagPostgresRepo = PersonligOppmøteGrunnlagPostgresRepo(dbMetrics)
    internal val personligOppmøteVilkårsvurderingPostgresRepo = PersonligOppmøteVilkårsvurderingPostgresRepo(
        personligOppmøteGrunnlagPostgresRepo = personligOppmøteGrunnlagPostgresRepo,
        dbMetrics = dbMetrics,
    )
    internal val grunnlagsdataOgVilkårsvurderingerPostgresRepo = GrunnlagsdataOgVilkårsvurderingerPostgresRepo(
        dbMetrics = dbMetrics,
        bosituasjongrunnlagPostgresRepo = bosituasjongrunnlagPostgresRepo,
        fradragsgrunnlagPostgresRepo = fradragsgrunnlagPostgresRepo,
        uføreVilkårsvurderingPostgresRepo = uføreVilkårsvurderingRepo,
        formueVilkårsvurderingPostgresRepo = formueVilkårsvurderingPostgresRepo,
        utenlandsoppholdVilkårsvurderingPostgresRepo = utenlandsoppholdVilkårsvurderingRepo,
        opplysningspliktVilkårsvurderingPostgresRepo = opplysningspliktVilkårsvurderingPostgresRepo,
        pensjonVilkårsvurderingPostgresRepo = vilkårsvurderingPensjonPostgresRepo,
        familiegjenforeningVilkårsvurderingPostgresRepo = familiegjenforeningVilkårsvurderingPostgresRepo,
        lovligOppholdVilkårsvurderingPostgresRepo = lovligOppholdVilkårsvurderingPostgresRepo,
        flyktningVilkårsvurderingPostgresRepo = flyktningVilkårsvurderingPostgresRepo,
        fastOppholdINorgeVilkårsvurderingPostgresRepo = fastOppholdVilkårsvurderingRepo,
        personligOppmøteVilkårsvurderingPostgresRepo = personligOppmøteVilkårsvurderingPostgresRepo,
        institusjonsoppholdVilkårsvurderingPostgresRepo = institusjonsoppholdVilkårsvurderingPostgresRepo,
    )
    internal val søknadsbehandlingRepo = SøknadsbehandlingPostgresRepo(
        sessionFactory = sessionFactory,
        dbMetrics = dbMetrics,
        grunnlagsdataOgVilkårsvurderingerPostgresRepo = grunnlagsdataOgVilkårsvurderingerPostgresRepo,
        avkortingsvarselRepo = avkortingsvarselRepo,
        satsFactory = satsFactory,
    )
    internal val klageinstanshendelsePostgresRepo = KlageinstanshendelsePostgresRepo(sessionFactory, dbMetrics)
    internal val klagePostgresRepo = KlagePostgresRepo(sessionFactory, dbMetrics, klageinstanshendelsePostgresRepo)
    internal val tilbakekrevingRepo = TilbakekrevingPostgresRepo(sessionFactory)
    internal val reguleringRepo = ReguleringPostgresRepo(
        sessionFactory = sessionFactory,
        grunnlagsdataOgVilkårsvurderingerPostgresRepo = grunnlagsdataOgVilkårsvurderingerPostgresRepo,
        dbMetrics = dbMetrics,
        satsFactory = satsFactory,
    )
    internal val revurderingRepo = RevurderingPostgresRepo(
        sessionFactory = sessionFactory,
        dbMetrics = dbMetrics,
        grunnlagsdataOgVilkårsvurderingerPostgresRepo = grunnlagsdataOgVilkårsvurderingerPostgresRepo,
        avkortingsvarselRepo = avkortingsvarselRepo,
        tilbakekrevingRepo = tilbakekrevingRepo,
        satsFactory = satsFactory,
    )
    internal val vedtakRepo = VedtakPostgresRepo(
        sessionFactory = sessionFactory,
        dbMetrics = dbMetrics,
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        revurderingRepo = revurderingRepo,
        klageRepo = klagePostgresRepo,
        reguleringRepo = reguleringRepo,
        satsFactory = satsFactory,
    )
    internal val personRepo = PersonPostgresRepo(
        sessionFactory = sessionFactory,
        dbMetrics = dbMetrics,
    )
    internal val nøkkeltallRepo =
        NøkkeltallPostgresRepo(sessionFactory = sessionFactory, dbMetrics = dbMetrics, clock = fixedClock)
    internal val dokumentRepo = DokumentPostgresRepo(sessionFactory, dbMetrics, clock)
    internal val hendelsePostgresRepo = PersonhendelsePostgresRepo(sessionFactory, dbMetrics, fixedClock)

    internal val sakRepo = SakPostgresRepo(
        sessionFactory = sessionFactory,
        dbMetrics = dbMetrics,
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        revurderingRepo = revurderingRepo,
        vedtakPostgresRepo = vedtakRepo,
        klageRepo = klagePostgresRepo,
        reguleringRepo = reguleringRepo,
        avkortingsvarselRepo = avkortingsvarselRepo,
    )

    internal val avstemmingRepo = AvstemmingPostgresRepo(
        sessionFactory,
        dbMetrics,
    )
    internal val jobContextRepo = JobContextPostgresRepo(
        sessionFactory = sessionFactory,
    )

    /**
     * Oppretter og persisterer en ny sak (dersom den ikke finnes fra før) med søknad med tomt søknadsinnhold.
     * Søknaden er uten journalføring og oppgave.
     */
    fun persisterSakMedSøknadUtenJournalføringOgOppgave(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        fnr: Fnr = Fnr.generer(),
        søknadInnhold: SøknadsinnholdUføre = SøknadInnholdTestdataBuilder.build(),
        søknadInnsendtAv: NavIdentBruker = veileder,
    ): NySak {
        return SakFactory(
            clock = clock,
            uuidFactory = object : UUIDFactory() {
                val ids = LinkedList(listOf(sakId, søknadId))
                override fun newUUID(): UUID {
                    return ids.pop()
                }
            },
        ).nySakMedNySøknad(
            fnr = fnr,
            søknadInnhold = søknadInnhold,
            innsendtAv = søknadInnsendtAv,
        ).also {
            sakRepo.opprettSak(it)
        }
    }

    /**
     * Oppretter og persisterer en ny søknad på en eksisterende sak med tomt søknadsinnhold.
     * Søknaden er uten journalføring og oppgave.
     */
    fun persisterSøknadUtenJournalføringOgOppgavePåEksisterendeSak(
        sakId: UUID,
        søknadId: UUID = UUID.randomUUID(),
        søknadInnhold: SøknadsinnholdUføre = SøknadInnholdTestdataBuilder.build(),
        identBruker: NavIdentBruker = innsender,
    ): Søknad.Ny {
        return Søknad.Ny(
            sakId = sakId,
            id = søknadId,
            søknadInnhold = søknadInnhold,
            opprettet = fixedTidspunkt,
            innsendtAv = identBruker,
        ).also { søknadRepo.opprettSøknad(it) }
    }

    /**
     * Oppretter og persisterer en ny lukket søknad på en eksisterende sak med tomt søknadsinnhold.
     * Søknaden er journalført og har oppgave (vi skal ikke kunne lukke en søknad før den har blitt journalført med oppgave).
     */
    fun persisterLukketJournalførtSøknadMedOppgave(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        fnr: Fnr = Fnr.generer(),
        journalpostId: JournalpostId = no.nav.su.se.bakover.database.journalpostId,
        søknadInnhold: SøknadsinnholdUføre = SøknadInnholdTestdataBuilder.build(),
    ): Søknad.Journalført.MedOppgave.Lukket {
        return persisterJournalførtSøknadMedOppgave(
            sakId = sakId,
            søknadId = søknadId,
            fnr = fnr,
            journalpostId = journalpostId,
            søknadInnhold = søknadInnhold,
        ).second
            .let {
                it.lukk(
                    trekkSøknad(søknadId),
                ).also { lukketSøknad ->
                    søknadRepo.lukkSøknad(lukketSøknad)
                }
            }
    }

    fun persisterSakOgJournalførtSøknadUtenOppgave(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        fnr: Fnr = Fnr.generer(),
        journalpostId: JournalpostId = no.nav.su.se.bakover.database.journalpostId,
        søknadInnhold: SøknadsinnholdUføre = SøknadInnholdTestdataBuilder.build(),
    ): Pair<Sak, Søknad.Journalført.UtenOppgave> {
        val nySak: NySak = persisterSakMedSøknadUtenJournalføringOgOppgave(
            fnr = fnr,
            sakId = sakId,
            søknadId = søknadId,
            søknadInnhold = søknadInnhold,
        )
        val journalførtSøknad = nySak.søknad.journalfør(journalpostId).also { journalførtSøknad ->
            søknadRepo.oppdaterjournalpostId(journalførtSøknad)
        }
        return Pair(
            sakRepo.hentSak(nySak.id) ?: throw IllegalStateException("Fant ikke sak rett etter vi opprettet den."),
            journalførtSøknad,
        )
    }

    fun persisterJournalførtSøknadUtenOppgaveForEksisterendeSak(
        sakId: UUID,
        søknadId: UUID = UUID.randomUUID(),
        journalpostId: JournalpostId = no.nav.su.se.bakover.database.journalpostId,
        søknadInnhold: SøknadsinnholdUføre = SøknadInnholdTestdataBuilder.build(),
    ): Søknad.Journalført.UtenOppgave {
        return persisterSøknadUtenJournalføringOgOppgavePåEksisterendeSak(
            sakId = sakId,
            søknadId = søknadId,
            søknadInnhold = søknadInnhold,
        ).journalfør(journalpostId)
            .also {
                søknadRepo.oppdaterjournalpostId(it)
            }
    }

    /**
     * Oppretter ikke ny sak dersom den finnes fra før.
     */
    fun persisterJournalførtSøknadMedOppgave(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        fnr: Fnr = Fnr.generer(),
        oppgaveId: OppgaveId = no.nav.su.se.bakover.database.oppgaveId,
        journalpostId: JournalpostId = no.nav.su.se.bakover.database.journalpostId,
        søknadInnhold: SøknadsinnholdUføre = SøknadInnholdTestdataBuilder.build(),
    ): Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> {
        return sakRepo.hentSak(sakId).let {
            if (it == null) {
                persisterSakOgJournalførtSøknadUtenOppgave(
                    sakId = sakId,
                    søknadId = søknadId,
                    fnr = fnr,
                    journalpostId = journalpostId,
                    søknadInnhold = søknadInnhold,
                )
            } else {
                Pair(
                    it,
                    persisterJournalførtSøknadUtenOppgaveForEksisterendeSak(
                        sakId = sakId,
                        søknadId = søknadId,
                        journalpostId = journalpostId,
                        søknadInnhold = søknadInnhold,
                    ),
                )
            }
        }.let {
            val medOppgave = it.second.medOppgave(oppgaveId).also { søknad ->
                søknadRepo.oppdaterOppgaveId(søknad)
            }
            Pair(
                sakRepo.hentSak(it.first.id)
                    ?: throw IllegalStateException("Fant ikke sak rett etter vi opprettet den."),
                medOppgave,
            )
        }
    }

    /**
     * Persisterer:
     * 1. [Søknadsbehandling.Iverksatt.Innvilget]
     * 1. [Utbetaling.UtbetalingKlargjortForOversendelse]:
     *    1. [Utbetaling.OversendtUtbetaling.UtenKvittering]
     *    1. [Utbetaling.OversendtUtbetaling.MedKvittering]
     * 1. [VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling]
     */
    fun persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(stønadsperiode.periode)),
        utbetalingId: UUID30 = UUID30.randomUUID(),
        epsFnr: Fnr? = null,
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
            stønadsperiode.periode,
        ),
        grunnlagsdata: Grunnlagsdata = if (epsFnr != null) {
            grunnlagsdataMedEpsMedFradrag(
                periode = stønadsperiode.periode,
                epsFnr = epsFnr,
            )
        } else {
            grunnlagsdataEnsligMedFradrag(stønadsperiode.periode)
        },
        søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget = persisterSøknadsbehandlingIverksattInnvilget(
            sakId = sakId,
            søknadId = søknadId,
            epsFnr = epsFnr,
            stønadsperiode = stønadsperiode,
            vilkårsvurderinger = vilkårsvurderinger,
            grunnlagsdata = grunnlagsdata,
        ).second,
    ): Triple<Sak, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling, Utbetaling.UtbetalingKlargjortForOversendelse.MedKvittering> {
        assert(søknadsbehandling.sakId == sakId && søknadsbehandling.søknad.sakId == sakId && søknadsbehandling.søknad.id == søknadId)

        val utbetalingMedKvittering: Utbetaling.UtbetalingKlargjortForOversendelse.MedKvittering = oversendtUtbetalingUtenKvittering(
            id = utbetalingId,
            søknadsbehandling = søknadsbehandling,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = utbetalingslinjer,
        ).let {
            utbetalingRepo.opprettUtbetaling(it, sessionFactory.newTransactionContext())
            it.toKvittertUtbetaling(kvitteringOk).also { utbetalingMedKvittering ->
                utbetalingRepo.oppdaterMedKvittering(utbetalingMedKvittering)
            }
        }
        val vedtak = VedtakSomKanRevurderes.fromSøknadsbehandling(
            søknadsbehandling = søknadsbehandling,
            utbetalingId = utbetalingMedKvittering.id,
            clock = fixedClock,
        ).also {
            vedtakRepo.lagre(it)
        }
        return Triple(
            sakRepo.hentSak(sakId)!!,
            vedtak,
            utbetalingMedKvittering,
        )
    }

    /**
     * Persisterer:
     * 1. [Søknadsbehandling.Iverksatt.Innvilget]
     * 1. [Utbetaling.UtbetalingKlargjortForOversendelse.UtenKvittering]
     * 1. [VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling]
     */
    fun persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingUtenKvittering(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(stønadsperiode.periode)),
        utbetalingId: UUID30 = UUID30.randomUUID(),
        søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget = persisterSøknadsbehandlingIverksattInnvilget(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second,
    ): Pair<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling, Utbetaling.UtbetalingKlargjortForOversendelse.UtenKvittering> {
        assert(søknadsbehandling.sakId == sakId && søknadsbehandling.søknad.sakId == sakId && søknadsbehandling.søknad.id == søknadId)

        val utbetalingUtenKvittering: Utbetaling.UtbetalingKlargjortForOversendelse.UtenKvittering = oversendtUtbetalingUtenKvittering(
            id = utbetalingId,
            søknadsbehandling = søknadsbehandling,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = utbetalingslinjer,
        ).also {
            utbetalingRepo.opprettUtbetaling(it, sessionFactory.newTransactionContext())
        }
        return Pair(
            VedtakSomKanRevurderes.fromSøknadsbehandling(
                søknadsbehandling,
                utbetalingUtenKvittering.id,
                fixedClock,
            ).also {
                vedtakRepo.lagre(it)
            },
            utbetalingUtenKvittering,
        )
    }

    fun persisterVedtakMedAvslåttSøknadsbehandlingUtenBeregning(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning = persisterSøknadsbehandlingIverksattAvslagUtenBeregning(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second,
    ): Triple<Sak, Avslagsvedtak.AvslagVilkår, Søknadsbehandling.Iverksatt.Avslag.UtenBeregning> {
        assert(sakId == søknadsbehandling.sakId && søknadId == søknadsbehandling.søknad.id && søknadsbehandling.stønadsperiode == stønadsperiode)
        val vedtak = Avslagsvedtak.fromSøknadsbehandlingUtenBeregning(
            avslag = søknadsbehandling,
            clock = fixedClock,
        )
        return Triple(sakRepo.hentSak(sakId)!!, vedtak, søknadsbehandling)
    }

    /**
     * Persisterer:
     * 1. [Utbetaling.UtbetalingKlargjortForOversendelse]:
     *    1. [Utbetaling.OversendtUtbetaling.UtenKvittering]
     *    1. [Utbetaling.OversendtUtbetaling.MedKvittering]
     * 1. [VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering]
     */
    fun persisterVedtakMedInnvilgetRevurderingOgOversendtUtbetalingMedKvittering(
        revurdering: RevurderingTilAttestering.Innvilget = persisterRevurderingTilAttesteringInnvilget().second,
        periode: Periode = stønadsperiode2021.periode,
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode)),
        utbetalingId: UUID30 = UUID30.randomUUID(),
    ): Pair<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering, Utbetaling.UtbetalingKlargjortForOversendelse.MedKvittering> {
        val utbetaling = oversendtUtbetalingUtenKvittering(
            id = utbetalingId,
            revurdering = revurdering,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = utbetalingslinjer,
        ).let {
            utbetalingRepo.opprettUtbetaling(it, sessionFactory.newTransactionContext())
            it.toKvittertUtbetaling(kvitteringOk).also { utbetalingMedKvittering ->
                utbetalingRepo.oppdaterMedKvittering(utbetalingMedKvittering)
            }
        }
        return Pair(
            VedtakSomKanRevurderes.from(
                revurdering = revurdering.tilIverksatt(
                    attestant = attestant,
                    clock = fixedClock,
                    hentOpprinneligAvkorting = { avkortingid ->
                        avkortingsvarselRepo.hent(id = avkortingid)
                    },
                ).getOrFail().also {
                    revurderingRepo.lagre(it)
                },
                utbetalingId = utbetaling.id,
                fixedClock,
            ).also {
                vedtakRepo.lagre(it)
            },
            utbetaling,
        )
    }

    fun persisterVedtakForKlageIverksattAvvist(
        klage: IverksattAvvistKlage = persisterKlageIverksattAvvist(),
    ): Klagevedtak.Avvist {
        return Klagevedtak.Avvist.fromIverksattAvvistKlage(klage, fixedClock).also {
            vedtakRepo.lagre(it)
        }
    }

    fun persisterVedtakForStans(
        stans: StansAvYtelseRevurdering.IverksattStansAvYtelse = persisterStansAvYtelseIverksatt(),
        periode: Periode = stønadsperiode2021.periode,
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode)),
        utbetalingId: UUID30 = UUID30.randomUUID(),
    ): VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse {
        oversendtUtbetalingUtenKvittering(
            id = utbetalingId,
            fnr = stans.fnr,
            sakId = stans.sakId,
            saksnummer = stans.saksnummer,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = utbetalingslinjer,
        ).let {
            utbetalingRepo.opprettUtbetaling(it, sessionFactory.newTransactionContext())
            it.toKvittertUtbetaling(kvitteringOk).also { utbetalingMedKvittering ->
                utbetalingRepo.oppdaterMedKvittering(utbetalingMedKvittering)
            }
        }
        return VedtakSomKanRevurderes.from(stans, utbetalingId, fixedClock).also {
            vedtakRepo.lagre(it)
        }
    }

    /**
     * TODO jah: På sikt burde denne muligens først persistere en stans, men føler det er litt out of scope for denne PR.
     */
    fun persisterVedtakForGjenopptak(
        stans: GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse = persisterGjenopptakAvYtelseIverksatt(),
        periode: Periode = stønadsperiode2021.periode,
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode)),
        utbetalingId: UUID30 = UUID30.randomUUID(),
    ): VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse {
        oversendtUtbetalingUtenKvittering(
            id = utbetalingId,
            fnr = stans.fnr,
            sakId = stans.sakId,
            saksnummer = stans.saksnummer,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = utbetalingslinjer,
        ).let {
            utbetalingRepo.opprettUtbetaling(it, sessionFactory.newTransactionContext())
            it.toKvittertUtbetaling(kvitteringOk).also { utbetalingMedKvittering ->
                utbetalingRepo.oppdaterMedKvittering(utbetalingMedKvittering)
            }
        }
        return VedtakSomKanRevurderes.from(stans, utbetalingId, fixedClock).also {
            vedtakRepo.lagre(it)
        }
    }

    fun persisterReguleringOpprettet(
        startDato: LocalDate = 1.mai(2021),
        clock: Clock = fixedClock,
    ): Regulering.OpprettetRegulering =
        persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().first.let { sak ->
            sak.opprettEllerOppdaterRegulering(
                startDato = startDato,
                clock = clock,
            ).getOrFail().also {
                reguleringRepo.lagre(it)
            }
        }

    fun persisterReguleringIverksatt(
        startDato: LocalDate = 1.mai(2021),
        clock: Clock = fixedClock,
    ) =
        persisterReguleringOpprettet(
            startDato = startDato,
            clock = clock,
        ).let {
            it.beregn(
                satsFactory = satsFactoryTestPåDato(),
                begrunnelse = "Begrunnelse",
                clock = clock,
            ).getOrFail()
                .simuler { simulertUtbetaling().right() }
                .getOrFail().tilIverksatt()
                .also { iverksattAttestering ->
                    reguleringRepo.lagre(iverksattAttestering)
                }
        }

    fun persisterRevurderingOpprettet(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().let { Pair(it.first, it.second) },
        periode: Periode = år(2021),
        epsFnr: Fnr? = null,
        grunnlagsdata: Grunnlagsdata = if (epsFnr != null) {
            grunnlagsdataMedEpsMedFradrag(
                periode,
                epsFnr,
            )
        } else {
            grunnlagsdataEnsligMedFradrag(periode)
        },
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = periode)
            .tilVilkårsvurderingerRevurdering(),
    ): Pair<Sak, OpprettetRevurdering> {
        sakOgVedtak.let { (sak, innvilget) ->
            return OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = innvilget.id,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = Revurderingsårsak(
                    Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
                ),
                forhåndsvarsel = null,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = AvkortingVedRevurdering.Uhåndtert.IngenUtestående,
                sakinfo = sak.info(),
            ).let {
                revurderingRepo.lagre(it)
                sak.copy(
                    revurderinger = sak.revurderinger + listOf(it),
                ) to it
            }
        }
    }

    fun persisterRevurderingBeregnetInnvilget(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().let { Pair(it.first, it.second) },
    ): Pair<Sak, BeregnetRevurdering.Innvilget> {
        val (sak, vedtak) = sakOgVedtak
        return persisterRevurderingOpprettet(
            sakOgVedtak = sak to vedtak,
            periode = stønadsperiode2021.periode,
            epsFnr = null,
        ).let { (sak, opprettet) ->
            opprettet.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = clock,
                gjeldendeVedtaksdata = sak.gjeldendeVedtaksdata(stønadsperiode2021),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let { beregnet ->
                revurderingRepo.lagre(beregnet)
                sak.copy(
                    revurderinger = sak.revurderinger.filterNot { it.id == opprettet.id } + listOf(beregnet),
                ) to beregnet as BeregnetRevurdering.Innvilget
            }
        }
    }

    fun persisterRevurderingBeregnetOpphørt(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().let {
            Pair(
                it.first,
                it.second,
            )
        },
    ): Pair<Sak, BeregnetRevurdering.Opphørt> {
        return sakOgVedtak.let { (sak, vedtak) ->
            persisterRevurderingOpprettet(
                sakOgVedtak = sak to vedtak as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling,
                periode = stønadsperiode2021.periode,
                epsFnr = null,
                vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgAndreInnvilget(
                    periode = stønadsperiode2021.periode,
                ),
            ).let { (sak, opprettet) ->
                opprettet.beregn(
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    clock = clock,
                    gjeldendeVedtaksdata = sak.gjeldendeVedtaksdata(stønadsperiode2021),
                    satsFactory = satsFactoryTestPåDato(),
                ).getOrFail().let { opphørt ->
                    revurderingRepo.lagre(opphørt)
                    sak.copy(
                        revurderinger = sak.revurderinger.filterNot { it.id == opphørt.id } + listOf(opphørt),
                    ) to opphørt as BeregnetRevurdering.Opphørt
                }
            }
        }
    }

    fun persisterRevurderingBeregningIngenEndring(): Pair<Sak, BeregnetRevurdering.IngenEndring> {
        val (sak, vedtak, _) = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
            stønadsperiode = stønadsperiode2021,
        )
        val gjeldende = sak.gjeldendeVedtaksdata(stønadsperiode2021)
        return persisterRevurderingOpprettet(
            sakOgVedtak = sak to vedtak,
            periode = stønadsperiode2021.periode,
            epsFnr = null,
            grunnlagsdata = gjeldende.grunnlagsdata,
            vilkårsvurderinger = gjeldende.vilkårsvurderinger,
        ).let { (sak, opprettet) ->
            opprettet.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = clock,
                gjeldendeVedtaksdata = sak.gjeldendeVedtaksdata(stønadsperiode2021),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let { beregnet ->
                revurderingRepo.lagre(beregnet)
                sak.copy(
                    revurderinger = sak.revurderinger.filterNot { it.id == beregnet.id } + listOf(beregnet),
                ) to beregnet as BeregnetRevurdering.IngenEndring
            }
        }
    }

    fun persisterRevurderingSimulertInnvilget(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().let { Pair(it.first, it.second) },
    ): Pair<Sak, SimulertRevurdering.Innvilget> {
        return persisterRevurderingBeregnetInnvilget(
            sakOgVedtak = sakOgVedtak,
        ).let { (sak, beregnet) ->
            beregnet.simuler(
                saksbehandler = saksbehandler,
                clock = clock,
            ) {
                nyUtbetalingSimulert(
                    sakOgBehandling = sak to beregnet,
                    beregning = beregnet.beregning,
                    clock = clock,
                ).right()
            }.getOrFail().let { simulert ->
                val håndtertForhåndsvarsel = simulert.ikkeSendForhåndsvarsel().getOrFail()
                val oppdatertTilbakekrevingsbehandling = if (håndtertForhåndsvarsel.harSimuleringFeilutbetaling()) {
                    håndtertForhåndsvarsel.oppdaterTilbakekrevingsbehandling(
                        Tilbakekrev(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            sakId = simulert.sakId,
                            revurderingId = simulert.id,
                            periode = simulert.periode,
                        ),
                    )
                } else {
                    håndtertForhåndsvarsel
                }
                revurderingRepo.lagre(oppdatertTilbakekrevingsbehandling)
                sak.copy(
                    revurderinger = sak.revurderinger.filterNot { it.id == oppdatertTilbakekrevingsbehandling.id } + listOf(oppdatertTilbakekrevingsbehandling),
                ) to oppdatertTilbakekrevingsbehandling
            }
        }
    }

    fun persisterRevurderingSimulertOpphørt(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().let {
            Pair(
                it.first,
                it.second,
            )
        },
    ): Pair<Sak, SimulertRevurdering.Opphørt> {
        return persisterRevurderingBeregnetOpphørt(sakOgVedtak).let { (sak, beregnet) ->
            beregnet.simuler(
                saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
                clock = clock,
            ) {
                opphørUtbetalingSimulert(
                    sakOgBehandling = sak to beregnet,
                    opphørsperiode = it.opphørsperiode,
                    clock = fixedClock,
                ).right()
            }.getOrFail().let { simulert ->
                val oppdatertTilbakekrevingsbehandling = if (simulert.harSimuleringFeilutbetaling()) {
                    simulert.oppdaterTilbakekrevingsbehandling(
                        Tilbakekrev(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            sakId = simulert.sakId,
                            revurderingId = simulert.id,
                            periode = simulert.periode,
                        ),
                    )
                } else {
                    simulert
                }
                revurderingRepo.lagre(oppdatertTilbakekrevingsbehandling)
                sak.copy(
                    revurderinger = sak.revurderinger.filterNot { it.id == oppdatertTilbakekrevingsbehandling.id } + listOf(oppdatertTilbakekrevingsbehandling),
                ) to oppdatertTilbakekrevingsbehandling
            }
        }
    }

    /**
     * Setter forhåndsvarsel til SkalIkkeForhåndsvarsel dersom den ikke er satt på dette tidspunktet.
     */
    fun persisterRevurderingTilAttesteringInnvilget(): Pair<Sak, RevurderingTilAttestering.Innvilget> {
        return persisterRevurderingSimulertInnvilget().let { (sak, simulert) ->
            val håndtertForhåndsvarsel = if (simulert.forhåndsvarsel == null) simulert.ikkeSendForhåndsvarsel().getOrFail() else simulert

            håndtertForhåndsvarsel.tilAttestering(
                attesteringsoppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "",
            ).getOrFail().let { tilAttestering ->
                revurderingRepo.lagre(tilAttestering)
                sak.copy(
                    revurderinger = sak.revurderinger.filterNot { it.id == tilAttestering.id } + listOf(tilAttestering),
                ) to tilAttestering
            }
        }
    }

    /**
     * Setter forhåndsvarsel til SkalIkkeForhåndsvarsel dersom den ikke er satt på dette tidspunktet.
     */
    @Suppress("unused")
    fun persisterRevurderingTilAttesteringIngenEndring(): RevurderingTilAttestering.IngenEndring {
        val beregnet: BeregnetRevurdering.IngenEndring = persisterRevurderingBeregningIngenEndring().second
        return beregnet.tilAttestering(
            attesteringsoppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            skalFøreTilUtsendingAvVedtaksbrev = false,
        ).also {
            revurderingRepo.lagre(it)
        }
    }

    /**
     * Setter forhåndsvarsel til SkalIkkeForhåndsvarsel.
     */
    fun persisterRevurderingTilAttesteringOpphørt(): RevurderingTilAttestering.Opphørt {
        return persisterRevurderingSimulertOpphørt().second.ikkeSendForhåndsvarsel().getOrFail().tilAttestering(
            attesteringsoppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
        ).getOrFail().also { revurderingRepo.lagre(it) }
    }

    fun persisterRevurderingIverksattInnvilget(): IverksattRevurdering.Innvilget {
        return persisterRevurderingTilAttesteringInnvilget().second.tilIverksatt(
            attestant = attestant,
            clock = fixedClock,
            hentOpprinneligAvkorting = { null },
        ).getOrHandle {
            throw IllegalStateException("Her skulle vi ha hatt en iverksatt revurdering")
        }.also {
            revurderingRepo.lagre(it)
        }
    }

    @Suppress("unused")
    fun persisterRevurderingIverksattIngenEndring(): IverksattRevurdering.IngenEndring {
        return persisterRevurderingTilAttesteringIngenEndring().tilIverksatt(
            attestant = attestant,
            clock = fixedClock,
            hentOpprinneligAvkorting = { null },
        ).getOrHandle {
            throw IllegalStateException("Her skulle vi ha hatt en iverksatt revurdering")
        }.also {
            revurderingRepo.lagre(it)
        }
    }

    fun persisterRevurderingIverksattOpphørt(): IverksattRevurdering.Opphørt {
        return persisterRevurderingTilAttesteringOpphørt().tilIverksatt(
            attestant,
            { null },
            fixedClock,
        ).getOrFail().also {
            revurderingRepo.lagre(it)
        }
    }

    fun persisterRevurderingUnderkjentInnvilget(): UnderkjentRevurdering.Innvilget {
        return persisterRevurderingTilAttesteringInnvilget().second.underkjenn(underkjentAttestering, OppgaveId("oppgaveid"))
            .also {
                revurderingRepo.lagre(it)
            } as UnderkjentRevurdering.Innvilget
    }

    /**
     * Baseres på [persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering] og [persisterRevurderingOpprettet]
     */
    fun persisterRevurderingAvsluttet(
        sakId: UUID = UUID.randomUUID(),
    ): AvsluttetRevurdering {
        val (sak, vedtak, _) = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(sakId = sakId)
        return persisterRevurderingOpprettet(
            sakOgVedtak = sak to vedtak,
            periode = stønadsperiode2021.periode,
        ).let { (_, opprettet) ->
            opprettet.avslutt(
                begrunnelse = "",
                brevvalg = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrFail().also { revurderingRepo.lagre(it) }
        }
    }

    fun persisterStansAvYtelseSimulert(
        id: UUID = UUID.randomUUID(),
        opprettet: Tidspunkt = fixedTidspunkt,
        periode: Periode = Periode.create(
            stønadsperiode2021.periode.fraOgMed.plusMonths(1),
            stønadsperiode2021.periode.tilOgMed,
        ),
        grunnlagsdata: Grunnlagsdata = grunnlagsdataMedEpsMedFradrag(periode, epsFnr),
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = periode)
            .tilVilkårsvurderingerRevurdering(),
        tilRevurdering: VedtakSomKanRevurderes = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
            stønadsperiode = stønadsperiode2021,
        ).second,
        simulering: Simulering = simulering(Fnr.generer()),
        revurderingsårsak: Revurderingsårsak = Revurderingsårsak(
            Revurderingsårsak.Årsak.DØDSFALL,
            Revurderingsårsak.Begrunnelse.create("begrunnelse"),
        ),
    ): StansAvYtelseRevurdering.SimulertStansAvYtelse {
        return StansAvYtelseRevurdering.SimulertStansAvYtelse(
            id = id,
            opprettet = opprettet,
            periode = periode,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            tilRevurdering = tilRevurdering.id,
            saksbehandler = saksbehandler,
            simulering = simulering,
            revurderingsårsak = revurderingsårsak,
            sakinfo = tilRevurdering.sakinfo(),
        ).also {
            revurderingRepo.lagre(it)
        }
    }

    fun persisterStansAvYtelseIverksatt(
        id: UUID = UUID.randomUUID(),
        opprettet: Tidspunkt = fixedTidspunkt,
        periode: Periode = Periode.create(
            stønadsperiode2021.periode.fraOgMed.plusMonths(1),
            stønadsperiode2021.periode.tilOgMed,
        ),
        grunnlagsdata: Grunnlagsdata = grunnlagsdataMedEpsMedFradrag(periode, epsFnr),
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = periode)
            .tilVilkårsvurderingerRevurdering(),
        tilRevurdering: VedtakSomKanRevurderes = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
            stønadsperiode = stønadsperiode2021,
        ).second,
        simulering: Simulering = simulering(Fnr.generer()),
        revurderingsårsak: Revurderingsårsak = Revurderingsårsak(
            Revurderingsårsak.Årsak.DØDSFALL,
            Revurderingsårsak.Begrunnelse.create("begrunnelse"),
        ),
    ): StansAvYtelseRevurdering.IverksattStansAvYtelse {
        return persisterStansAvYtelseSimulert(
            id,
            opprettet,
            periode,
            grunnlagsdata,
            vilkårsvurderinger,
            tilRevurdering,
            simulering,
            revurderingsårsak,
        ).iverksett(iverksattAttestering).getOrFail().also {
            revurderingRepo.lagre(it)
        }
    }

    fun persisterGjenopptakAvYtelseSimulert(
        id: UUID = UUID.randomUUID(),
        opprettet: Tidspunkt = fixedTidspunkt,
        periode: Periode = Periode.create(
            stønadsperiode2021.periode.fraOgMed.plusMonths(1),
            stønadsperiode2021.periode.tilOgMed,
        ),
        grunnlagsdata: Grunnlagsdata = grunnlagsdataMedEpsMedFradrag(periode, epsFnr),
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = periode)
            .tilVilkårsvurderingerRevurdering(),
        tilRevurdering: VedtakSomKanRevurderes = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
            stønadsperiode = stønadsperiode2021,
        ).second,
        simulering: Simulering = simulering(Fnr.generer()),
        revurderingsårsak: Revurderingsårsak = Revurderingsårsak(
            Revurderingsårsak.Årsak.DØDSFALL,
            Revurderingsårsak.Begrunnelse.create("begrunnelse"),
        ),
    ): GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse {
        return GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
            id = id,
            opprettet = opprettet,
            periode = periode,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            tilRevurdering = tilRevurdering.id,
            saksbehandler = saksbehandler,
            simulering = simulering,
            revurderingsårsak = revurderingsårsak,
            sakinfo = tilRevurdering.sakinfo(),
        ).also {
            revurderingRepo.lagre(it)
        }
    }

    /**
     * @param tilRevurdering default: [persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering]
     */
    fun persisterGjenopptakAvYtelseIverksatt(
        id: UUID = UUID.randomUUID(),
        opprettet: Tidspunkt = fixedTidspunkt,
        periode: Periode = Periode.create(
            stønadsperiode2021.periode.fraOgMed.plusMonths(1),
            stønadsperiode2021.periode.tilOgMed,
        ),
        grunnlagsdata: Grunnlagsdata = grunnlagsdataMedEpsMedFradrag(periode, epsFnr),
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = periode)
            .tilVilkårsvurderingerRevurdering(),
        tilRevurdering: VedtakSomKanRevurderes = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
            stønadsperiode = stønadsperiode2021,
        ).second,
        simulering: Simulering = simulering(Fnr.generer()),
        revurderingsårsak: Revurderingsårsak = Revurderingsårsak(
            Revurderingsårsak.Årsak.DØDSFALL,
            Revurderingsårsak.Begrunnelse.create("begrunnelse"),
        ),
    ): GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse {
        return persisterGjenopptakAvYtelseSimulert(
            id,
            opprettet,
            periode,
            grunnlagsdata,
            vilkårsvurderinger,
            tilRevurdering,
            simulering,
            revurderingsårsak,
        ).iverksett(iverksattAttestering).getOrFail().also {
            revurderingRepo.lagre(it)
        }
    }

    /**
     * Underliggende søknadsbehahandling: [Søknadsbehandling.Vilkårsvurdert.Uavklart]
     */
    fun persisterSøknadsbehandlingAvsluttet(
        id: UUID = UUID.randomUUID(),
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, LukketSøknadsbehandling> {
        return persisterSøknadsbehandlingVilkårsvurdertUavklart(
            id = id,
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.let {
            it.lukkSøknadsbehandlingOgSøknad(
                trekkSøknad(søknadId = søknadId),
            ).getOrFail().let { lukketSøknadsbehandling ->
                søknadsbehandlingRepo.lagre(lukketSøknadsbehandling)
                søknadRepo.lukkSøknad(lukketSøknadsbehandling.søknad)
                Pair(sakRepo.hentSak(sakId)!!, lukketSøknadsbehandling)
            }
        }
    }

    /**
     * 1) persisterer en [NySøknadsbehandling]
     * 2) legger stønadsperiode og persisterer
     */
    fun persisterSøknadsbehandlingVilkårsvurdertUavklart(
        id: UUID = UUID.randomUUID(),
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Uavklart> {
        val (sak, søknad) = persisterJournalførtSøknadMedOppgave(sakId = sakId, søknadId = søknadId)
        assert(sak.id == sakId && sak.søknader.count { it.sakId == sakId && it.id == søknadId } == 1)
        return NySøknadsbehandling(
            id = id,
            opprettet = fixedTidspunkt,
            sakId = sak.id,
            søknad = søknad,
            oppgaveId = søknad.oppgaveId,
            fnr = sak.fnr,
            avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
            sakstype = sak.type,
        ).let { nySøknadsbehandling ->
            søknadsbehandlingRepo.lagreNySøknadsbehandling(nySøknadsbehandling)
            sakRepo.hentSak(sakId)!!.oppdaterStønadsperiodeForSøknadsbehandling(
                søknadsbehandlingId = nySøknadsbehandling.id,
                stønadsperiode = stønadsperiode,
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
            ).getOrFail().let {
                søknadsbehandlingRepo.lagre(it)
                assert(it.fnr == sak.fnr && it.sakId == sakId)
                Pair(sakRepo.hentSak(sakId)!!, it as Søknadsbehandling.Vilkårsvurdert.Uavklart)
            }
        }
    }

    internal fun persisterSøknadsbehandlingVilkårsvurdertInnvilget(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
            periode = stønadsperiode.periode,
        ),
        grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligMedFradrag(periode = stønadsperiode.periode),
    ): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Innvilget> {
        return persisterSøknadsbehandlingVilkårsvurdertUavklart(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.copy(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        ).vilkårsvurder().let {
            søknadsbehandlingRepo.lagre(it)
            Pair(sakRepo.hentSak(sakId)!!, it as Søknadsbehandling.Vilkårsvurdert.Innvilget)
        }
    }

    internal fun persisterSøknadsbehandlingVilkårsvurdertAvslag(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        grunnlagsdata: Grunnlagsdata = Grunnlagsdata.create(
            bosituasjon = listOf(bosituasjongrunnlagEnslig(periode = stønadsperiode2021.periode)),
        ),
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = Vilkårsvurderinger.Søknadsbehandling.Uføre(
            uføre = innvilgetUførevilkår(periode = stønadsperiode2021.periode),
            formue = formuevilkårIkkeVurdert(),
            lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
            fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
            institusjonsopphold = institusjonsoppholdvilkårAvslag(),
            personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
            flyktning = FlyktningVilkår.IkkeVurdert,
            opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
            utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
        ),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Avslag> {
        return persisterSøknadsbehandlingVilkårsvurdertUavklart(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.copy(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        ).vilkårsvurder()
            .let {
                søknadsbehandlingRepo.lagre(it)
                Pair(sakRepo.hentSak(sakId)!!, it as Søknadsbehandling.Vilkårsvurdert.Avslag)
            }
    }

    fun persisterSøknadsbehandlingBeregnetInnvilget(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
            stønadsperiode.periode,
        ),
        grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligMedFradrag(stønadsperiode.periode),
    ): Pair<Sak, Søknadsbehandling.Beregnet.Innvilget> {
        return persisterSøknadsbehandlingVilkårsvurdertInnvilget(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
            vilkårsvurderinger = vilkårsvurderinger,
            grunnlagsdata = grunnlagsdata,
        ).second.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail().let {
            søknadsbehandlingRepo.lagre(it)
            Pair(sakRepo.hentSak(sakId)!!, it as Søknadsbehandling.Beregnet.Innvilget)
        }
    }

    internal fun persisterSøknadsbehandlingBeregnetAvslag(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.Beregnet.Avslag> {
        return persisterSøknadsbehandlingVilkårsvurdertInnvilget(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
            vilkårsvurderinger = vilkårsvurderingerSøknadsbehandlingInnvilget(
                periode = stønadsperiode.periode,
                uføre = innvilgetUførevilkårForventetInntekt0(
                    id = UUID.randomUUID(),
                    periode = stønadsperiode.periode,
                    uføregrunnlag = uføregrunnlagForventetInntekt(
                        id = UUID.randomUUID(),
                        periode = stønadsperiode.periode,
                        forventetInntekt = 1_000_000,
                    ),
                ),
            ),
        ).second.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail().let {
            søknadsbehandlingRepo.lagre(it as Søknadsbehandling.Beregnet.Avslag)
            Pair(sakRepo.hentSak(sakId)!!, it)
        }
    }

    internal fun persisterSøknadsbehandlingSimulert(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
            stønadsperiode.periode,
        ),
        grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligMedFradrag(stønadsperiode.periode),
    ): Pair<Sak, Søknadsbehandling.Simulert> {
        return persisterSøknadsbehandlingBeregnetInnvilget(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
            vilkårsvurderinger = vilkårsvurderinger,
            grunnlagsdata = grunnlagsdata,
        ).let { (sak, beregnet) ->
            beregnet.simuler(
                saksbehandler = saksbehandler,
            ) {
                simulerNyUtbetaling(
                    sak = sak,
                    request = it,
                    clock = fixedClock,
                )
            }
        }.getOrFail()
            .let {
                søknadsbehandlingRepo.lagre(it)
                Pair(sakRepo.hentSak(sakId)!!, it)
            }
    }

    internal fun persisterSøknadsbehandlingTilAttesteringInnvilget(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
            stønadsperiode.periode,
        ),
        grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligMedFradrag(stønadsperiode.periode),
        fritekstTilBrev: String = "",
    ): Pair<Sak, Søknadsbehandling.TilAttestering.Innvilget> {
        return persisterSøknadsbehandlingSimulert(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
            vilkårsvurderinger = vilkårsvurderinger,
            grunnlagsdata = grunnlagsdata,
        ).second.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
        ).let {
            søknadsbehandlingRepo.lagre(it)
            Pair(sakRepo.hentSak(sakId)!!, it)
        }
    }

    internal fun persisterSøknadsbehandlingTilAttesteringAvslagMedBeregning(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        fritekstTilBrev: String = "",
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.TilAttestering.Avslag.MedBeregning> {
        return persisterSøknadsbehandlingBeregnetAvslag(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
        ).let {
            søknadsbehandlingRepo.lagre(it)
            Pair(sakRepo.hentSak(sakId)!!, it)
        }
    }

    internal fun persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        fritekstTilBrev: String = "",
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.TilAttestering.Avslag.UtenBeregning> {
        return persisterSøknadsbehandlingVilkårsvurdertAvslag(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.tilAttestering(
            saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
        ).let {
            søknadsbehandlingRepo.lagre(it)
            Pair(sakRepo.hentSak(sakId)!!, it)
        }
    }

    internal fun persisterSøknadsbehandlingUnderkjentInnvilget(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.Underkjent.Innvilget> {
        return persisterSøknadsbehandlingTilAttesteringInnvilget(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.tilUnderkjent(
            underkjentAttestering,
        ).let {
            søknadsbehandlingRepo.lagre(it)
            Pair(sakRepo.hentSak(sakId)!!, it)
        }
    }

    internal fun persisterSøknadsbehandlingUnderkjentAvslagUtenBeregning(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.Underkjent.Avslag.UtenBeregning> {
        return persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.tilUnderkjent(
            underkjentAttestering,
        ).let {
            søknadsbehandlingRepo.lagre(it)
            Pair(sakRepo.hentSak(sakId)!!, it)
        }
    }

    internal fun persisterSøknadsbehandlingUnderkjentAvslagMedBeregning(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.Underkjent.Avslag.MedBeregning> {
        return persisterSøknadsbehandlingTilAttesteringAvslagMedBeregning(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.tilUnderkjent(
            underkjentAttestering,
        ).let {
            søknadsbehandlingRepo.lagre(it)
            Pair(sakRepo.hentSak(sakId)!!, it)
        }
    }

    internal fun persisterSøknadsbehandlingIverksattInnvilget(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
            stønadsperiode.periode,
        ),
        epsFnr: Fnr? = null,
        grunnlagsdata: Grunnlagsdata = if (epsFnr != null) {
            grunnlagsdataMedEpsMedFradrag(
                periode = stønadsperiode.periode,
                epsFnr = epsFnr,
            )
        } else {
            grunnlagsdataEnsligMedFradrag(stønadsperiode.periode)
        },
    ): Pair<Sak, Søknadsbehandling.Iverksatt.Innvilget> {
        return persisterSøknadsbehandlingTilAttesteringInnvilget(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
            vilkårsvurderinger = vilkårsvurderinger,
            grunnlagsdata = grunnlagsdata,
        ).second.tilIverksatt(
            attestering = iverksattAttestering,
        ).let {
            søknadsbehandlingRepo.lagre(it)
            Pair(sakRepo.hentSak(sakId)!!, it)
        }
    }

    internal fun persisterSøknadsbehandlingIverksattAvslagUtenBeregning(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        fritekstTilBrev: String = "",
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.Iverksatt.Avslag.UtenBeregning> {
        return persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning(
            sakId = sakId,
            søknadId = søknadId,
            fritekstTilBrev = fritekstTilBrev,
            stønadsperiode = stønadsperiode,
        ).second.tilIverksatt(
            iverksattAttestering,
        ).let {
            søknadsbehandlingRepo.lagre(it)
            Pair(sakRepo.hentSak(sakId)!!, it)
        }
    }

    internal fun persisterSøknadsbehandlingIverksattAvslagMedBeregning(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.Iverksatt.Avslag.MedBeregning> {
        return persisterSøknadsbehandlingTilAttesteringAvslagMedBeregning(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.tilIverksatt(
            iverksattAttestering,
        ).let {
            søknadsbehandlingRepo.lagre(it)
            Pair(sakRepo.hentSak(sakId)!!, it)
        }
    }

    fun persisterKlageOpprettet(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
    ): OpprettetKlage {
        return Klage.ny(
            sakId = vedtak.behandling.sakId,
            saksnummer = vedtak.behandling.saksnummer,
            fnr = vedtak.behandling.fnr,
            journalpostId = JournalpostId(value = UUID.randomUUID().toString()),
            oppgaveId = oppgaveId,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerNyKlage"),
            clock = fixedClock,
            datoKlageMottatt = fixedLocalDate,
        ).also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageVilkårsvurdertUtfyltTilVurdering(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
    ): VilkårsvurdertKlage.Utfylt.TilVurdering {
        return persisterKlageOpprettet(vedtak = vedtak).vilkårsvurder(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerUtfyltVilkårsvurdertKlage"),
            vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
                vedtakId = vedtak.id,
                innenforFristen = VilkårsvurderingerTilKlage.Svarord.JA,
                klagesDetPåKonkreteElementerIVedtaket = true,
                erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
                begrunnelse = "enBegrunnelse",
            ),
        ).getOrFail().let {
            if (it !is VilkårsvurdertKlage.Utfylt.TilVurdering) throw IllegalStateException("Forventet en Utfylt(TilVurdering) vilkårsvurdert klage. fikk ${it::class} ved opprettelse av test-data")
            it
        }.also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageVilkårsvurdertUtfyltAvvist(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
    ): VilkårsvurdertKlage.Utfylt.Avvist {
        return persisterKlageOpprettet(vedtak).vilkårsvurder(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerUtfyltAvvistVilkårsvurdertKlage"),
            vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
                vedtakId = vedtak.id,
                innenforFristen = VilkårsvurderingerTilKlage.Svarord.NEI,
                klagesDetPåKonkreteElementerIVedtaket = true,
                erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
                begrunnelse = "en begrunnelse med person opplysninger",
            ),
        ).getOrFail().let {
            if (it !is VilkårsvurdertKlage.Utfylt.Avvist) throw IllegalStateException("Forventet en Utfylt(Avvist) vilkårsvurdert klage. fikk ${it::class} ved opprettelse av test-data")
            it
        }.also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageVilkårsvurdertBekreftetTilVurdering(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
    ): VilkårsvurdertKlage.Bekreftet.TilVurdering {
        return persisterKlageVilkårsvurdertUtfyltTilVurdering(vedtak = vedtak).bekreftVilkårsvurderinger(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerBekreftetVilkårsvurdertKlage"),
        ).getOrFail().let {
            if (it !is VilkårsvurdertKlage.Bekreftet.TilVurdering) throw IllegalStateException("Forventet en Bekreftet(TilVurdering) vilkårsvurdert klage. fikk ${it::class} ved opprettelse av test-data")
            it
        }.also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageVilkårsvurdertBekreftetAvvist(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
    ): VilkårsvurdertKlage.Bekreftet.Avvist {
        return persisterKlageVilkårsvurdertUtfyltAvvist(vedtak).bekreftVilkårsvurderinger(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerBekreftetAvvistVilkårsvurdertKlage"),
        ).getOrFail().let {
            if (it !is VilkårsvurdertKlage.Bekreftet.Avvist) throw IllegalStateException("Forventet en Bekreftet(Avvist) vilkårsvurdert klage. fikk ${it::class} ved opprettelse av test-data")
            it
        }.also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageVurdertPåbegynt(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
    ): VurdertKlage.Påbegynt {
        return persisterKlageVilkårsvurdertBekreftetTilVurdering(vedtak = vedtak).vurder(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerUtfyltVUrdertKlage"),
            vurderinger = VurderingerTilKlage.Påbegynt.create(
                fritekstTilOversendelsesbrev = "Friteksten til brevet er som følge: ",
                vedtaksvurdering = null,
            ),
        ).let {
            if (it !is VurdertKlage.Påbegynt) throw IllegalStateException("Forventet en Påbegynt vurdert klage. fikk ${it::class} ved opprettelse av test data")
            it
        }.also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageVurdertUtfylt(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
    ): VurdertKlage.Utfylt {
        return persisterKlageVurdertPåbegynt(vedtak = vedtak).vurder(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerUtfyltVUrdertKlage"),
            vurderinger = VurderingerTilKlage.Utfylt(
                fritekstTilOversendelsesbrev = "Friteksten til brevet er som følge: ",
                vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold(
                    hjemler = Hjemler.Utfylt.create(
                        nonEmptyListOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4),
                    ),
                ),
            ),
        ).let {
            if (it !is VurdertKlage.Utfylt) throw IllegalStateException("Forventet en Påbegynt vurdert klage. fikk ${it::class} ved opprettelse av test data")
            it
        }.also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageVurdertBekreftet(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
    ): VurdertKlage.Bekreftet {
        return persisterKlageVurdertUtfylt(vedtak = vedtak).bekreftVurderinger(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerBekreftetVurdertKlage"),
        ).also {
            klagePostgresRepo.lagre(it)
        }
    }

    /**
     * underliggende klage: [persisterKlageVurdertBekreftet]
     */
    fun persisterKlageAvsluttet(
        sakId: UUID = UUID.randomUUID(),
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
            sakId = sakId,
        ).second,
        begrunnelse: String = "Begrunnelse for å avslutte klagen.",
        tidspunktAvsluttet: Tidspunkt = fixedTidspunkt,
    ): AvsluttetKlage {
        return persisterKlageVurdertBekreftet(vedtak = vedtak).avslutt(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerSomAvsluttetKlagen"),
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
        ).getOrFail().also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageAvvist(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
        saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerAvvistKlage"),
        fritekstTilBrev: String = "en god, og lang fritekst",
    ): AvvistKlage {
        return persisterKlageVilkårsvurdertBekreftetAvvist(vedtak).leggTilFritekstTilAvvistVedtaksbrev(
            saksbehandler,
            fritekstTilBrev,
        ).also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageTilAttesteringVurdert(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
        oppgaveId: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    ): KlageTilAttestering.Vurdert {
        return persisterKlageVurdertBekreftet(vedtak = vedtak).sendTilAttestering(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerKlageTilAttestering"),
            opprettOppgave = { oppgaveId.right() },
        ).getOrFail().let {
            if (it !is KlageTilAttestering.Vurdert) throw IllegalStateException("Forventet en KlageTilAttestering(TilVurdering). fikk ${it::class} ved opprettelse av test-data")
            it
        }.also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageTilAttesteringAvvist(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
        oppgaveId: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
        saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerAvvistKlageTilAttestering"),
        fritekstTilBrev: String = "en god, og lang fritekst",
    ): KlageTilAttestering.Avvist {
        return persisterKlageAvvist(vedtak, saksbehandler, fritekstTilBrev).sendTilAttestering(
            saksbehandler = saksbehandler,
            opprettOppgave = { oppgaveId.right() },
        ).getOrFail().also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageUnderkjentVurdert(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
        oppgaveId: OppgaveId = OppgaveId("underkjentKlageOppgaveId"),
    ): VurdertKlage.Bekreftet {
        return persisterKlageTilAttesteringVurdert(vedtak = vedtak, oppgaveId = oppgaveId).underkjenn(
            underkjentAttestering = Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant(navIdent = "saksbehandlerUnderkjentKlage"),
                opprettet = fixedTidspunkt,
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "underkjennelseskommentar",
            ),
        ) { oppgaveId.right() }.getOrFail().also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageUnderkjentAvvist(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
        oppgaveId: OppgaveId = OppgaveId("underkjentKlageOppgaveId"),
    ): AvvistKlage {
        return persisterKlageTilAttesteringAvvist(vedtak, oppgaveId).underkjenn(
            underkjentAttestering = Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant(navIdent = "saksbehandlerUnderkjentKlage"),
                opprettet = fixedTidspunkt,
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "underkjennelseskommentar",
            ),
        ) { oppgaveId.right() }.getOrFail().also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageOversendt(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
        oppgaveId: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    ): OversendtKlage {
        return persisterKlageTilAttesteringVurdert(vedtak = vedtak, oppgaveId = oppgaveId).oversend(
            iverksattAttestering = Attestering.Iverksatt(
                attestant = NavIdentBruker.Attestant(navIdent = "saksbehandlerOversendtKlage"),
                opprettet = fixedTidspunkt,
            ),
        ).getOrFail().also {
            klagePostgresRepo.lagre(it)
        }
    }

    fun persisterKlageIverksattAvvist(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
        oppgaveId: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    ): IverksattAvvistKlage {
        return persisterKlageTilAttesteringAvvist(vedtak, oppgaveId).iverksett(
            iverksattAttestering = Attestering.Iverksatt(
                attestant = NavIdentBruker.Attestant(navIdent = "saksbehandlerIverksattAvvistKlage"),
                opprettet = fixedTidspunkt,
            ),
        ).getOrFail().also { klagePostgresRepo.lagre(it) }
    }

    fun persisterUprosessertKlageinstanshendelse(
        id: UUID = UUID.randomUUID(),
        klageId: UUID = UUID.randomUUID(),
        utfall: KlageinstansUtfall = KlageinstansUtfall.STADFESTELSE,
        opprettet: Tidspunkt = fixedTidspunkt,
    ): Pair<UUID, UUID> {
        klageinstanshendelsePostgresRepo.lagre(
            UprosessertKlageinstanshendelse(
                id = id,
                opprettet = opprettet,
                metadata = UprosessertKlageinstanshendelse.Metadata(
                    topic = "klage.behandling-events.v1",
                    hendelseId = UUID.randomUUID().toString(),
                    offset = 0,
                    partisjon = 0,
                    key = "",
                    value = "{\"kildeReferanse\": \"$klageId\", \"utfall\":\"$utfall\"}",
                ),
            ),
        )

        return Pair(id, klageId)
    }

    companion object {
        /** Kaster hvis size != 1 */
        fun Sak.journalførtSøknadMedOppgave(): Søknad.Journalført.MedOppgave.IkkeLukket {
            kastDersomSøknadErUlikEn()
            return søknader.first() as Søknad.Journalført.MedOppgave.IkkeLukket
        }

        /** Kaster hvis size != 1 */
        fun Sak.journalførtSøknad(): Søknad.Journalført.UtenOppgave {
            kastDersomSøknadErUlikEn()
            return søknader.first() as Søknad.Journalført.UtenOppgave
        }

        /** Kaster hvis size != 1 */
        fun Sak.søknadNy(): Søknad.Ny {
            kastDersomSøknadErUlikEn()
            return søknader.first() as Søknad.Ny
        }

        private fun Sak.kastDersomSøknadErUlikEn() {
            if (søknader.size != 1) throw IllegalStateException("Var ferre/fler enn 1 søknad. Testen bør spesifisere dersom fler. Antall: ${søknader.size}")
        }
    }
}

internal fun <T> DataSource.withSession(block: (session: Session) -> T): T {
    return using(sessionOf(this, dbMetricsStub)) { block(it) }
}

internal fun <T> DataSource.withTransaction(block: (session: TransactionalSession) -> T): T {
    return using(sessionOf(this, dbMetricsStub)) { s -> s.transaction { block(it) } }
}
