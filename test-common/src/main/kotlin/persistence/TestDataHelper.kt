package no.nav.su.se.bakover.test.persistence

import arrow.core.NonEmptyList
import arrow.core.Tuple4
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
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.TransactionalSession
import no.nav.su.se.bakover.common.persistence.sessionOf
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DomainToQueryParameterMapper
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attestering
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
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.opprettEllerOppdaterRegulering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.oppdaterStønadsperiodeForSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.attesteringIverksatt
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.beregnetSøknadsbehandling
import no.nav.su.se.bakover.test.epsFnr
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataMedEpsMedFradrag
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.kvittering
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.revurderingUnderkjent
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulerUtbetaling
import no.nav.su.se.bakover.test.simulering
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.simulertSøknadsbehandling
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknad.journalpostIdSøknad
import no.nav.su.se.bakover.test.søknad.oppgaveIdSøknad
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.tilAttesteringSøknadsbehandling
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.test.underkjentSøknadsbehandling
import no.nav.su.se.bakover.test.utbetalingslinje
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.veileder
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderingerSøknadsbehandlingInnvilget
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandling
import vilkår.personligOppmøtevilkårAvslag
import java.time.Clock
import java.time.LocalDate
import java.util.LinkedList
import java.util.UUID
import javax.sql.DataSource

class TestDataHelper(
    val dataSource: DataSource,
    val dbMetrics: DbMetrics = dbMetricsStub,
    val clock: Clock = tikkendeFixedClock(),
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
        søknadInnhold: SøknadInnhold = søknadinnholdUføre(),
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
        søknadInnhold: SøknadInnhold = søknadinnholdUføre(),
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
        søknadInnhold: SøknadsinnholdUføre = søknadinnholdUføre(),
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
        søknadInnhold: SøknadInnhold = søknadinnholdUføre(),
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
        søknadInnhold: SøknadInnhold = søknadinnholdUføre(),
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
        søknadInnhold: SøknadInnhold = søknadinnholdUføre(),
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

    fun persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Triple<Sak, Søknadsbehandling.Iverksatt, Stønadsvedtak> = { (sak, søknad) ->
            iverksattSøknadsbehandlingUføre(
                clock = clock,
                sakOgSøknad = sak to søknad,
            )
        },
    ): Triple<Sak, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling, Utbetaling.OversendtUtbetaling.MedKvittering> {
        return persisterSøknadsbehandlingIverksatt(sakOgSøknad) { søknadsbehandling(it) }.let { (sak, _, vedtak) ->
            (vedtak as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling).let {
                databaseRepos.utbetaling.hentOversendtUtbetalingForUtbetalingId(vedtak.utbetalingId)
                    .let { utbetalingUtenKvittering ->
                        (utbetalingUtenKvittering as Utbetaling.OversendtUtbetaling.UtenKvittering).toKvittertUtbetaling(
                            kvittering(),
                        ).let { utbetalingMedKvittering ->
                            databaseRepos.utbetaling.oppdaterMedKvittering(utbetalingMedKvittering)
                            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                                Triple(
                                    persistertSak!!,
                                    persistertSak.vedtakListe.single { it.id == vedtak.id } as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling,
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
     * 1. [VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering]
     */
    fun persisterVedtakMedInnvilgetRevurderingOgOversendtUtbetalingMedKvittering(
        sakOgRevurdering: Tuple4<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.UtenKvittering, VedtakSomKanRevurderes.EndringIYtelse> = persisterIverksattRevurdering(),
    ): Pair<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering> {
        return sakOgRevurdering.let { (sak, _, utbetaling, vedtak) ->
            databaseRepos.utbetaling.oppdaterMedKvittering(utbetaling.toKvittertUtbetaling(kvittering()))
            databaseRepos.sak.hentSak(sak.id)!!.let { persistertSak ->
                persistertSak.vedtakListe.single { it.id == vedtak.id } as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering to persistertSak.utbetalinger.single { it.id == utbetaling.id } as Utbetaling.OversendtUtbetaling.MedKvittering
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
        return VedtakSomKanRevurderes.from(stans, utbetalingId, clock).also {
            databaseRepos.vedtakRepo.lagre(it)
        }
    }

    fun persisterReguleringOpprettet(
        startDato: LocalDate = 1.mai(2021),
        clock: Clock = tikkendeFixedClock(),
    ): Pair<Sak, OpprettetRegulering> {
        return persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().first.let { sak ->
            sak.opprettEllerOppdaterRegulering(
                startDato = startDato,
                clock = clock,
            ).getOrFail().let {
                databaseRepos.reguleringRepo.lagre(it)
                sak.copy(
                    reguleringer = sak.reguleringer + it,
                ) to it
            }
        }
    }

    fun persisterReguleringIverksatt(
        startDato: LocalDate = 1.mai(2021),
        clock: Clock = tikkendeFixedClock(),
    ): Pair<Sak, IverksattRegulering> {
        return persisterReguleringOpprettet(
            startDato = startDato,
            clock = clock,
        ).let { (sak, regulering) ->
            regulering.beregn(
                satsFactory = satsFactoryTestPåDato(),
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
                        ).getOrFail().simulering.right()
                    },
                ).getOrFail().tilIverksatt().let { iverksattAttestering ->
                    databaseRepos.reguleringRepo.lagre(iverksattAttestering)
                    sak.copy(
                        reguleringer = sak.reguleringer.filterNot { it.id == iverksattAttestering.id } + iverksattAttestering,
                    ) to iverksattAttestering
                }
            }
        }
    }

    fun persisterRevurderingOpprettet(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
        sakOgRevurdering: (sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse>) -> Pair<Sak, OpprettetRevurdering> = {
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
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
        sakOgRevurdering: (sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse>) -> Pair<Sak, BeregnetRevurdering> = {
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
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
        sakOgRevurdering: (sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse>) -> Pair<Sak, SimulertRevurdering> = {
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
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
        sakOgRevurdering: (sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse>) -> Pair<Sak, RevurderingTilAttestering> = {
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
     */
    fun persisterIverksattRevurdering(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
        sakOgRevurdering: (sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse>) -> Tuple4<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.UtenKvittering, VedtakSomKanRevurderes.EndringIYtelse> = {
            iverksattRevurdering(clock = clock, sakOgVedtakSomKanRevurderes = it)
        },
    ): Tuple4<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.UtenKvittering, VedtakSomKanRevurderes.EndringIYtelse> {
        return sakOgRevurdering(sakOgVedtak).let { (sak, revurdering, utbetaling, vedtak) ->
            databaseRepos.revurderingRepo.lagre(revurdering)
            databaseRepos.utbetaling.opprettUtbetaling(utbetaling)
            databaseRepos.vedtakRepo.lagre(vedtak)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                Tuple4(
                    first = persistertSak!!,
                    second = persistertSak.revurderinger.single { it.id == revurdering.id } as IverksattRevurdering,
                    third = persistertSak.utbetalinger.single { it.id == utbetaling.id } as Utbetaling.OversendtUtbetaling.UtenKvittering,
                    fourth = persistertSak.vedtakListe.single { it.id == vedtak.id } as VedtakSomKanRevurderes.EndringIYtelse,
                )
            }
        }
    }

    private fun persisterUnderkjentRevurdering(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
        sakOgRevurdering: (sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse>) -> Pair<Sak, UnderkjentRevurdering> = {
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
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
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
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Pair<Sak, SimulertRevurdering.Innvilget> {
        return persisterSimulertRevurdering(sakOgVedtak).let { (sak, revurdering) ->
            sak to revurdering as SimulertRevurdering.Innvilget
        }
    }

    fun persisterRevurderingSimulertOpphørt(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Pair<Sak, SimulertRevurdering.Opphørt> {
        return persisterSimulertRevurdering(sakOgVedtak) { (sak, vedtak) ->
            simulertRevurdering(
                sakOgVedtakSomKanRevurderes = sak to vedtak,
                clock = clock,
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
            )
        }.let { (sak, revurdering) ->
            sak to revurdering as SimulertRevurdering.Opphørt
        }
    }

    fun persisterRevurderingTilAttesteringInnvilget(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Pair<Sak, RevurderingTilAttestering.Innvilget> {
        return persisterRevurderingTilAttestering(sakOgVedtak).let { (sak, revurdering) ->
            sak to revurdering as RevurderingTilAttestering.Innvilget
        }
    }

    fun persisterRevurderingTilAttesteringOpphørt(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
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
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Tuple4<Sak, IverksattRevurdering.Innvilget, Utbetaling, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering> {
        @Suppress("UNCHECKED_CAST")
        return persisterIverksattRevurdering(sakOgVedtak) as Tuple4<Sak, IverksattRevurdering.Innvilget, Utbetaling, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>
    }

    fun persisterRevurderingIverksattOpphørt(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): IverksattRevurdering.Opphørt {
        return persisterIverksattRevurdering(sakOgVedtak) { (sak, vedtak) ->
            iverksattRevurdering(
                clock = clock,
                sakOgVedtakSomKanRevurderes = sak to vedtak,
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
            )
        }.let {
            it.second as IverksattRevurdering.Opphørt
        }
    }

    fun persisterRevurderingUnderkjentInnvilget(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
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
    fun persisterRevurderingAvsluttet(): AvsluttetRevurdering {
        val (sak, vedtak, _) = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
        return persisterRevurderingOpprettet(
            sakOgVedtak = sak to vedtak,
        ).let { (_, opprettet) ->
            opprettet.avslutt(
                begrunnelse = "",
                brevvalg = null,
                tidspunktAvsluttet = Tidspunkt.now(clock),
            ).getOrFail().also { databaseRepos.revurderingRepo.lagre(it) }
        }
    }

    fun persisterSimulertStansAvYtelse(
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
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
        sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { (sak, vedtak, _) ->
            sak to vedtak
        },
    ): Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse> {
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
        id: UUID = UUID.randomUUID(),
        opprettet: Tidspunkt = Tidspunkt.now(clock),
        oppdatert: Tidspunkt = Tidspunkt.now(clock),
        periode: Periode = Periode.create(
            stønadsperiode2021.periode.fraOgMed.plusMonths(1),
            stønadsperiode2021.periode.tilOgMed,
        ),
        grunnlagsdata: Grunnlagsdata = grunnlagsdataMedEpsMedFradrag(periode, epsFnr),
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = periode).tilVilkårsvurderingerRevurdering(),
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
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
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
        id: UUID = UUID.randomUUID(),
        opprettet: Tidspunkt = Tidspunkt.now(clock),
        oppdatert: Tidspunkt = Tidspunkt.now(clock),
        periode: Periode = Periode.create(
            stønadsperiode2021.periode.fraOgMed.plusMonths(1),
            stønadsperiode2021.periode.tilOgMed,
        ),
        grunnlagsdata: Grunnlagsdata = grunnlagsdataMedEpsMedFradrag(periode, epsFnr),
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = periode).tilVilkårsvurderingerRevurdering(),
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
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Triple<Sak, Søknadsbehandling.Iverksatt, Stønadsvedtak> = { (sak, søknad) ->
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
    ): Triple<Sak, Søknadsbehandling.Iverksatt, Avslagsvedtak> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling, vedtak) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            databaseRepos.vedtakRepo.lagre(vedtak)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                Triple(
                    persistertSak!!,
                    persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as Søknadsbehandling.Iverksatt.Avslag,
                    persistertSak.vedtakListe.single { it.id == vedtak.id } as Avslagsvedtak,
                )
            }
        }
    }

    fun persisterSøknadsbehandlingVilkårsvurdert(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, Søknadsbehandling.Vilkårsvurdert> = { (sak, søknad) ->
            vilkårsvurdertSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        },
    ): Pair<Sak, Søknadsbehandling.Vilkårsvurdert> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as Søknadsbehandling.Vilkårsvurdert
            }
        }
    }

    fun persisterSøknadsbehandlingVilkårsvurdertUavklart(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Uavklart> = { (sak, søknad) ->
            nySøknadsbehandlingMedStønadsperiode(
                sakOgSøknad = sak to søknad,
                clock = clock,
            ).let {
                it.first to it.second
            }
        },
    ): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Uavklart> {
        return persisterSøknadsbehandlingVilkårsvurdert(sakOgSøknad) { søknadsbehandling(it) }.let { (sak, vilkårsvurdertSøknadsbehandling) ->
            sak to vilkårsvurdertSøknadsbehandling as Søknadsbehandling.Vilkårsvurdert.Uavklart
        }
    }

    fun persisterSøknadsbehandlingVilkårsvurdertInnvilget(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Innvilget> {
        return persisterSøknadsbehandlingVilkårsvurdert(sakOgSøknad) { (sak, søknad) ->
            vilkårsvurdertSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        }.let { (sak, revurdering) ->
            sak to revurdering as Søknadsbehandling.Vilkårsvurdert.Innvilget
        }
    }

    fun persisterSøknadsbehandlingVilkårsvurdertAvslag(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Avslag> {
        return persisterSøknadsbehandlingVilkårsvurdert(sakOgSøknad) { (sak, søknad) ->
            vilkårsvurdertSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            )
        }.let { (sak, revurdering) ->
            sak to revurdering as Søknadsbehandling.Vilkårsvurdert.Avslag
        }
    }

    private fun persisterSøknadsbehandlingBeregnet(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, Søknadsbehandling.Beregnet> = { (sak, søknad) ->
            beregnetSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        },
    ): Pair<Sak, Søknadsbehandling.Beregnet> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as Søknadsbehandling.Beregnet
            }
        }
    }

    fun persisterSøknadsbehandlingBeregnetInnvilget(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, Søknadsbehandling.Beregnet.Innvilget> {
        return persisterSøknadsbehandlingBeregnet(sakOgSøknad).let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as Søknadsbehandling.Beregnet.Innvilget
        }
    }

    fun persisterSøknadsbehandlingBeregnetAvslag(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, Søknadsbehandling.Beregnet.Avslag> {
        return persisterSøknadsbehandlingBeregnet(sakOgSøknad) { (sak, søknad) ->
            beregnetSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 600000.0)),
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as Søknadsbehandling.Beregnet.Avslag
        }
    }

    fun persisterSøknadsbehandlingSimulert(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, Søknadsbehandling.Simulert> = { (sak, søknad) ->
            simulertSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        },
    ): Pair<Sak, Søknadsbehandling.Simulert> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as Søknadsbehandling.Simulert
            }
        }
    }

    private fun persisterSøknadsbehandlingTilAttestering(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, Søknadsbehandling.TilAttestering> = { (sak, søknad) ->
            tilAttesteringSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        },
    ): Pair<Sak, Søknadsbehandling.TilAttestering> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as Søknadsbehandling.TilAttestering
            }
        }
    }

    fun persisterSøknadsbehandlingTilAttesteringInnvilget(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, Søknadsbehandling.TilAttestering.Innvilget> {
        return persisterSøknadsbehandlingTilAttestering(sakOgSøknad).let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as Søknadsbehandling.TilAttestering.Innvilget
        }
    }

    fun persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, Søknadsbehandling.TilAttestering.Avslag.UtenBeregning> {
        return persisterSøknadsbehandlingTilAttestering(sakOgSøknad) { (sak, søknad) ->
            tilAttesteringSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as Søknadsbehandling.TilAttestering.Avslag.UtenBeregning
        }
    }

    fun persisterSøknadsbehandlingTilAttesteringAvslagMedBeregning(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, Søknadsbehandling.TilAttestering.Avslag.MedBeregning> {
        return persisterSøknadsbehandlingTilAttestering(sakOgSøknad) { (sak, søknad) ->
            tilAttesteringSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 60000.0)),
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as Søknadsbehandling.TilAttestering.Avslag.MedBeregning
        }
    }

    fun persisterSøknadsbehandlingIverksatt(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Triple<Sak, Søknadsbehandling.Iverksatt, Stønadsvedtak> = { (sak, søknad) ->
            iverksattSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        },
    ): Triple<Sak, Søknadsbehandling.Iverksatt, Stønadsvedtak> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling, vedtak) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            if (vedtak is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling) {
                databaseRepos.utbetaling.opprettUtbetaling(sak.utbetalinger.single { it.id == vedtak.utbetalingId } as Utbetaling.OversendtUtbetaling.UtenKvittering)
            }
            databaseRepos.vedtakRepo.lagre(vedtak)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                Triple(
                    persistertSak!!,
                    persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as Søknadsbehandling.Iverksatt,
                    persistertSak.vedtakListe.single { it.id == vedtak.id } as Stønadsvedtak,
                )
            }
        }
    }

    fun persisterSøknadsbehandlingIverksattInnvilget(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Triple<Sak, Søknadsbehandling.Iverksatt, Stønadsvedtak> = { (sak, søknad) ->
            iverksattSøknadsbehandlingUføre(
                clock = clock,
                sakInfo = SakInfo(
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    fnr = sak.fnr,
                    type = sak.type,
                ),
                sakOgSøknad = sak to søknad,
            )
        },
    ): Triple<Sak, Søknadsbehandling.Iverksatt, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling> {
        return persisterSøknadsbehandlingIverksatt(sakOgSøknad) { søknadsbehandling(it) }.let { (sak, søknadsbehandling, vedtak) ->
            Triple(
                sak,
                søknadsbehandling as Søknadsbehandling.Iverksatt.Innvilget,
                vedtak as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling,
            )
        }
    }

    fun persisterSøknadsbehandlingIverksattAvslagUtenBeregning(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Triple<Sak, Søknadsbehandling.Iverksatt.Avslag.UtenBeregning, Avslagsvedtak.AvslagVilkår> {
        return persisterSøknadsbehandlingIverksatt(sakOgSøknad) { (sak, søknad) ->
            iverksattSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            )
        }.let { (sak, søknadsbehandling, vedtak) ->
            Triple(
                sak,
                søknadsbehandling as Søknadsbehandling.Iverksatt.Avslag.UtenBeregning,
                vedtak as Avslagsvedtak.AvslagVilkår,
            )
        }
    }

    fun persisterSøknadsbehandlingIverksattAvslagMedBeregning(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Triple<Sak, Søknadsbehandling.Iverksatt.Avslag.MedBeregning, Avslagsvedtak.AvslagBeregning> {
        return persisterSøknadsbehandlingIverksatt(sakOgSøknad) { (sak, søknad) ->
            iverksattSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 60000.0)),
            )
        }.let { (sak, søknadsbehandling, vedtak) ->
            Triple(
                sak,
                søknadsbehandling as Søknadsbehandling.Iverksatt.Avslag.MedBeregning,
                vedtak as Avslagsvedtak.AvslagBeregning,
            )
        }
    }

    private fun persisterSøknadsbehandlingUnderkjent(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
        søknadsbehandling: (sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket>) -> Pair<Sak, Søknadsbehandling.Underkjent> = { (sak, søknad) ->
            underkjentSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        },
    ): Pair<Sak, Søknadsbehandling.Underkjent> {
        return søknadsbehandling(sakOgSøknad).let { (sak, søknadsbehandling) ->
            databaseRepos.søknadsbehandling.lagre(søknadsbehandling)
            databaseRepos.sak.hentSak(sak.id).let { persistertSak ->
                persistertSak!! to persistertSak.søknadsbehandlinger.single { it.id == søknadsbehandling.id } as Søknadsbehandling.Underkjent
            }
        }
    }

    fun persisterSøknadsbehandlingUnderkjentInnvilget(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, Søknadsbehandling.Underkjent.Innvilget> {
        return persisterSøknadsbehandlingUnderkjent(sakOgSøknad) { (sak, søknad) ->
            underkjentSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as Søknadsbehandling.Underkjent.Innvilget
        }
    }

    fun persisterSøknadsbehandlingUnderkjentAvslagUtenBeregning(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, Søknadsbehandling.Underkjent.Avslag.UtenBeregning> {
        return persisterSøknadsbehandlingUnderkjent(sakOgSøknad) { (sak, søknad) ->
            underkjentSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as Søknadsbehandling.Underkjent.Avslag.UtenBeregning
        }
    }

    fun persisterSøknadsbehandlingUnderkjentAvslagMedBeregning(
        sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> = persisterJournalførtSøknadMedOppgave(),
    ): Pair<Sak, Søknadsbehandling.Underkjent.Avslag.MedBeregning> {
        return persisterSøknadsbehandlingUnderkjent(sakOgSøknad) { (sak, søknad) ->
            underkjentSøknadsbehandling(
                sakOgSøknad = sak to søknad,
                clock = clock,
                customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0)),
            )
        }.let { (sak, søknadsbehandling) ->
            sak to søknadsbehandling as Søknadsbehandling.Underkjent.Avslag.MedBeregning
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
        saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    ): Pair<Sak, Søknadsbehandling.Vilkårsvurdert.Uavklart> {
        val (sak, søknad) = persisterJournalførtSøknadMedOppgave(sakId = sakId, søknadId = søknadId)
        assert(sak.id == sakId && sak.søknader.count { it.sakId == sakId && it.id == søknadId } == 1)
        val opprettet = Tidspunkt.now(clock)
        return NySøknadsbehandling(
            id = id,
            opprettet = opprettet,
            sakId = sak.id,
            søknad = søknad,
            oppgaveId = søknad.oppgaveId,
            fnr = sak.fnr,
            avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
            sakstype = sak.type,
            saksbehandler = saksbehandler,
        ).let { nySøknadsbehandling ->
            databaseRepos.søknadsbehandling.lagreNySøknadsbehandling(nySøknadsbehandling)
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
                assert(it.fnr == sak.fnr && it.sakId == sakId)
                Pair(databaseRepos.sak.hentSak(sakId)!!, it as Søknadsbehandling.Vilkårsvurdert.Uavklart)
            }
        }
    }

    fun persisterKlageOpprettet(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
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
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
        oppgaveId: OppgaveId = OppgaveId("underkjentKlageOppgaveId"),
    ): VurdertKlage.Bekreftet {
        return persisterKlageTilAttesteringVurdert(vedtak = vedtak, oppgaveId = oppgaveId).underkjenn(
            underkjentAttestering = Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant(navIdent = "saksbehandlerUnderkjentKlage"),
                opprettet = Tidspunkt.now(clock),
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "underkjennelseskommentar",
            ),
        ) { oppgaveId.right() }.getOrFail().also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageUnderkjentAvvist(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
        oppgaveId: OppgaveId = OppgaveId("underkjentKlageOppgaveId"),
    ): AvvistKlage {
        return persisterKlageTilAttesteringAvvist(vedtak, oppgaveId).underkjenn(
            underkjentAttestering = Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant(navIdent = "saksbehandlerUnderkjentKlage"),
                opprettet = Tidspunkt.now(clock),
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "underkjennelseskommentar",
            ),
        ) { oppgaveId.right() }.getOrFail().also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageOversendt(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
        oppgaveId: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    ): OversendtKlage {
        return persisterKlageTilAttesteringVurdert(vedtak = vedtak, oppgaveId = oppgaveId).oversend(
            iverksattAttestering = Attestering.Iverksatt(
                attestant = NavIdentBruker.Attestant(navIdent = "saksbehandlerOversendtKlage"),
                opprettet = Tidspunkt.now(clock),
            ),
        ).getOrFail().also {
            databaseRepos.klageRepo.lagre(it)
        }
    }

    fun persisterKlageIverksattAvvist(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling = persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
        oppgaveId: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    ): IverksattAvvistKlage {
        return persisterKlageTilAttesteringAvvist(vedtak, oppgaveId).iverksett(
            iverksattAttestering = Attestering.Iverksatt(
                attestant = NavIdentBruker.Attestant(navIdent = "saksbehandlerIverksattAvvistKlage"),
                opprettet = Tidspunkt.now(clock),
            ),
        ).getOrFail().also { databaseRepos.klageRepo.lagre(it) }
    }

    fun persisterUprosessertKlageinstanshendelse(
        id: UUID = UUID.randomUUID(),
        klageId: UUID = UUID.randomUUID(),
        utfall: KlageinstansUtfall = KlageinstansUtfall.STADFESTELSE,
        opprettet: Tidspunkt = Tidspunkt.now(clock),
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
