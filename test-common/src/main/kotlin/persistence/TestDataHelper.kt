package no.nav.su.se.bakover.test.persistence

import arrow.core.NonEmptyList
import arrow.core.Tuple4
import arrow.core.Tuple6
import arrow.core.nonEmptyListOf
import arrow.core.right
import behandling.domain.UnderkjennAttesteringsgrunnBehandling
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import io.kotest.matchers.shouldBe
import kotliquery.using
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.sessionOf
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DomainToQueryParameterMapper
import no.nav.su.se.bakover.dokument.infrastructure.database.DokumentHendelsePostgresRepo
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.Hjemmel
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageId
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.UprosessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.opprettEllerOppdaterRegulering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.fromGjenopptak
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.sak.nyRegulering
import no.nav.su.se.bakover.domain.sak.oppdaterRegulering
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.opprettNySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.oppdaterStønadsperiodeForSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.Revurderingsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagVilkår
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelseFilPostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.attesteringIverksatt
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.beregnetSøknadsbehandling
import no.nav.su.se.bakover.test.eksterneGrunnlag.eksternGrunnlagHentet
import no.nav.su.se.bakover.test.epsFnr
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataMedEpsMedFradrag
import no.nav.su.se.bakover.test.hendelse.defaultHendelseMetadata
import no.nav.su.se.bakover.test.hendelse.jmsHendelseMetadata
import no.nav.su.se.bakover.test.hendelse.oppgaveHendelseMetadata
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlagPåSakHendelse
import no.nav.su.se.bakover.test.kravgrunnlag.råttKravgrunnlagHendelse
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.nyOppgaveHendelse
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.persistence.dokument.PersistertDokumentHendelseTestData
import no.nav.su.se.bakover.test.persistence.tilbakekreving.PersistertTilbakekrevingTestData
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.revurderingUnderkjent
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.simulering.simulering
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.simulertSøknadsbehandling
import no.nav.su.se.bakover.test.skatt.nySkattedokumentGenerert
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknad.journalpostIdSøknad
import no.nav.su.se.bakover.test.søknad.oppgaveIdSøknad
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.tilAttesteringSøknadsbehandling
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.test.underkjentSøknadsbehandling
import no.nav.su.se.bakover.test.utbetaling.kvittering
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.veileder
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderingerRevurderingInnvilget
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandling
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagDetaljerPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagStatusendringPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.rått.RåttKravgrunnlagHendelse
import tilbakekreving.infrastructure.repo.kravgrunnlag.KravgrunnlagPostgresRepo
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlag
import tilbakekreving.presentation.consumer.KravgrunnlagDtoMapper
import vedtak.domain.Stønadsvedtak
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.common.domain.Vilkår
import vilkår.common.domain.grunnlag.Grunnlag
import vilkår.personligOppmøtevilkårAvslag
import vilkår.skatt.domain.Skattedokument
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import vilkår.vurderinger.domain.Grunnlagsdata
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.Simuleringsresultat
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalingslinje
import java.time.Clock
import java.time.LocalDate
import java.util.LinkedList
import java.util.UUID
import javax.sql.DataSource

class TestDataHelper(
    val dataSource: DataSource,
    val dbMetrics: DbMetrics = dbMetricsStub,
    val clock: Clock = TikkendeKlokke(),
    satsFactory: SatsFactoryForSupplerendeStønad = satsFactoryTest,
    råttKravgrunnlagMapper: MapRåttKravgrunnlag = KravgrunnlagDtoMapper::toKravgrunnlag,
) {
    val sessionFactory: PostgresSessionFactory =
        PostgresSessionFactory(dataSource, dbMetrics, sessionCounterStub, listOf(DomainToQueryParameterMapper))

    val databaseRepos = DatabaseBuilder.build(
        embeddedDatasource = dataSource,
        dbMetrics = dbMetrics,
        clock = clock,
        satsFactory = satsFactory,
        råttKravgrunnlagMapper = råttKravgrunnlagMapper,
    )
    val avstemmingRepo = databaseRepos.avstemming
    val dokumentRepo = databaseRepos.dokumentRepo
    val sakRepo = databaseRepos.sak
    val vedtakRepo = databaseRepos.vedtakRepo
    val klagePostgresRepo = databaseRepos.klageRepo
    val klageinstanshendelsePostgresRepo = databaseRepos.klageinstanshendelseRepo
    val nøkkeltallRepo = databaseRepos.nøkkeltallRepo
    val personRepo = databaseRepos.person
    val personhendelseRepo = databaseRepos.personhendelseRepo
    val reguleringRepo = databaseRepos.reguleringRepo
    val revurderingRepo = databaseRepos.revurderingRepo
    val søknadRepo = databaseRepos.søknad
    val søknadsbehandlingRepo = databaseRepos.søknadsbehandling
    val utbetalingRepo = databaseRepos.utbetaling
    val institusjonsoppholdHendelseRepo = databaseRepos.institusjonsoppholdHendelseRepo
    val oppgaveHendelseRepo = databaseRepos.oppgaveHendelseRepo
    val hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo
    val hendelseRepo = HendelsePostgresRepo(sessionFactory = sessionFactory, dbMetrics = dbMetrics)
    val kravgrunnlagPostgresRepo = KravgrunnlagPostgresRepo(hendelseRepo, hendelsekonsumenterRepo)

    val dokumentHendelseRepo =
        DokumentHendelsePostgresRepo(hendelseRepo, HendelseFilPostgresRepo(sessionFactory), sessionFactory)

    val tilbakekreving = PersistertTilbakekrevingTestData(
        databaseRepos = databaseRepos,
        sessionFactory = sessionFactory,
        hendelseRepo = hendelseRepo,
        kravgrunnlagPostgresRepo = kravgrunnlagPostgresRepo,
        dokumentHendelseRepo = dokumentHendelseRepo,
        dbMetrics = dbMetrics,
        testDataHelper = this,
    )

    val dokumentHendelse = PersistertDokumentHendelseTestData(
        hendelseRepo = hendelseRepo,
        testDataHelper = this,
    )

    /**
     * Oppretter og persisterer en ny sak (dersom den ikke finnes fra før) med søknad med tomt søknadsinnhold.
     * Søknaden er uten journalføring og oppgave.
     */
    fun persisterSakMedSøknadUtenJournalføringOgOppgave(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        fnr: Fnr = Fnr.generer(),
        søknadInnhold: SøknadInnhold = søknadinnholdUføre(personopplysninger = Personopplysninger(fnr)),
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
            databaseRepos.sak.opprettSak(it)
        }
    }

    /**
     * Oppretter og persisterer en ny søknad på en eksisterende sak med tomt søknadsinnhold.
     * Søknaden er uten journalføring og oppgave.
     */
    fun persisterSøknadUtenJournalføringOgOppgavePåEksisterendeSak(
        sakId: UUID,
        søknadId: UUID = UUID.randomUUID(),
        fnr: Fnr,
        søknadInnhold: SøknadInnhold = søknadinnholdUføre(personopplysninger = Personopplysninger(fnr)),
        identBruker: NavIdentBruker = veileder,
    ): Søknad.Ny {
        return Søknad.Ny(
            sakId = sakId,
            id = søknadId,
            søknadInnhold = søknadInnhold,
            opprettet = Tidspunkt.now(clock),
            innsendtAv = identBruker,
        ).also { databaseRepos.søknad.opprettSøknad(it) }
    }

    /**
     * Oppretter og persisterer en ny lukket søknad på en eksisterende sak med tomt søknadsinnhold.
     * Søknaden er journalført og har oppgave (vi skal ikke kunne lukke en søknad før den har blitt journalført med oppgave).
     */
    fun persisterLukketJournalførtSøknadMedOppgave(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        fnr: Fnr = Fnr.generer(),
        journalpostId: JournalpostId = journalpostIdSøknad,
        søknadInnhold: SøknadsinnholdUføre = søknadinnholdUføre(personopplysninger = Personopplysninger(fnr)),
    ): Søknad.Journalført.MedOppgave.Lukket {
        return persisterJournalførtSøknadMedOppgave(
            sakId = sakId,
            søknadId = søknadId,
            fnr = fnr,
            journalpostId = journalpostId,
            søknadInnhold = søknadInnhold,
        ).second.let {
            it.lukk(
                trekkSøknad(
                    søknadId = søknadId,
                    lukketTidspunkt = Tidspunkt.now(clock),
                ),
            ).also { lukketSøknad ->
                databaseRepos.søknad.lukkSøknad(lukketSøknad)
            }
        }
    }

    fun persisterSakOgJournalførtSøknadUtenOppgave(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        fnr: Fnr = Fnr.generer(),
        journalpostId: JournalpostId = journalpostIdSøknad,
        søknadInnhold: SøknadInnhold = søknadinnholdUføre(personopplysninger = Personopplysninger(fnr)),
    ): Pair<Sak, Søknad.Journalført.UtenOppgave> {
        val nySak: NySak = persisterSakMedSøknadUtenJournalføringOgOppgave(
            fnr = fnr,
            sakId = sakId,
            søknadId = søknadId,
            søknadInnhold = søknadInnhold,
        )
        val journalførtSøknad = nySak.søknad.journalfør(journalpostId).also { journalførtSøknad ->
            databaseRepos.søknad.oppdaterjournalpostId(journalførtSøknad)
        }
        return Pair(
            databaseRepos.sak.hentSak(nySak.id)
                ?: throw IllegalStateException("Fant ikke sak rett etter vi opprettet den."),
            journalførtSøknad,
        )
    }

    fun persisterJournalførtSøknadUtenOppgaveForEksisterendeSak(
        sakId: UUID,
        søknadId: UUID = UUID.randomUUID(),
        journalpostId: JournalpostId = journalpostIdSøknad,
        fnr: Fnr,
        søknadInnhold: SøknadInnhold = søknadinnholdUføre(personopplysninger = Personopplysninger(fnr)),
    ): Søknad.Journalført.UtenOppgave {
        return persisterSøknadUtenJournalføringOgOppgavePåEksisterendeSak(
            sakId = sakId,
            søknadId = søknadId,
            fnr = fnr,
            søknadInnhold = søknadInnhold,
        ).journalfør(journalpostId).also {
            databaseRepos.søknad.oppdaterjournalpostId(it)
        }
    }

    /**
     * Oppretter ikke ny sak dersom den finnes fra før.
     */
    fun persisterJournalførtSøknadMedOppgave(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        fnr: Fnr = Fnr.generer(),
        oppgaveId: OppgaveId = oppgaveIdSøknad,
        journalpostId: JournalpostId = journalpostIdSøknad,
        søknadInnhold: SøknadInnhold = søknadinnholdUføre(personopplysninger = Personopplysninger(fnr)),
    ): Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> {
        return databaseRepos.sak.hentSak(sakId).let {
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
                        fnr = fnr,
                        søknadInnhold = søknadInnhold,
                    ),
                )
            }
        }.let {
            val medOppgave = it.second.medOppgave(oppgaveId).also { søknad ->
                databaseRepos.søknad.oppdaterOppgaveId(søknad)
            }
            Pair(
                databaseRepos.sak.hentSak(it.first.id)
                    ?: throw IllegalStateException("Fant ikke sak rett etter vi opprettet den."),
                medOppgave,
            )
        }
    }

    fun persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Triple<Sak, IverksattSøknadsbehandling, Stønadsvedtak> = { (sak, søknad) ->
            iverksattSøknadsbehandlingUføre(
                clock = clock,
                sakOgSøknad = sak to søknad,
                stønadsperiode = stønadsperiode,
            )
        },
    ): Triple<Sak, VedtakInnvilgetSøknadsbehandling, Utbetaling.OversendtUtbetaling.MedKvittering> {
        // I dette tilfellet persisterer vi først utbetalingen uten kvittering, for så å persistere kvitteringen i et eget steg.
        return persisterSøknadsbehandlingIverksatt(
            sakOgSøknad,
            kvittering = null,
        ) { søknadsbehandling(it) }.let { (sak, _, vedtak) ->
            (vedtak as VedtakInnvilgetSøknadsbehandling).let {
                databaseRepos.utbetaling.hentOversendtUtbetalingForUtbetalingId(vedtak.utbetalingId, null)
                    .let { utbetalingUtenKvittering ->
                        (utbetalingUtenKvittering as Utbetaling.OversendtUtbetaling.MedKvittering).let { utbetalingMedKvittering ->
                            databaseRepos.utbetaling.oppdaterMedKvittering(utbetalingMedKvittering, null)
                            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                                Triple(
                                    persistertSak!!,
                                    persistertSak.vedtakListe.single { it.id == vedtak.id } as VedtakInnvilgetSøknadsbehandling,
                                    persistertSak.utbetalinger.single { it.id == utbetalingUtenKvittering.id } as Utbetaling.OversendtUtbetaling.MedKvittering,
                                )
                            }
                        }
                    }
            }
        }
    }

    /**
     * Persisterer:
     * 1. [Utbetaling.OversendtUtbetaling]:
     *    1. [Utbetaling.OversendtUtbetaling.UtenKvittering]
     *    1. [Utbetaling.OversendtUtbetaling.MedKvittering]
     * 1. [VedtakInnvilgetRevurdering]
     */
    fun persisterVedtakMedInnvilgetRevurderingOgOversendtUtbetalingMedKvittering(
        sakOgRevurdering: Tuple4<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse> = persisterIverksattRevurdering().let {
            Tuple4(it.first, it.second, it.third, it.fourth)
        },
    ): Pair<VedtakInnvilgetRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering> {
        return sakOgRevurdering.let { (sak, _, utbetaling, vedtak) ->
            databaseRepos.utbetaling.oppdaterMedKvittering(utbetaling, null)
            databaseRepos.sak.hentSak(sak.id)!!.let { persistertSak ->
                persistertSak.vedtakListe.single { it.id == vedtak.id } as VedtakInnvilgetRevurdering to persistertSak.utbetalinger.single { it.id == utbetaling.id } as Utbetaling.OversendtUtbetaling.MedKvittering
            }
        }
    }

    fun persisterVedtakForKlageIverksattAvvist(
        klage: IverksattAvvistKlage = persisterKlageIverksattAvvist(),
    ): Klagevedtak.Avvist {
        return Klagevedtak.Avvist.fromIverksattAvvistKlage(klage, clock).also {
            databaseRepos.vedtakRepo.lagre(it)
        }
    }

    /**
     * TODO jah: På sikt burde denne muligens først persistere en stans, men føler det er litt out of scope for denne PR.
     */
    fun persisterVedtakForGjenopptak(
        gjenopptak: GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse = persisterGjenopptakAvYtelseIverksatt(),
        periode: Periode = stønadsperiode2021.periode,
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.utbetaling.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinjeNy(periode = periode)),
        utbetalingId: UUID30 = UUID30.randomUUID(),
    ): VedtakGjenopptakAvYtelse {
        oversendtUtbetalingUtenKvittering(
            id = utbetalingId,
            fnr = gjenopptak.fnr,
            sakId = gjenopptak.sakId,
            saksnummer = gjenopptak.saksnummer,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = utbetalingslinjer,
        ).let {
            databaseRepos.utbetaling.opprettUtbetaling(it, sessionFactory.newTransactionContext())
            it.toKvittertUtbetaling(kvittering()).also { utbetalingMedKvittering ->
                databaseRepos.utbetaling.oppdaterMedKvittering(utbetalingMedKvittering, null)
            }
        }
        return VedtakSomKanRevurderes.fromGjenopptak(gjenopptak, utbetalingId, clock).also {
            databaseRepos.vedtakRepo.lagre(it)
        }
    }

    fun persisterReguleringOpprettet(
        fraOgMedMåned: Måned = mai(2021),
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Triple<Sak, IverksattSøknadsbehandling, Stønadsvedtak> = { (sak, søknad) ->
            iverksattSøknadsbehandlingUføre(
                clock = clock,
                sakOgSøknad = sak to søknad,
            )
        },
    ): Pair<Sak, OpprettetRegulering> {
        return persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
            sakOgSøknad = sakOgSøknad,
            søknadsbehandling = søknadsbehandling,
        ).first.let { sak ->
            sak.opprettEllerOppdaterRegulering(
                fraOgMedMåned = fraOgMedMåned,
                clock = clock,
            ).getOrFail().let {
                databaseRepos.reguleringRepo.lagre(it)
                sak.nyRegulering(it) to it
            }
        }
    }

    fun persisterReguleringIverksatt(
        fraOgMedMåned: Måned = mai(2021),
    ): Pair<Sak, IverksattRegulering> {
        return persisterReguleringOpprettet(
            fraOgMedMåned = fraOgMedMåned,
        ).let { (sak, regulering) ->
            regulering.beregn(
                satsFactory = satsFactoryTestPåDato(påDato = LocalDate.now(clock)),
                begrunnelse = "Begrunnelse",
                clock = clock,
            ).getOrFail().let { beregnet ->
                beregnet.simuler(
                    simuler = { _, _ ->
                        simulerUtbetaling(
                            sak = sak,
                            regulering = beregnet,
                            behandler = beregnet.saksbehandler,
                            clock = clock,
                        ).getOrFail().let {
                            Simuleringsresultat.UtenForskjeller(it)
                        }.right()
                    },
                ).getOrFail().first.tilIverksatt().let { iverksattAttestering ->
                    databaseRepos.reguleringRepo.lagre(iverksattAttestering)
                    sak.oppdaterRegulering(iverksattAttestering) to iverksattAttestering
                }
            }
        }
    }

    fun persisterRevurderingOpprettet(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
        sakOgRevurdering: (sakOgVedtak: Pair<Sak, VedtakEndringIYtelse>) -> Pair<Sak, OpprettetRevurdering> = {
            opprettetRevurdering(sakOgVedtakSomKanRevurderes = it, clock = clock)
        },
    ): Pair<Sak, OpprettetRevurdering> {
        return sakOgRevurdering(sakOgVedtak).let { (sak, revurdering) ->
            databaseRepos.revurderingRepo.lagre(revurdering)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.revurderinger.single { it.id == revurdering.id } as OpprettetRevurdering
            }
        }
    }

    fun persisterBeregnetRevurdering(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
        sakOgRevurdering: (sakOgVedtak: Pair<Sak, VedtakEndringIYtelse>) -> Pair<Sak, BeregnetRevurdering> = {
            beregnetRevurdering(sakOgVedtakSomKanRevurderes = it, clock = clock)
        },
    ): Pair<Sak, BeregnetRevurdering> {
        return sakOgRevurdering(sakOgVedtak).let { (sak, revurdering) ->
            databaseRepos.revurderingRepo.lagre(revurdering)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.revurderinger.single { it.id == revurdering.id } as BeregnetRevurdering
            }
        }
    }

    fun persisterSimulertRevurdering(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
        sakOgRevurdering: (sakOgVedtak: Pair<Sak, VedtakEndringIYtelse>) -> Pair<Sak, SimulertRevurdering> = {
            simulertRevurdering(sakOgVedtakSomKanRevurderes = it, clock = clock)
        },
    ): Pair<Sak, SimulertRevurdering> {
        return sakOgRevurdering(sakOgVedtak).let { (sak, revurdering) ->
            databaseRepos.revurderingRepo.lagre(revurdering)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.revurderinger.single { it.id == revurdering.id } as SimulertRevurdering
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun persisterRevurderingTilAttestering(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
        sakOgRevurdering: (sakOgVedtak: Pair<Sak, VedtakEndringIYtelse>) -> Pair<Sak, RevurderingTilAttestering> = {
            revurderingTilAttestering(sakOgVedtakSomKanRevurderes = it, clock = clock)
        },
    ): Pair<Sak, RevurderingTilAttestering> {
        return sakOgRevurdering(sakOgVedtak).let { (sak, revurdering) ->
            databaseRepos.revurderingRepo.lagre(revurdering)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.revurderinger.single { it.id == revurdering.id } as RevurderingTilAttestering
            }
        }
    }

    /**
     * Oppretter sak, søknad og søknadsbehandlingsvedtak dersom dette ikke sendes eksplisitt inn.
     * @param stønadsperiode Ignoreres dersom [sakOgVedtak] sendes inn.
     * @param revurderingsperiode Ignoreres dersom [sakOgRevurdering] sendes inn.
     * @param sakOgVedtak Dersom denne sendes inn, bør også [revurderingsperiode] sendes inn, hvis ikke defaulter den til 2021.
     * @param grunnlagsdataOverrides Gjelder kun for revurdering. Ignoreres dersom [sakOgRevurdering] sendes inn.
     */
    fun persisterIverksattRevurdering(
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        revurderingsperiode: Periode = stønadsperiode.periode,
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
            stønadsperiode = stønadsperiode,
        ).let { (sak, vedtak, _) ->
            sak to vedtak
        },
        nesteKravgrunnlagVersjon: Hendelsesversjon = Hendelsesversjon.ny(),
        grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
        vilkårOverrides: List<Vilkår> = emptyList(),
        sakOgRevurdering: (sakOgVedtak: Pair<Sak, VedtakEndringIYtelse>) -> Tuple4<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling, Revurderingsvedtak> = {
            iverksattRevurdering(
                clock = clock,
                sakOgVedtakSomKanRevurderes = it,
                revurderingsperiode = revurderingsperiode,
                grunnlagsdataOverrides = grunnlagsdataOverrides,
                vilkårOverrides = vilkårOverrides,
            )
        },
    ): Tuple6<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, RåttKravgrunnlagHendelse?, KravgrunnlagDetaljerPåSakHendelse?> {
        return sakOgRevurdering(sakOgVedtak).let { (sak, revurdering, utbetaling, vedtak) ->
            databaseRepos.revurderingRepo.lagre(revurdering)
            databaseRepos.utbetaling.opprettUtbetaling(utbetaling)
            databaseRepos.vedtakRepo.lagre(vedtak)
            val (råttKravgrunnlagHendelse, kravgrunnlagPåSakHendelse) = sak.uteståendeKravgrunnlag?.let {
                val (råttKravgrunnlagHendelse, kravgrunnlagPåSakHendelse) = persisterRåttKravgrunnlagOgKravgrunnlagKnyttetTilSak(
                    it,
                    sak,
                    nesteKravgrunnlagVersjon,
                )
                råttKravgrunnlagHendelse to kravgrunnlagPåSakHendelse
            } ?: Pair(null, null)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                sak.uteståendeKravgrunnlag?.also {
                    sak.uteståendeKravgrunnlag shouldBe persistertSak!!.uteståendeKravgrunnlag
                }
                Tuple6(
                    first = persistertSak!!,
                    second = persistertSak.revurderinger.single { it.id == revurdering.id } as IverksattRevurdering,
                    third = persistertSak.utbetalinger.single { it.id == utbetaling.id } as Utbetaling.OversendtUtbetaling.MedKvittering,
                    fourth = persistertSak.vedtakListe.single { it.id == vedtak.id } as VedtakEndringIYtelse,
                    fifth = råttKravgrunnlagHendelse,
                    sixth = kravgrunnlagPåSakHendelse,
                )
            }
        }
    }

    fun emulerViMottarKravgrunnlagstatusendring(
        sak: Sak,
        status: Kravgrunnlagstatus = Kravgrunnlagstatus.Sperret,
    ): Pair<RåttKravgrunnlagHendelse, KravgrunnlagStatusendringPåSakHendelse> {
        val råttKravgrunnlagHendelse = råttKravgrunnlagHendelse(
            clock = clock,
        )
        kravgrunnlagPostgresRepo.lagreRåttKravgrunnlagHendelse(råttKravgrunnlagHendelse, jmsHendelseMetadata())
        val eksternTidspunkt = Tidspunkt.now(clock)
        val hendelsestidspunkt = Tidspunkt.now(clock)
        val kravgrunnlagPåSakHendelse = KravgrunnlagStatusendringPåSakHendelse(
            hendelseId = HendelseId.generer(),
            versjon = sak.versjon.inc(),
            sakId = sak.id,
            hendelsestidspunkt = hendelsestidspunkt,
            tidligereHendelseId = råttKravgrunnlagHendelse.hendelseId,
            saksnummer = sak.saksnummer,
            eksternVedtakId = sak.uteståendeKravgrunnlag!!.eksternVedtakId,
            status = status,
            eksternTidspunkt = eksternTidspunkt,
        )
        kravgrunnlagPostgresRepo.lagreKravgrunnlagPåSakHendelse(
            hendelse = kravgrunnlagPåSakHendelse,
            meta = defaultHendelseMetadata(),
        )
        return råttKravgrunnlagHendelse to kravgrunnlagPåSakHendelse
    }

    private fun persisterRåttKravgrunnlagOgKravgrunnlagKnyttetTilSak(
        kravgrunnlag: Kravgrunnlag,
        sak: Sak,
        nesteKravgrunnlagVersjon: Hendelsesversjon,
    ): Pair<RåttKravgrunnlagHendelse, KravgrunnlagDetaljerPåSakHendelse> {
        // XMLen her er tom, men det går bra siden vi lagrer knytt kravgrunnlag mot sak hendelsen selv.
        val råttKravgrunnlagHendelse = råttKravgrunnlagHendelse(
            clock = clock,
        )
        kravgrunnlagPostgresRepo.lagreRåttKravgrunnlagHendelse(råttKravgrunnlagHendelse, jmsHendelseMetadata())
        val kravgrunnlagPåSakHendelse = kravgrunnlagPåSakHendelse(
            kravgrunnlag = kravgrunnlag,
            sakId = sak.id,
            tidligereHendelseId = råttKravgrunnlagHendelse.hendelseId,
            // Siden vi genererer en ny hendelseId på kravgrunnlaget som ligger på saken, må vi bruke den her.
            hendelseId = kravgrunnlag.hendelseId,
            versjon = nesteKravgrunnlagVersjon,
            clock = clock,
        )
        kravgrunnlagPostgresRepo.lagreKravgrunnlagPåSakHendelse(
            hendelse = kravgrunnlagPåSakHendelse,
            meta = defaultHendelseMetadata(),
        )
        return Pair(råttKravgrunnlagHendelse, kravgrunnlagPåSakHendelse)
    }

    private fun persisterUnderkjentRevurdering(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
        sakOgRevurdering: (sakOgVedtak: Pair<Sak, VedtakEndringIYtelse>) -> Pair<Sak, UnderkjentRevurdering> = {
            revurderingUnderkjent(sakOgVedtakSomKanRevurderes = it, clock = clock)
        },
    ): Pair<Sak, UnderkjentRevurdering> {
        return sakOgRevurdering(sakOgVedtak).let { (sak, revurdering) ->
            databaseRepos.revurderingRepo.lagre(revurdering)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.revurderinger.single { it.id == revurdering.id } as UnderkjentRevurdering
            }
        }
    }

    fun persisterRevurderingBeregnetOpphørt(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Pair<Sak, BeregnetRevurdering.Opphørt> {
        return persisterBeregnetRevurdering(sakOgVedtak) { (sak, vedtak) ->
            beregnetRevurdering(
                sakOgVedtakSomKanRevurderes = sak to vedtak,
                clock = clock,
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
            )
        }.let { (sak, revurdering) ->
            sak to revurdering as BeregnetRevurdering.Opphørt
        }
    }

    fun persisterRevurderingSimulertInnvilget(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Pair<Sak, SimulertRevurdering.Innvilget> {
        return persisterSimulertRevurdering(sakOgVedtak).let { (sak, revurdering) ->
            sak to revurdering as SimulertRevurdering.Innvilget
        }
    }

    fun persisterRevurderingSimulertOpphørt(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
        revurderingsperiode: Periode = år(2021),
    ): Pair<Sak, SimulertRevurdering.Opphørt> {
        return persisterSimulertRevurdering(sakOgVedtak) { (sak, vedtak) ->
            simulertRevurdering(
                sakOgVedtakSomKanRevurderes = sak to vedtak,
                clock = clock,
                vilkårOverrides = listOf(
                    avslåttUførevilkårUtenGrunnlag(
                        periode = revurderingsperiode,
                        opprettet = Tidspunkt.now(clock),
                    ),
                ),
                revurderingsperiode = revurderingsperiode,
            )
        }.let { (sak, revurdering) ->
            sak to revurdering as SimulertRevurdering.Opphørt
        }
    }

    fun persisterRevurderingTilAttesteringInnvilget(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Pair<Sak, RevurderingTilAttestering.Innvilget> {
        return persisterRevurderingTilAttestering(sakOgVedtak).let { (sak, revurdering) ->
            sak to revurdering as RevurderingTilAttestering.Innvilget
        }
    }

    fun persisterRevurderingTilAttesteringOpphørt(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Pair<Sak, RevurderingTilAttestering.Opphørt> {
        return persisterRevurderingTilAttestering(sakOgVedtak) { (sak, vedtak) ->
            revurderingTilAttestering(
                sakOgVedtakSomKanRevurderes = sak to vedtak,
                clock = clock,
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
            )
        }.let { (sak, revurdering) ->
            sak to revurdering as RevurderingTilAttestering.Opphørt
        }
    }

    /**
     * Oppretter sak, søknad og søknadsbehandlingsvedtak dersom dette ikke sendes eksplisitt inn.
     */
    fun persisterRevurderingIverksattInnvilget(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Tuple4<Sak, IverksattRevurdering.Innvilget, Utbetaling, VedtakInnvilgetRevurdering> {
        return persisterIverksattRevurdering(sakOgVedtak = sakOgVedtak).let {
            Tuple4(
                it.first,
                it.second as IverksattRevurdering.Innvilget,
                it.third,
                it.fourth as VedtakInnvilgetRevurdering,
            )
        }
    }

    fun persisterRevurderingIverksattOpphørt(
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        periode: Periode = stønadsperiode2021.periode,
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
            stønadsperiode = stønadsperiode,
        ).let { (sak, vedtak, _) ->
            sak to vedtak
        },
        nesteKravgrunnlagVersjon: Hendelsesversjon = Hendelsesversjon.ny(),
    ): Tuple6<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, RåttKravgrunnlagHendelse?, KravgrunnlagDetaljerPåSakHendelse?> {
        return persisterIverksattRevurdering(
            sakOgVedtak = sakOgVedtak,
            nesteKravgrunnlagVersjon = nesteKravgrunnlagVersjon,
        ) { (sak, vedtak) ->
            iverksattRevurdering(
                clock = clock,
                sakOgVedtakSomKanRevurderes = sak to vedtak,
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag(periode = periode)),
                revurderingsperiode = periode,
            )
        }
    }

    fun persisterRevurderingUnderkjentInnvilget(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Pair<Sak, UnderkjentRevurdering.Innvilget> {
        return persisterUnderkjentRevurdering(sakOgVedtak).let { (sak, revurdering) ->
            sak to revurdering as UnderkjentRevurdering.Innvilget
        }
    }

    /**
     * Baseres på [persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling] og [persisterRevurderingOpprettet]
     */
    fun persisterRevurderingAvsluttet(): Pair<Sak, AvsluttetRevurdering> {
        val (sak, vedtak, _) = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
        return persisterRevurderingOpprettet(
            sakOgVedtak = sak to vedtak,
        ).let { (_, opprettet) ->
            opprettet.avslutt(
                begrunnelse = "",
                brevvalg = null,
                tidspunktAvsluttet = Tidspunkt.now(clock),
                avsluttetAv = saksbehandler,
            ).getOrFail().also { databaseRepos.revurderingRepo.lagre(it) }
        }.let {
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                (persistertSak!!.revurderinger.single { it.id == it.id } as AvsluttetRevurdering) shouldBe it
                persistertSak to it
            }
        }
    }

    fun persisterSimulertStansAvYtelse(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Pair<Sak, StansAvYtelseRevurdering.SimulertStansAvYtelse> {
        return simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            sakOgVedtakSomKanRevurderes = sakOgVedtak.first to sakOgVedtak.second,
            clock = clock,
        ).let { (sak, revurdering) ->
            databaseRepos.revurderingRepo.lagre(revurdering)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.revurderinger.single { it.id == revurdering.id } as StansAvYtelseRevurdering.SimulertStansAvYtelse
            }
        }
    }

    fun persisterIverksattStansOgVedtak(
        sakOgVedtak: Pair<Sak, VedtakEndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Pair<Sak, VedtakStansAvYtelse> {
        return vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            sakOgVedtakSomKanRevurderes = sakOgVedtak.first to sakOgVedtak.second,
            clock = clock,
        ).let { (sak, vedtak, utbetaling) ->
            databaseRepos.revurderingRepo.lagre(vedtak.behandling)
            databaseRepos.utbetaling.opprettUtbetaling(utbetaling)
            databaseRepos.vedtakRepo.lagre(vedtak)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to vedtak
            }
        }
    }

    fun persisterGjenopptakAvYtelseSimulert(
        id: RevurderingId = RevurderingId.generer(),
        opprettet: Tidspunkt = Tidspunkt.now(clock),
        oppdatert: Tidspunkt = opprettet,
        periode: Periode = Periode.create(
            stønadsperiode2021.periode.fraOgMed.plusMonths(1),
            stønadsperiode2021.periode.tilOgMed,
        ),
        grunnlagsdata: Grunnlagsdata = grunnlagsdataMedEpsMedFradrag(periode, epsFnr),
        vilkårsvurderinger: VilkårsvurderingerRevurdering.Uføre = vilkårsvurderingerRevurderingInnvilget(periode = periode),
        tilRevurdering: VedtakSomKanRevurderes = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
        vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis = VedtakSomRevurderesMånedsvis(
            periode.måneder().associateWith { tilRevurdering.id },
        ),
        simulering: Simulering = simulering(fnr = Fnr.generer()),
        revurderingsårsak: Revurderingsårsak = Revurderingsårsak(
            Revurderingsårsak.Årsak.DØDSFALL,
            Revurderingsårsak.Begrunnelse.create("begrunnelse"),
        ),
    ): GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse {
        // TODO jah: Dette vil ikke ligne på produksjonskoden.
        return GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
            id = id,
            opprettet = opprettet,
            oppdatert = oppdatert,
            periode = periode,
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerRevurdering(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
            ),
            tilRevurdering = tilRevurdering.id,
            vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
            saksbehandler = saksbehandler,
            simulering = simulering,
            revurderingsårsak = revurderingsårsak,
            sakinfo = tilRevurdering.sakinfo(),
        ).also {
            databaseRepos.revurderingRepo.lagre(it)
        }
    }

    /**
     * @param tilRevurdering default: [persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling]
     */
    fun persisterGjenopptakAvYtelseIverksatt(
        id: RevurderingId = RevurderingId.generer(),
        opprettet: Tidspunkt = Tidspunkt.now(clock),
        oppdatert: Tidspunkt = opprettet,
        periode: Periode = Periode.create(
            stønadsperiode2021.periode.fraOgMed.plusMonths(1),
            stønadsperiode2021.periode.tilOgMed,
        ),
        grunnlagsdata: Grunnlagsdata = grunnlagsdataMedEpsMedFradrag(periode, epsFnr),
        vilkårsvurderinger: VilkårsvurderingerRevurdering.Uføre = vilkårsvurderingerRevurderingInnvilget(periode = periode),
        tilRevurdering: VedtakSomKanRevurderes = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
        vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis = VedtakSomRevurderesMånedsvis(
            periode.måneder().associateWith { tilRevurdering.id },
        ),
        simulering: Simulering = simulering(fnr = Fnr.generer()),
        revurderingsårsak: Revurderingsårsak = Revurderingsårsak(
            Revurderingsårsak.Årsak.DØDSFALL,
            Revurderingsårsak.Begrunnelse.create("begrunnelse"),
        ),
    ): GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse {
        return persisterGjenopptakAvYtelseSimulert(
            id = id,
            opprettet = opprettet,
            oppdatert = oppdatert,
            periode = periode,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            tilRevurdering = tilRevurdering,
            vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
            simulering = simulering,
            revurderingsårsak = revurderingsårsak,
        ).iverksett(attesteringIverksatt()).getOrFail().also {
            databaseRepos.revurderingRepo.lagre(it)
        }
    }

    /**
     * Underliggende søknadsbehahandling: [VilkårsvurdertSøknadsbehandling.Uavklart]
     */
    fun persisterSøknadsbehandlingAvsluttet(
        id: SøknadsbehandlingId = SøknadsbehandlingId.generer(),
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, LukketSøknadsbehandling> {
        return persisternySøknadsbehandlingMedStønadsperiode(
            id = id,
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.let {
            it.lukkSøknadsbehandlingOgSøknad(
                trekkSøknad(
                    søknadId = søknadId,
                    lukketTidspunkt = Tidspunkt.now(clock),
                ),
            ).getOrFail().let { lukketSøknadsbehandling ->
                databaseRepos.søknadsbehandling.lagre(lukketSøknadsbehandling)
                databaseRepos.søknad.lukkSøknad(lukketSøknadsbehandling.søknad)
                Pair(databaseRepos.sak.hentSak(sakId)!!, lukketSøknadsbehandling)
            }
        }
    }

    fun persisterIverksattSøknadsbehandlingAvslag(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Triple<Sak, IverksattSøknadsbehandling, Stønadsvedtak> = { (sak, søknad) ->
            iverksattSøknadsbehandlingUføre(
                clock = clock,
                sakInfo = SakInfo(
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    fnr = sak.fnr,
                    type = sak.type,
                ),
                sakOgSøknad = sak to søknad,
                customVilkår = listOf(
                    personligOppmøtevilkårAvslag(),
                ),
            )
        },
    ): Triple<Sak, IverksattSøknadsbehandling, Avslagsvedtak> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling, vedtak) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            databaseRepos.vedtakRepo.lagre(vedtak)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                Triple(
                    persistertSak!!,
                    persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as IverksattSøknadsbehandling.Avslag,
                    persistertSak.vedtakListe.single { it.id == vedtak.id } as Avslagsvedtak,
                )
            }
        }
    }

    fun persisterSøknadsbehandlingVilkårsvurdert(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, VilkårsvurdertSøknadsbehandling> = { (sak, søknad) ->
            vilkårsvurdertSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        },
    ): Pair<Sak, VilkårsvurdertSøknadsbehandling> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as VilkårsvurdertSøknadsbehandling
            }
        }
    }

    fun persisternySøknadsbehandlingMedStønadsperiode(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart> = { (sak, søknad) ->
            nySøknadsbehandlingMedStønadsperiode(
                sakOgSøknad = sak to søknad,
                clock = clock,
            ).let {
                it.first to it.second
            }
        },
    ): Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart> {
        return persisterSøknadsbehandlingVilkårsvurdert(sakOgSøknad) { søknadsbehandling(it) }.let { (sak, vilkårsvurdertSøknadsbehandling) ->
            sak to vilkårsvurdertSøknadsbehandling as VilkårsvurdertSøknadsbehandling.Uavklart
        }
    }

    fun persisterSøknadsbehandlingVilkårsvurdertInnvilget(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    ): Pair<Sak, VilkårsvurdertSøknadsbehandling.Innvilget> {
        return persisterSøknadsbehandlingVilkårsvurdert(sakOgSøknad) { (sak, søknad) ->
            vilkårsvurdertSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                eksterneGrunnlag = eksterneGrunnlag,
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as VilkårsvurdertSøknadsbehandling.Innvilget
        }
    }

    fun persisterSøknadsbehandlingVilkårsvurdertAvslag(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, VilkårsvurdertSøknadsbehandling.Avslag> {
        return persisterSøknadsbehandlingVilkårsvurdert(sakOgSøknad) { (sak, søknad) ->
            vilkårsvurdertSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            )
        }.let { (sak, revurdering) ->
            sak to revurdering as VilkårsvurdertSøknadsbehandling.Avslag
        }
    }

    private fun persisterSøknadsbehandlingBeregnet(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, BeregnetSøknadsbehandling> = { (sak, søknad) ->
            beregnetSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        },
    ): Pair<Sak, BeregnetSøknadsbehandling> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as BeregnetSøknadsbehandling
            }
        }
    }

    fun persisterSøknadsbehandlingBeregnetInnvilget(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, BeregnetSøknadsbehandling> = { (sak, søknad) ->
            beregnetSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        },
    ): Pair<Sak, BeregnetSøknadsbehandling.Innvilget> {
        return persisterSøknadsbehandlingBeregnet(sakOgSøknad, søknadsbehandling).let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as BeregnetSøknadsbehandling.Innvilget
        }
    }

    fun persisterSøknadsbehandlingBeregnetAvslag(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, BeregnetSøknadsbehandling.Avslag> {
        return persisterSøknadsbehandlingBeregnet(sakOgSøknad) { (sak, søknad) ->
            beregnetSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 600000.0)),
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as BeregnetSøknadsbehandling.Avslag
        }
    }

    fun persistersimulertSøknadsbehandling(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, SimulertSøknadsbehandling> = { (sak, søknad) ->
            simulertSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        },
    ): Pair<Sak, SimulertSøknadsbehandling> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as SimulertSøknadsbehandling
            }
        }
    }

    private fun persisterSøknadsbehandlingTilAttestering(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, SøknadsbehandlingTilAttestering> = { (sak, søknad) ->
            tilAttesteringSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        },
    ): Pair<Sak, SøknadsbehandlingTilAttestering> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as SøknadsbehandlingTilAttestering
            }
        }
    }

    fun persisterSøknadsbehandlingTilAttesteringInnvilget(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, SøknadsbehandlingTilAttestering.Innvilget> {
        return persisterSøknadsbehandlingTilAttestering(sakOgSøknad).let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as SøknadsbehandlingTilAttestering.Innvilget
        }
    }

    fun persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, SøknadsbehandlingTilAttestering.Avslag.UtenBeregning> {
        return persisterSøknadsbehandlingTilAttestering(sakOgSøknad) { (sak, søknad) ->
            tilAttesteringSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as SøknadsbehandlingTilAttestering.Avslag.UtenBeregning
        }
    }

    fun persisterSøknadsbehandlingTilAttesteringAvslagMedBeregning(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, SøknadsbehandlingTilAttestering.Avslag.MedBeregning> {
        return persisterSøknadsbehandlingTilAttestering(sakOgSøknad) { (sak, søknad) ->
            tilAttesteringSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 60000.0)),
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as SøknadsbehandlingTilAttestering.Avslag.MedBeregning
        }
    }

    /**
     * Persisterer:
     * - sak
     * - søknad
     * - iverksatt søknadsbehandling
     * - vedtak
     * - Ved innvilgelse: utbetaling
     */
    fun persisterSøknadsbehandlingIverksatt(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        kvittering: Kvittering? = kvittering(clock = clock),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Triple<Sak, IverksattSøknadsbehandling, Stønadsvedtak> = { (sak, søknad) ->
            iverksattSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                kvittering = kvittering,
            )
        },
    ): Tuple4<Sak, IverksattSøknadsbehandling, Stønadsvedtak, Utbetaling.OversendtUtbetaling?> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling, vedtak) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            val utbetaling = if (vedtak is VedtakInnvilgetSøknadsbehandling) {
                (sak.utbetalinger.single { it.id == vedtak.utbetalingId } as Utbetaling.OversendtUtbetaling).also {
                    databaseRepos.utbetaling.opprettUtbetaling(it)
                }
            } else {
                null
            }
            databaseRepos.vedtakRepo.lagre(vedtak)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                Tuple4(
                    persistertSak!!,
                    persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as IverksattSøknadsbehandling,
                    persistertSak.vedtakListe.single { it.id == vedtak.id } as Stønadsvedtak,
                    utbetaling,
                )
            }
        }
    }

    fun persisterSøknadsbehandlingIverksattInnvilget(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        kvittering: Kvittering? = kvittering(clock = clock),
        grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
        vilkårOverrides: List<Vilkår> = emptyList(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Triple<Sak, IverksattSøknadsbehandling, Stønadsvedtak> = { (sak, søknad) ->
            iverksattSøknadsbehandlingUføre(
                clock = clock,
                sakInfo = SakInfo(
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    fnr = sak.fnr,
                    type = sak.type,
                ),
                sakOgSøknad = sak to søknad,
                kvittering = kvittering,
                customGrunnlag = grunnlagsdataOverrides,
                customVilkår = vilkårOverrides,
            )
        },
    ): Tuple4<Sak, IverksattSøknadsbehandling, VedtakInnvilgetSøknadsbehandling, Utbetaling.OversendtUtbetaling> {
        return persisterSøknadsbehandlingIverksatt(
            sakOgSøknad = sakOgSøknad,
            kvittering = kvittering,
        ) { søknadsbehandling(it) }.let { (sak, søknadsbehandling, vedtak, utbetaling) ->
            Tuple4(
                sak,
                søknadsbehandling as IverksattSøknadsbehandling.Innvilget,
                vedtak as VedtakInnvilgetSøknadsbehandling,
                utbetaling!!,
            )
        }
    }

    fun persisterSøknadsbehandlingIverksattAvslagUtenBeregning(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Triple<Sak, IverksattSøknadsbehandling.Avslag.UtenBeregning, VedtakAvslagVilkår> {
        return persisterSøknadsbehandlingIverksatt(sakOgSøknad) { (sak, søknad) ->
            iverksattSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            )
        }.let { (sak, søknadsbehandling, vedtak) ->
            Triple(
                sak,
                søknadsbehandling as IverksattSøknadsbehandling.Avslag.UtenBeregning,
                vedtak as VedtakAvslagVilkår,
            )
        }
    }

    /**
     * Persisterer:
     * - sak
     * - søknad
     * - iverksatt søknadsbehandling
     * - vedtak
     */
    fun persisterSøknadsbehandlingIverksattAvslagMedBeregning(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Triple<Sak, IverksattSøknadsbehandling.Avslag.MedBeregning, VedtakAvslagBeregning> {
        return persisterSøknadsbehandlingIverksatt(sakOgSøknad) { (sak, søknad) ->
            iverksattSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 60000.0)),
            )
        }.let { (sak, søknadsbehandling, vedtak) ->
            Triple(
                sak,
                søknadsbehandling as IverksattSøknadsbehandling.Avslag.MedBeregning,
                vedtak as VedtakAvslagBeregning,
            )
        }
    }

    private fun persisterSøknadsbehandlingUnderkjent(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, UnderkjentSøknadsbehandling> = { (sak, søknad) ->
            underkjentSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        },
    ): Pair<Sak, UnderkjentSøknadsbehandling> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as UnderkjentSøknadsbehandling
            }
        }
    }

    fun persisterSøknadsbehandlingUnderkjentInnvilget(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, UnderkjentSøknadsbehandling.Innvilget> {
        return persisterSøknadsbehandlingUnderkjent(sakOgSøknad) { (sak, søknad) ->
            underkjentSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as UnderkjentSøknadsbehandling.Innvilget
        }
    }

    fun persisterSøknadsbehandlingUnderkjentAvslagUtenBeregning(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, UnderkjentSøknadsbehandling.Avslag.UtenBeregning> {
        return persisterSøknadsbehandlingUnderkjent(sakOgSøknad) { (sak, søknad) ->
            underkjentSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as UnderkjentSøknadsbehandling.Avslag.UtenBeregning
        }
    }

    fun persisterSøknadsbehandlingUnderkjentAvslagMedBeregning(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, UnderkjentSøknadsbehandling.Avslag.MedBeregning> {
        return persisterSøknadsbehandlingUnderkjent(sakOgSøknad) { (sak, søknad) ->
            underkjentSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0)),
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as UnderkjentSøknadsbehandling.Avslag.MedBeregning
        }
    }

    /**
     * 1) persisterer en [VilkårsvurdertSøknadsbehandling.Uavklart]
     * 2) legger stønadsperiode og persisterer
     */
    fun persisternySøknadsbehandlingMedStønadsperiode(
        id: SøknadsbehandlingId = SøknadsbehandlingId.generer(),
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    ): Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart> {
        val (sak, søknad) = persisterJournalførtSøknadMedOppgave(sakId = sakId, søknadId = søknadId)
        require(sak.id == sakId && sak.søknader.count { it.sakId == sakId && it.id == søknadId } == 1)

        return sak.opprettNySøknadsbehandling(
            søknadsbehandlingId = id,
            søknadId = søknad.id,
            clock = fixedClock,
            saksbehandler = saksbehandler,
            oppdaterOppgave = null,

        ).getOrFail().let { (_, nySøknadsbehandling) ->
            databaseRepos.søknadsbehandling.lagre(nySøknadsbehandling)
            databaseRepos.sak.hentSak(sakId)!!.oppdaterStønadsperiodeForSøknadsbehandling(
                søknadsbehandlingId = nySøknadsbehandling.id,
                stønadsperiode = stønadsperiode,
                clock = clock,
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                saksbehandler = saksbehandler,
                hentPerson = { person().right() },
                saksbehandlersAvgjørelse = null,
            ).getOrFail().second.let {
                databaseRepos.søknadsbehandling.lagre(it)
                require(it.fnr == sak.fnr && it.sakId == sakId)
                Pair(databaseRepos.sak.hentSak(sakId)!!, it as VilkårsvurdertSøknadsbehandling.Uavklart)
            }
        }
    }

    fun persisternySøknadsbehandlingMedStønadsperiodeMedSkatt(
        skatt: EksterneGrunnlagSkatt = EksterneGrunnlagSkatt.Hentet(søkers = nySkattegrunnlag(), eps = null),
    ): VilkårsvurdertSøknadsbehandling.Uavklart {
        return persisternySøknadsbehandlingMedStønadsperiode().second.leggTilSkatt(skatt).getOrFail().also {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    fun persisterKlageOpprettet(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
    ): OpprettetKlage {
        return Klage.ny(
            sakId = vedtak.behandling.sakId,
            saksnummer = vedtak.behandling.saksnummer,
            fnr = vedtak.behandling.fnr,
            journalpostId = JournalpostId(value = UUID.randomUUID().toString()),
            oppgaveId = oppgaveIdRevurdering,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerNyKlage"),
            clock = clock,
            datoKlageMottatt = fixedLocalDate,
        ).also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageVilkårsvurdertUtfyltTilVurdering(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageVilkårsvurdertUtfyltAvvist(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageVilkårsvurdertBekreftetTilVurdering(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
    ): VilkårsvurdertKlage.Bekreftet.TilVurdering {
        return persisterKlageVilkårsvurdertUtfyltTilVurdering(vedtak = vedtak).bekreftVilkårsvurderinger(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerBekreftetVilkårsvurdertKlage"),
        ).getOrFail().let {
            if (it !is VilkårsvurdertKlage.Bekreftet.TilVurdering) throw IllegalStateException("Forventet en Bekreftet(TilVurdering) vilkårsvurdert klage. fikk ${it::class} ved opprettelse av test-data")
            it
        }.also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageVilkårsvurdertBekreftetAvvist(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
    ): VilkårsvurdertKlage.Bekreftet.Avvist {
        return persisterKlageVilkårsvurdertUtfyltAvvist(vedtak).bekreftVilkårsvurderinger(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerBekreftetAvvistVilkårsvurdertKlage"),
        ).getOrFail().let {
            if (it !is VilkårsvurdertKlage.Bekreftet.Avvist) throw IllegalStateException("Forventet en Bekreftet(Avvist) vilkårsvurdert klage. fikk ${it::class} ved opprettelse av test-data")
            it
        }.also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageVurdertPåbegynt(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageVurdertUtfylt(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageVurdertBekreftet(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
    ): VurdertKlage.Bekreftet {
        return persisterKlageVurdertUtfylt(vedtak = vedtak).bekreftVurderinger(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerBekreftetVurdertKlage"),
        ).also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    /**
     * underliggende klage: [persisterKlageVurdertBekreftet]
     */
    fun persisterKlageAvsluttet(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
        begrunnelse: String = "Begrunnelse for å avslutte klagen.",
        tidspunktAvsluttet: Tidspunkt = Tidspunkt.now(clock),
    ): AvsluttetKlage {
        return persisterKlageVurdertBekreftet(vedtak = vedtak).avslutt(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerSomAvsluttetKlagen"),
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
        ).getOrFail().also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageAvvist(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
        saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerAvvistKlage"),
        fritekstTilBrev: String = "en god, og lang fritekst",
    ): AvvistKlage {
        return persisterKlageVilkårsvurdertBekreftetAvvist(vedtak).leggTilFritekstTilAvvistVedtaksbrev(
            saksbehandler,
            fritekstTilBrev,
        ).also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageTilAttesteringVurdert(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
    ): KlageTilAttestering.Vurdert {
        return persisterKlageVurdertBekreftet(vedtak = vedtak).sendTilAttestering(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerKlageTilAttestering"),
        ).getOrFail().let {
            if (it !is KlageTilAttestering.Vurdert) throw IllegalStateException("Forventet en KlageTilAttestering(TilVurdering). fikk ${it::class} ved opprettelse av test-data")
            it
        }.also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageTilAttesteringAvvist(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
        saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerAvvistKlageTilAttestering"),
        fritekstTilBrev: String = "en god, og lang fritekst",
    ): KlageTilAttestering.Avvist {
        return persisterKlageAvvist(vedtak, saksbehandler, fritekstTilBrev).sendTilAttestering(
            saksbehandler = saksbehandler,
        ).getOrFail().also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageUnderkjentVurdert(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
    ): VurdertKlage.Bekreftet {
        return persisterKlageTilAttesteringVurdert(vedtak = vedtak).underkjenn(
            underkjentAttestering = Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant(navIdent = "saksbehandlerUnderkjentKlage"),
                opprettet = Tidspunkt.now(clock),
                grunn = UnderkjennAttesteringsgrunnBehandling.ANDRE_FORHOLD,
                kommentar = "underkjennelseskommentar",
            ),
        ).getOrFail().also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageUnderkjentAvvist(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
    ): AvvistKlage {
        return persisterKlageTilAttesteringAvvist(vedtak).underkjenn(
            underkjentAttestering = Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant(navIdent = "saksbehandlerUnderkjentKlage"),
                opprettet = Tidspunkt.now(clock),
                grunn = UnderkjennAttesteringsgrunnBehandling.ANDRE_FORHOLD,
                kommentar = "underkjennelseskommentar",
            ),
        ).getOrFail().also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageOversendt(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
    ): OversendtKlage {
        return persisterKlageTilAttesteringVurdert(vedtak = vedtak).oversend(
            iverksattAttestering = Attestering.Iverksatt(
                attestant = NavIdentBruker.Attestant(navIdent = "saksbehandlerOversendtKlage"),
                opprettet = Tidspunkt.now(clock),
            ),
        ).getOrFail().also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageIverksattAvvist(
        vedtak: VedtakInnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
    ): IverksattAvvistKlage {
        return persisterKlageTilAttesteringAvvist(vedtak).iverksett(
            iverksattAttestering = Attestering.Iverksatt(
                attestant = NavIdentBruker.Attestant(navIdent = "saksbehandlerIverksattAvvistKlage"),
                opprettet = Tidspunkt.now(clock),
            ),
        ).getOrFail().also { databaseRepos.klageRepo.lagre(it) }
    }

    fun persisterUprosessertKlageinstanshendelse(
        id: UUID = UUID.randomUUID(),
        klageId: KlageId = KlageId.generer(),
        utfall: KlageinstansUtfall = KlageinstansUtfall.STADFESTELSE,
        opprettet: Tidspunkt = Tidspunkt.now(clock),
    ): Pair<UUID, KlageId> {
        databaseRepos.klageinstanshendelseRepo.lagre(
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

    fun persisterSkattedokumentGenerert(
        id: UUID = UUID.randomUUID(),
        generertDokument: PdfA = PdfA("jeg er en pdf".toByteArray()),
        dokumentJson: String = """{"key": "value"}""",
        skattedataHentet: Tidspunkt = fixedTidspunkt,
    ): Skattedokument.Generert {
        return persisterSøknadsbehandlingIverksatt().let {
            nySkattedokumentGenerert(
                id = id,
                søkersSkatteId = (it.second.eksterneGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet).søkers.id,
                epsSkatteId = (it.second.eksterneGrunnlag.skatt as? EksterneGrunnlagSkatt.Hentet)?.eps?.id,
                sakId = it.first.id,
                vedtakId = it.third.id,
                generertDokument = generertDokument,
                dokumentJson = dokumentJson,
                skattedataHentet = skattedataHentet,
            ).also { databaseRepos.dokumentSkattRepo.lagre(it) }
        }
    }

    fun persisterSkattedokumentJournalført(): Skattedokument.Journalført {
        return persisterSkattedokumentGenerert().tilJournalført(JournalpostId("journalpostId")).also {
            databaseRepos.dokumentSkattRepo.lagre(it)
        }
    }

    fun persisterInstitusjonsoppholdHendelse(): InstitusjonsoppholdHendelse {
        return persisterSøknadsbehandlingIverksatt().let {
            nyInstitusjonsoppholdHendelse(sakId = it.first.id).also {
                institusjonsoppholdHendelseRepo.lagre(it, defaultHendelseMetadata())
            }
        }
    }

    fun persisterOppgaveHendelse(): OppgaveHendelse {
        return persisterInstitusjonsoppholdHendelse().let {
            nyOppgaveHendelse(
                relaterteHendelser = listOf(it.hendelseId),
                nesteVersjon = it.versjon.inc(),
                sakId = it.sakId,
            ).also { oppgaveHendelse ->
                sessionFactory.withSessionContext {
                    oppgaveHendelseRepo.lagre(oppgaveHendelse, oppgaveHendelseMetadata(), it)
                }
            }
        }
    }

    internal fun <T : Hendelse<T>> persisterOppgaveHendelseFraRelatertHendelse(
        relatertHendelse: () -> T,
    ): OppgaveHendelse {
        return relatertHendelse().let {
            nyOppgaveHendelse(
                sakId = (it as? Sakshendelse)?.sakId ?: UUID.randomUUID(),
                nesteVersjon = it.versjon.inc(),
                relaterteHendelser = listOf(it.hendelseId),
            )
        }.also { oppgaveHendelse ->
            sessionFactory.withSessionContext {
                oppgaveHendelseRepo.lagre(oppgaveHendelse, oppgaveHendelseMetadata(), it)
            }
        }
    }

    fun persisterInstJobbHendelse(): InstitusjonsoppholdHendelse {
        return persisterInstitusjonsoppholdHendelse().let { hendelse ->
            hendelse.also {
                sessionFactory.withSessionContext { tx ->
                    hendelsekonsumenterRepo.lagre(
                        hendelseId = hendelse.hendelseId,
                        konsumentId = HendelseskonsumentId("INSTITUSJON"),
                        context = tx,
                    )
                }
            }
        }
    }

    fun persisterFlereInstJobbHendelser(): List<InstitusjonsoppholdHendelse> {
        return persisterSøknadsbehandlingIverksatt().let {
            sessionFactory.withSessionContext { tx ->
                val første = nyInstitusjonsoppholdHendelse(sakId = it.first.id).also {
                    institusjonsoppholdHendelseRepo.lagre(it, defaultHendelseMetadata())
                }
                val andre = nyInstitusjonsoppholdHendelse(sakId = it.first.id, versjon = Hendelsesversjon(3)).also {
                    institusjonsoppholdHendelseRepo.lagre(it, defaultHendelseMetadata())
                }

                listOf(første, andre).also {
                    hendelsekonsumenterRepo.lagre(
                        hendelser = listOf(første.hendelseId, andre.hendelseId),
                        konsumentId = HendelseskonsumentId("INSTITUSJON"),
                        tx,
                    )
                }
            }
        }
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

fun <T> DataSource.withSession(block: (session: Session) -> T): T {
    return using(
        closeable = sessionOf(
            dataSource = this,
            timedDbMetrics = dbMetricsStub,
            queryParameterMappers = listOf(DomainToQueryParameterMapper),
        ),
    ) { block(it) }
}

fun <T> DataSource.withTransaction(block: (session: TransactionalSession) -> T): T {
    return using(
        closeable = sessionOf(
            dataSource = this,
            timedDbMetrics = dbMetricsStub,
            queryParameterMappers = listOf(DomainToQueryParameterMapper),
        ),
    ) { s -> s.transaction { block(it) } }
}
