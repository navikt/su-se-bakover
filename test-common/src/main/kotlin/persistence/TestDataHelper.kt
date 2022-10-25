package no.nav.su.se.bakover.test.persistence

import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import arrow.core.right
import kotliquery.using
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.TransactionalSession
import no.nav.su.se.bakover.common.persistence.sessionOf
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DomainToQueryParameterMapper
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
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
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
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
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
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
import no.nav.su.se.bakover.test.attesteringIverksatt
import no.nav.su.se.bakover.test.attesteringUnderkjent
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.enUkeEtterFixedClock
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
import no.nav.su.se.bakover.test.kvittering
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulerOpphør
import no.nav.su.se.bakover.test.simulerUtbetaling
import no.nav.su.se.bakover.test.simulering
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknad.journalpostIdSøknad
import no.nav.su.se.bakover.test.søknad.oppgaveIdSøknad
import no.nav.su.se.bakover.test.søknad.søknadinnhold
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.test.utbetalingslinje
import no.nav.su.se.bakover.test.veileder
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

class TestDataHelper(
    val dataSource: DataSource,
    val dbMetrics: DbMetrics = dbMetricsStub,
    val clock: Clock = fixedClock,
    satsFactory: SatsFactoryForSupplerendeStønad = satsFactoryTest,
) {
    val sessionFactory: PostgresSessionFactory =
        PostgresSessionFactory(dataSource, dbMetrics, sessionCounterStub, listOf(DomainToQueryParameterMapper))

    val databaseRepos = DatabaseBuilder.build(
        embeddedDatasource = dataSource,
        dbMetrics = dbMetrics,
        clock = clock,
        satsFactory = satsFactory,
    )
    val avstemmingRepo = databaseRepos.avstemming
    val avkortingsvarselRepo = databaseRepos.avkortingsvarselRepo
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
    val tilbakekrevingRepo = databaseRepos.tilbakekrevingRepo
    val søknadsbehandlingRepo = databaseRepos.søknadsbehandling
    val utbetalingRepo = databaseRepos.utbetaling

    /**
     * Oppretter og persisterer en ny sak (dersom den ikke finnes fra før) med søknad med tomt søknadsinnhold.
     * Søknaden er uten journalføring og oppgave.
     */
    fun persisterSakMedSøknadUtenJournalføringOgOppgave(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        fnr: Fnr = Fnr.generer(),
        søknadInnhold: SøknadsinnholdUføre = søknadinnhold(),
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
        søknadInnhold: SøknadsinnholdUføre = søknadinnhold(),
        identBruker: NavIdentBruker = veileder,
    ): Søknad.Ny {
        return Søknad.Ny(
            sakId = sakId,
            id = søknadId,
            søknadInnhold = søknadInnhold,
            opprettet = fixedTidspunkt,
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
        søknadInnhold: SøknadsinnholdUføre = søknadinnhold(),
    ): Søknad.Journalført.MedOppgave.Lukket {
        return persisterJournalførtSøknadMedOppgave(
            sakId = sakId,
            søknadId = søknadId,
            fnr = fnr,
            journalpostId = journalpostId,
            søknadInnhold = søknadInnhold,
        ).second.let {
            it.lukk(
                trekkSøknad(søknadId),
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
        søknadInnhold: SøknadsinnholdUføre = søknadinnhold(),
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
        søknadInnhold: SøknadsinnholdUføre = søknadinnhold(),
    ): Søknad.Journalført.UtenOppgave {
        return persisterSøknadUtenJournalføringOgOppgavePåEksisterendeSak(
            sakId = sakId,
            søknadId = søknadId,
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
        søknadInnhold: SøknadsinnholdUføre = søknadinnhold(),
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

    /**
     * Persisterer:
     * 1. [Søknadsbehandling.Iverksatt.Innvilget]
     * 1. [Utbetaling.OversendtUtbetaling]:
     *    1. [Utbetaling.OversendtUtbetaling.UtenKvittering]
     *    1. [Utbetaling.OversendtUtbetaling.MedKvittering]
     * 1. [VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling]
     */
    fun persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode = stønadsperiode.periode)),
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
    ): Triple<Sak, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling, Utbetaling.OversendtUtbetaling.MedKvittering> {
        assert(søknadsbehandling.sakId == sakId && søknadsbehandling.søknad.sakId == sakId && søknadsbehandling.søknad.id == søknadId)

        val utbetalingMedKvittering: Utbetaling.OversendtUtbetaling.MedKvittering = oversendtUtbetalingUtenKvittering(
            id = utbetalingId,
            søknadsbehandling = søknadsbehandling,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = utbetalingslinjer,
        ).let {
            databaseRepos.utbetaling.opprettUtbetaling(it, sessionFactory.newTransactionContext())
            it.toKvittertUtbetaling(kvittering()).also { utbetalingMedKvittering ->
                databaseRepos.utbetaling.oppdaterMedKvittering(utbetalingMedKvittering)
            }
        }
        val vedtak = VedtakSomKanRevurderes.fromSøknadsbehandling(
            søknadsbehandling = søknadsbehandling,
            utbetalingId = utbetalingMedKvittering.id,
            clock = fixedClock,
        ).also {
            databaseRepos.vedtakRepo.lagre(it)
        }
        return Triple(
            databaseRepos.sak.hentSak(sakId)!!,
            vedtak,
            utbetalingMedKvittering,
        )
    }

    /**
     * Persisterer:
     * 1. [Søknadsbehandling.Iverksatt.Innvilget]
     * 1. [Utbetaling.OversendtUtbetaling.UtenKvittering]
     * 1. [VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling]
     */
    fun persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingUtenKvittering(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode = stønadsperiode.periode)),
        utbetalingId: UUID30 = UUID30.randomUUID(),
        søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget = persisterSøknadsbehandlingIverksattInnvilget(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second,
    ): Pair<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        assert(søknadsbehandling.sakId == sakId && søknadsbehandling.søknad.sakId == sakId && søknadsbehandling.søknad.id == søknadId)

        val utbetalingUtenKvittering: Utbetaling.OversendtUtbetaling.UtenKvittering = oversendtUtbetalingUtenKvittering(
            id = utbetalingId,
            søknadsbehandling = søknadsbehandling,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = utbetalingslinjer,
        ).also {
            databaseRepos.utbetaling.opprettUtbetaling(it, sessionFactory.newTransactionContext())
        }
        return Pair(
            VedtakSomKanRevurderes.fromSøknadsbehandling(
                søknadsbehandling,
                utbetalingUtenKvittering.id,
                fixedClock,
            ).also {
                databaseRepos.vedtakRepo.lagre(it)
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
        return Triple(databaseRepos.sak.hentSak(sakId)!!, vedtak, søknadsbehandling)
    }

    /**
     * Persisterer:
     * 1. [Utbetaling.OversendtUtbetaling]:
     *    1. [Utbetaling.OversendtUtbetaling.UtenKvittering]
     *    1. [Utbetaling.OversendtUtbetaling.MedKvittering]
     * 1. [VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering]
     */
    fun persisterVedtakMedInnvilgetRevurderingOgOversendtUtbetalingMedKvittering(
        revurdering: RevurderingTilAttestering.Innvilget = persisterRevurderingTilAttesteringInnvilget().second,
        periode: Periode = stønadsperiode2021.periode,
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode = periode)),
        utbetalingId: UUID30 = UUID30.randomUUID(),
    ): Pair<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering> {
        val utbetaling = oversendtUtbetalingUtenKvittering(
            id = utbetalingId,
            revurdering = revurdering,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = utbetalingslinjer,
        ).let {
            databaseRepos.utbetaling.opprettUtbetaling(it, sessionFactory.newTransactionContext())
            it.toKvittertUtbetaling(kvittering()).also { utbetalingMedKvittering ->
                databaseRepos.utbetaling.oppdaterMedKvittering(utbetalingMedKvittering)
            }
        }
        return Pair(
            VedtakSomKanRevurderes.from(
                revurdering = revurdering.tilIverksatt(
                    attestant = attestant,
                    clock = fixedClock,
                    hentOpprinneligAvkorting = { avkortingid ->
                        databaseRepos.avkortingsvarselRepo.hent(id = avkortingid)
                    },
                ).getOrFail().also {
                    databaseRepos.revurderingRepo.lagre(it)
                },
                utbetalingId = utbetaling.id,
                fixedClock,
            ).also {
                databaseRepos.vedtakRepo.lagre(it)
            },
            utbetaling,
        )
    }

    fun persisterVedtakForKlageIverksattAvvist(
        klage: IverksattAvvistKlage = persisterKlageIverksattAvvist(),
    ): Klagevedtak.Avvist {
        return Klagevedtak.Avvist.fromIverksattAvvistKlage(klage, fixedClock).also {
            databaseRepos.vedtakRepo.lagre(it)
        }
    }

    fun persisterVedtakForStans(
        stans: StansAvYtelseRevurdering.IverksattStansAvYtelse = persisterStansAvYtelseIverksatt(),
        periode: Periode = stønadsperiode2021.periode,
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode = periode)),
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
            databaseRepos.utbetaling.opprettUtbetaling(it, sessionFactory.newTransactionContext())
            it.toKvittertUtbetaling(kvittering()).also { utbetalingMedKvittering ->
                databaseRepos.utbetaling.oppdaterMedKvittering(utbetalingMedKvittering)
            }
        }
        return VedtakSomKanRevurderes.from(stans, utbetalingId, fixedClock).also {
            databaseRepos.vedtakRepo.lagre(it)
        }
    }

    /**
     * TODO jah: På sikt burde denne muligens først persistere en stans, men føler det er litt out of scope for denne PR.
     */
    fun persisterVedtakForGjenopptak(
        stans: GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse = persisterGjenopptakAvYtelseIverksatt(),
        periode: Periode = stønadsperiode2021.periode,
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode = periode)),
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
            databaseRepos.utbetaling.opprettUtbetaling(it, sessionFactory.newTransactionContext())
            it.toKvittertUtbetaling(kvittering()).also { utbetalingMedKvittering ->
                databaseRepos.utbetaling.oppdaterMedKvittering(utbetalingMedKvittering)
            }
        }
        return VedtakSomKanRevurderes.from(stans, utbetalingId, fixedClock).also {
            databaseRepos.vedtakRepo.lagre(it)
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
                databaseRepos.reguleringRepo.lagre(it)
            }
        }

    fun persisterReguleringIverksatt(
        startDato: LocalDate = 1.mai(2021),
        clock: Clock = fixedClock,
    ) = persisterReguleringOpprettet(
        startDato = startDato,
        clock = clock,
    ).let {
        it.beregn(
            satsFactory = satsFactoryTestPåDato(),
            begrunnelse = "Begrunnelse",
            clock = clock,
        ).getOrFail().simuler(
            simuler = { _, _ ->
                simulertUtbetaling().simulering.right() // TODO bare tull, refaktorer vekk hele funksjonen og gjør koblinger mot sak/revurdering
            },
        ).getOrFail().tilIverksatt().also { iverksattAttestering ->
            databaseRepos.reguleringRepo.lagre(iverksattAttestering)
        }
    }

    fun persisterRevurderingOpprettet(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().let {
            Pair(
                it.first,
                it.second,
            )
        },
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
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = periode).tilVilkårsvurderingerRevurdering(),
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
                databaseRepos.revurderingRepo.lagre(it)
                sak.copy(
                    revurderinger = sak.revurderinger + listOf(it),
                ) to it
            }
        }
    }

    fun persisterRevurderingBeregnetInnvilget(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().let {
            Pair(
                it.first,
                it.second,
            )
        },
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
                databaseRepos.revurderingRepo.lagre(beregnet)
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
                    databaseRepos.revurderingRepo.lagre(opphørt)
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
                databaseRepos.revurderingRepo.lagre(beregnet)
                sak.copy(
                    revurderinger = sak.revurderinger.filterNot { it.id == beregnet.id } + listOf(beregnet),
                ) to beregnet as BeregnetRevurdering.IngenEndring
            }
        }
    }

    fun persisterRevurderingSimulertInnvilget(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().let {
            Pair(
                it.first,
                it.second,
            )
        },
    ): Pair<Sak, SimulertRevurdering.Innvilget> {
        return persisterRevurderingBeregnetInnvilget(
            sakOgVedtak = sakOgVedtak,
        ).let { (sak, beregnet) ->
            beregnet.simuler(
                saksbehandler = saksbehandler,
                clock = clock,
                simuler = { _, _ ->
                    simulerUtbetaling(
                        sak = sak,
                        revurdering = beregnet,
                        strict = false,
                    ).map {
                        it.simulering
                    }
                },
            ).getOrFail().let { simulert ->
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
                databaseRepos.revurderingRepo.lagre(oppdatertTilbakekrevingsbehandling)
                sak.copy(
                    revurderinger = sak.revurderinger.filterNot { it.id == oppdatertTilbakekrevingsbehandling.id } + listOf(
                        oppdatertTilbakekrevingsbehandling,
                    ),
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
                saksbehandler = saksbehandler,
                clock = clock,
                simuler = { periode: Periode, _: NavIdentBruker.Saksbehandler ->
                    simulerOpphør(
                        sak = sak,
                        revurdering = beregnet,
                        simuleringsperiode = periode,
                    )
                },
            ).getOrFail().let { simulert ->
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
                databaseRepos.revurderingRepo.lagre(oppdatertTilbakekrevingsbehandling)
                sak.copy(
                    revurderinger = sak.revurderinger.filterNot { it.id == oppdatertTilbakekrevingsbehandling.id } + listOf(
                        oppdatertTilbakekrevingsbehandling,
                    ),
                ) to oppdatertTilbakekrevingsbehandling
            }
        }
    }

    /**
     * Setter forhåndsvarsel til SkalIkkeForhåndsvarsel dersom den ikke er satt på dette tidspunktet.
     */
    fun persisterRevurderingTilAttesteringInnvilget(): Pair<Sak, RevurderingTilAttestering.Innvilget> {
        return persisterRevurderingSimulertInnvilget().let { (sak, simulert) ->
            val håndtertForhåndsvarsel =
                if (simulert.forhåndsvarsel == null) simulert.ikkeSendForhåndsvarsel().getOrFail() else simulert

            håndtertForhåndsvarsel.tilAttestering(
                attesteringsoppgaveId = oppgaveIdRevurdering,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "",
            ).getOrFail().let { tilAttestering ->
                databaseRepos.revurderingRepo.lagre(tilAttestering)
                sak.copy(
                    revurderinger = sak.revurderinger.filterNot { it.id == tilAttestering.id } + listOf(tilAttestering),
                ) to tilAttestering
            }
        }
    }

    /**
     * Setter forhåndsvarsel til SkalIkkeForhåndsvarsel dersom den ikke er satt på dette tidspunktet.
     */
    fun persisterRevurderingTilAttesteringIngenEndring(): RevurderingTilAttestering.IngenEndring {
        val beregnet: BeregnetRevurdering.IngenEndring = persisterRevurderingBeregningIngenEndring().second
        return beregnet.tilAttestering(
            attesteringsoppgaveId = oppgaveIdRevurdering,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            skalFøreTilUtsendingAvVedtaksbrev = false,
        ).also {
            databaseRepos.revurderingRepo.lagre(it)
        }
    }

    /**
     * Setter forhåndsvarsel til SkalIkkeForhåndsvarsel.
     */
    fun persisterRevurderingTilAttesteringOpphørt(): RevurderingTilAttestering.Opphørt {
        return persisterRevurderingSimulertOpphørt().second.ikkeSendForhåndsvarsel().getOrFail().tilAttestering(
            attesteringsoppgaveId = oppgaveIdRevurdering,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
        ).getOrFail().also { databaseRepos.revurderingRepo.lagre(it) }
    }

    fun persisterRevurderingIverksattInnvilget(): IverksattRevurdering.Innvilget {
        return persisterRevurderingTilAttesteringInnvilget().second.tilIverksatt(
            attestant = attestant,
            clock = fixedClock,
            hentOpprinneligAvkorting = { null },
        ).getOrHandle {
            throw IllegalStateException("Her skulle vi ha hatt en iverksatt revurdering")
        }.also {
            databaseRepos.revurderingRepo.lagre(it)
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
            databaseRepos.revurderingRepo.lagre(it)
        }
    }

    fun persisterRevurderingIverksattOpphørt(): IverksattRevurdering.Opphørt {
        return persisterRevurderingTilAttesteringOpphørt().tilIverksatt(
            attestant,
            { null },
            fixedClock,
        ).getOrFail().also {
            databaseRepos.revurderingRepo.lagre(it)
        }
    }

    fun persisterRevurderingUnderkjentInnvilget(): UnderkjentRevurdering.Innvilget {
        return persisterRevurderingTilAttesteringInnvilget().second.underkjenn(
            attesteringUnderkjent(),
            OppgaveId("oppgaveid"),
        ).also {
            databaseRepos.revurderingRepo.lagre(it)
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
            ).getOrFail().also { databaseRepos.revurderingRepo.lagre(it) }
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
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = periode).tilVilkårsvurderingerRevurdering(),
        tilRevurdering: VedtakSomKanRevurderes = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
            stønadsperiode = stønadsperiode2021,
        ).second,
        simulering: Simulering = simulering(fnr = Fnr.generer()),
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
            databaseRepos.revurderingRepo.lagre(it)
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
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = periode).tilVilkårsvurderingerRevurdering(),
        tilRevurdering: VedtakSomKanRevurderes = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
            stønadsperiode = stønadsperiode2021,
        ).second,
        simulering: Simulering = simulering(fnr = Fnr.generer()),
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
        ).iverksett(attesteringIverksatt()).getOrFail().also {
            databaseRepos.revurderingRepo.lagre(it)
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
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = periode).tilVilkårsvurderingerRevurdering(),
        tilRevurdering: VedtakSomKanRevurderes = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
            stønadsperiode = stønadsperiode2021,
        ).second,
        simulering: Simulering = simulering(fnr = Fnr.generer()),
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
            databaseRepos.revurderingRepo.lagre(it)
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
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = periode).tilVilkårsvurderingerRevurdering(),
        tilRevurdering: VedtakSomKanRevurderes = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
            stønadsperiode = stønadsperiode2021,
        ).second,
        simulering: Simulering = simulering(fnr = Fnr.generer()),
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
        ).iverksett(attesteringIverksatt()).getOrFail().also {
            databaseRepos.revurderingRepo.lagre(it)
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
                databaseRepos.søknadsbehandling.lagre(lukketSøknadsbehandling)
                databaseRepos.søknad.lukkSøknad(lukketSøknadsbehandling.søknad)
                Pair(databaseRepos.sak.hentSak(sakId)!!, lukketSøknadsbehandling)
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
            databaseRepos.søknadsbehandling.lagreNySøknadsbehandling(nySøknadsbehandling)
            databaseRepos.sak.hentSak(sakId)!!.oppdaterStønadsperiodeForSøknadsbehandling(
                søknadsbehandlingId = nySøknadsbehandling.id,
                stønadsperiode = stønadsperiode,
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
            ).getOrFail().let {
                databaseRepos.søknadsbehandling.lagre(it)
                assert(it.fnr == sak.fnr && it.sakId == sakId)
                Pair(databaseRepos.sak.hentSak(sakId)!!, it as Søknadsbehandling.Vilkårsvurdert.Uavklart)
            }
        }
    }

    fun persisterSøknadsbehandlingVilkårsvurdertInnvilget(
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
            databaseRepos.søknadsbehandling.lagre(it)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it as Søknadsbehandling.Vilkårsvurdert.Innvilget)
        }
    }

    fun persisterSøknadsbehandlingVilkårsvurdertAvslag(
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
        ).vilkårsvurder().let {
            databaseRepos.søknadsbehandling.lagre(it)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it as Søknadsbehandling.Vilkårsvurdert.Avslag)
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
            databaseRepos.søknadsbehandling.lagre(it)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it as Søknadsbehandling.Beregnet.Innvilget)
        }
    }

    fun persisterSøknadsbehandlingBeregnetAvslag(
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
            databaseRepos.søknadsbehandling.lagre(it as Søknadsbehandling.Beregnet.Avslag)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it)
        }
    }

    fun persisterSøknadsbehandlingSimulert(
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
            ) { _, _ ->
                simulerUtbetaling(
                    sak = sak,
                    søknadsbehandling = beregnet,
                    strict = false,
                ).map {
                    it.simulering
                }
            }.getOrFail()
                .let {
                    databaseRepos.søknadsbehandling.lagre(it)
                    Pair(databaseRepos.sak.hentSak(sakId)!!, it)
                }
        }
    }

    fun persisterSøknadsbehandlingTilAttesteringInnvilget(
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
            databaseRepos.søknadsbehandling.lagre(it)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it)
        }
    }

    fun persisterSøknadsbehandlingTilAttesteringAvslagMedBeregning(
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
            databaseRepos.søknadsbehandling.lagre(it)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it)
        }
    }

    fun persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning(
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
            databaseRepos.søknadsbehandling.lagre(it)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it)
        }
    }

    fun persisterSøknadsbehandlingUnderkjentInnvilget(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.Underkjent.Innvilget> {
        return persisterSøknadsbehandlingTilAttesteringInnvilget(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.tilUnderkjent(
            attesteringUnderkjent(),
        ).let {
            databaseRepos.søknadsbehandling.lagre(it)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it)
        }
    }

    fun persisterSøknadsbehandlingUnderkjentAvslagUtenBeregning(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.Underkjent.Avslag.UtenBeregning> {
        return persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.tilUnderkjent(
            attesteringUnderkjent(),
        ).let {
            databaseRepos.søknadsbehandling.lagre(it)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it)
        }
    }

    fun persisterSøknadsbehandlingUnderkjentAvslagMedBeregning(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.Underkjent.Avslag.MedBeregning> {
        return persisterSøknadsbehandlingTilAttesteringAvslagMedBeregning(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.tilUnderkjent(
            attesteringUnderkjent(),
        ).let {
            databaseRepos.søknadsbehandling.lagre(it)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it)
        }
    }

    fun persisterSøknadsbehandlingIverksattInnvilget(
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
            attestering = attesteringIverksatt(),
        ).let {
            databaseRepos.søknadsbehandling.lagre(it)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it)
        }
    }

    fun persisterSøknadsbehandlingIverksattAvslagUtenBeregning(
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
            attesteringIverksatt(
                clock = enUkeEtterFixedClock,
            ),
        ).let {
            databaseRepos.søknadsbehandling.lagre(it)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it)
        }
    }

    fun persisterSøknadsbehandlingIverksattAvslagMedBeregning(
        sakId: UUID = UUID.randomUUID(),
        søknadId: UUID = UUID.randomUUID(),
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
    ): Pair<Sak, Søknadsbehandling.Iverksatt.Avslag.MedBeregning> {
        return persisterSøknadsbehandlingTilAttesteringAvslagMedBeregning(
            sakId = sakId,
            søknadId = søknadId,
            stønadsperiode = stønadsperiode,
        ).second.tilIverksatt(
            attesteringIverksatt(),
        ).let {
            databaseRepos.søknadsbehandling.lagre(it)
            Pair(databaseRepos.sak.hentSak(sakId)!!, it)
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
            oppgaveId = oppgaveIdRevurdering,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerNyKlage"),
            clock = fixedClock,
            datoKlageMottatt = fixedLocalDate,
        ).also {
            databaseRepos.klageRepo.lagre(it)
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
            databaseRepos.klageRepo.lagre(it)
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
            databaseRepos.klageRepo.lagre(it)
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
            databaseRepos.klageRepo.lagre(it)
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
            databaseRepos.klageRepo.lagre(it)
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
            databaseRepos.klageRepo.lagre(it)
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
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageVurdertBekreftet(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
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
            databaseRepos.klageRepo.lagre(it)
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
            databaseRepos.klageRepo.lagre(it)
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
            databaseRepos.klageRepo.lagre(it)
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
            databaseRepos.klageRepo.lagre(it)
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
            databaseRepos.klageRepo.lagre(it)
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
            databaseRepos.klageRepo.lagre(it)
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
            databaseRepos.klageRepo.lagre(it)
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
        ).getOrFail().also { databaseRepos.klageRepo.lagre(it) }
    }

    fun persisterUprosessertKlageinstanshendelse(
        id: UUID = UUID.randomUUID(),
        klageId: UUID = UUID.randomUUID(),
        utfall: KlageinstansUtfall = KlageinstansUtfall.STADFESTELSE,
        opprettet: Tidspunkt = fixedTidspunkt,
    ): Pair<UUID, UUID> {
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
