package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.journalpost.ErTilknyttetSak
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkeTilknytningTilSak
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.KanBekrefteKlagevurdering
import no.nav.su.se.bakover.domain.klage.KanLeggeTilFritekstTilAvvistBrev
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageSomKanVurderes
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeAvslutteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteAvvistKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLeggeTilFritekstForAvvist
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class KlageServiceImpl(
    private val sakRepo: SakRepo,
    private val klageRepo: KlageRepo,
    private val vedtakService: VedtakService,
    private val brevService: BrevService,
    private val personService: PersonService,
    private val identClient: IdentClient,
    private val klageClient: KlageClient,
    private val sessionFactory: SessionFactory,
    private val oppgaveService: OppgaveService,
    private val journalpostClient: JournalpostClient,
    val clock: Clock,
) : KlageService {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(observer: StatistikkEventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<StatistikkEventObserver> = observers.toList()

    override fun opprett(request: NyKlageRequest): Either<KunneIkkeOppretteKlage, OpprettetKlage> {
        request.validate().getOrHandle { return it.left() }
        val sak = sakRepo.hentSak(request.sakId) ?: return KunneIkkeOppretteKlage.FantIkkeSak.left()

        if (!sak.kanOppretteKlage()) {
            return KunneIkkeOppretteKlage.FinnesAlleredeEnÅpenKlage.left()
        }
        runBlocking {
            journalpostClient.erTilknyttetSak(request.journalpostId, sak.saksnummer)
        }.fold(
            {
                return KunneIkkeOppretteKlage.FeilVedHentingAvJournalpost(it).left()
            },
            {
                when (it) {
                    ErTilknyttetSak.Ja -> {
                        /*sjekk ok, trenger ikke gjøre noe mer*/
                    }
                    ErTilknyttetSak.Nei -> {
                        return KunneIkkeOppretteKlage.FeilVedHentingAvJournalpost(KunneIkkeSjekkeTilknytningTilSak.JournalpostIkkeKnyttetTilSak).left()
                    }
                }
            },
        )

        val aktørId = personService.hentAktørId(sak.fnr).getOrElse {
            return KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave.left()
        }
        val oppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.Klage.Saksbehandler(
                saksnummer = sak.saksnummer,
                aktørId = aktørId,
                journalpostId = request.journalpostId,
                tilordnetRessurs = null,
                clock = clock,
            ),
        ).getOrHandle {
            return KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave.left()
        }
        // Dette er greit så lenge toKlage ikke kan feile. På det tidspunktet må vi gjøre om rekkefølgen.
        return request.toKlage(
            saksnummer = sak.saksnummer,
            fnr = sak.fnr,
            oppgaveId = oppgaveId,
            clock = clock,
        ).also {
            klageRepo.lagre(it)
            observers.notify(StatistikkEvent.Behandling.Klage.Opprettet(it))
        }.right()
    }

    override fun vilkårsvurder(request: VurderKlagevilkårRequest): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return request.toDomain().flatMap {
            it.vilkårsvurderinger.vedtakId?.let { vedtakId ->
                if (vedtakService.hentForVedtakId(vedtakId) == null) {
                    return KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak.left()
                }
            }
            val klage = klageRepo.hentKlage(it.klageId) ?: return KunneIkkeVilkårsvurdereKlage.FantIkkeKlage.left()
            klage.vilkårsvurder(
                saksbehandler = it.saksbehandler,
                vilkårsvurderinger = it.vilkårsvurderinger,
            )
        }.tap {
            klageRepo.lagre(it)
        }
    }

    override fun bekreftVilkårsvurderinger(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VilkårsvurdertKlage.Bekreftet> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeBekrefteKlagesteg.FantIkkeKlage.left()

        return klage.bekreftVilkårsvurderinger(
            saksbehandler = saksbehandler,
        ).tap {
            klageRepo.lagre(it)
        }
    }

    override fun vurder(request: KlageVurderingerRequest): Either<KunneIkkeVurdereKlage, VurdertKlage> {
        return request.toDomain().flatMap { r ->
            klageRepo.hentKlage(r.klageId)
                .rightIfNotNull { KunneIkkeVurdereKlage.FantIkkeKlage }
                .flatMap {
                    (it as? KlageSomKanVurderes)
                        ?.right()
                        ?: KunneIkkeVurdereKlage.UgyldigTilstand(it::class).left()
                }.map {
                    it.vurder(
                        saksbehandler = r.saksbehandler,
                        vurderinger = r.vurderinger,
                    ).also { vurdertKlage ->
                        klageRepo.lagre(vurdertKlage)
                    }
                }
        }
    }

    override fun bekreftVurderinger(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VurdertKlage.Bekreftet> {
        return klageRepo.hentKlage(klageId)
            .rightIfNotNull { KunneIkkeBekrefteKlagesteg.FantIkkeKlage }
            .flatMap {
                (it as? KanBekrefteKlagevurdering)
                    ?.right()
                    ?: KunneIkkeBekrefteKlagesteg.UgyldigTilstand(it::class, VurdertKlage.Bekreftet::class).left()
            }.map {
                it.bekreftVurderinger(
                    saksbehandler = saksbehandler,
                ).also { bekreftetVurdertKlage ->
                    klageRepo.lagre(bekreftetVurdertKlage)
                }
            }
    }

    override fun leggTilAvvistFritekstTilBrev(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): Either<KunneIkkeLeggeTilFritekstForAvvist, AvvistKlage> {
        return klageRepo.hentKlage(klageId)
            .rightIfNotNull { KunneIkkeLeggeTilFritekstForAvvist.FantIkkeKlage }
            .flatMap {
                (it as? KanLeggeTilFritekstTilAvvistBrev)
                    ?.right()
                    ?: KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand(it::class).left()
            }.map {
                it.leggTilFritekstTilAvvistVedtaksbrev(
                    saksbehandler = saksbehandler,
                    fritekstTilAvvistVedtaksbrev = fritekst,
                ).also { avvistKlage ->
                    klageRepo.lagre(avvistKlage)
                }
            }
    }

    override fun sendTilAttestering(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeSendeTilAttestering.FantIkkeKlage.left()
        val oppgaveIdSomSkalLukkes = klage.oppgaveId
        return klage.sendTilAttestering(saksbehandler) {
            personService.hentAktørId(klage.fnr).flatMap { aktørId ->
                oppgaveService.opprettOppgave(
                    OppgaveConfig.Klage.Attestering(
                        saksnummer = klage.saksnummer,
                        aktørId = aktørId,
                        journalpostId = klage.journalpostId,
                        tilordnetRessurs = when (klage) {
                            is VurdertKlage -> klage.attesteringer.map { it.attestant }
                                .lastOrNull()

                            is AvvistKlage -> klage.attesteringer.map { it.attestant }
                                .lastOrNull()

                            else -> null
                        },
                        clock = clock,
                    ),
                )
            }.mapLeft {
                KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave
            }
        }.tap {
            klageRepo.lagre(it)
            oppgaveService.lukkOppgave(oppgaveIdSomSkalLukkes)
        }
    }

    override fun underkjenn(request: UnderkjennKlageRequest): Either<KunneIkkeUnderkjenne, Klage> {
        val klage = klageRepo.hentKlage(request.klageId) ?: return KunneIkkeUnderkjenne.FantIkkeKlage.left()
        val oppgaveIdSomSkalLukkes = klage.oppgaveId
        return klage.underkjenn(
            underkjentAttestering = Attestering.Underkjent(
                attestant = request.attestant,
                opprettet = Tidspunkt.now(clock),
                grunn = request.grunn,
                kommentar = request.kommentar,
            ),
        ) {
            personService.hentAktørId(klage.fnr).flatMap {
                oppgaveService.opprettOppgave(
                    OppgaveConfig.Klage.Saksbehandler(
                        saksnummer = klage.saksnummer,
                        aktørId = it,
                        journalpostId = klage.journalpostId,
                        tilordnetRessurs = klage.saksbehandler,
                        clock = clock,
                    ),
                )
            }.mapLeft {
                KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave
            }
        }.tap {
            klageRepo.lagre(it)
            oppgaveService.lukkOppgave(oppgaveIdSomSkalLukkes)
        }
    }

    override fun oversend(
        klageId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeOversendeKlage, OversendtKlage> {
        val klage = (klageRepo.hentKlage(klageId) ?: return KunneIkkeOversendeKlage.FantIkkeKlage.left()).let {
            it as? KlageTilAttestering.Vurdert ?: return KunneIkkeOversendeKlage.UgyldigTilstand(it::class)
                .left()
        }

        val oversendtKlage = klage.oversend(
            Attestering.Iverksatt(
                attestant = attestant,
                opprettet = Tidspunkt.now(clock),
            ),
        ).getOrHandle { return it.left() }

        val dokument = klage.lagBrevRequest(
            hentNavnForNavIdent = { identClient.hentNavnForNavIdent(klage.saksbehandler) },
            hentVedtakDato = { klageRepo.hentKnyttetVedtaksdato(klage.id) },
            hentPerson = { personService.hentPerson(klage.fnr) },
            clock = clock,
        ).mapLeft {
            return KunneIkkeOversendeKlage.KunneIkkeLageBrevRequest(it).left()
        }.flatMap {
            it.tilDokument(clock) { brevRequest ->
                brevService.lagBrev(brevRequest).mapLeft {
                    LagBrevRequest.KunneIkkeGenererePdf
                }
            }.mapLeft {
                KunneIkkeLageBrevForKlage.KunneIkkeGenererePDF
            }.map { dokumentUtenMetadata ->
                dokumentUtenMetadata.leggTilMetadata(
                    Dokument.Metadata(
                        klageId = klage.id,
                        sakId = klage.sakId,
                        bestillBrev = true,
                    ),
                )
            }
        }.getOrHandle {
            return KunneIkkeOversendeKlage.KunneIkkeLageBrev(it).left()
        }

        val journalpostIdForVedtak = vedtakService.hentJournalpostId(oversendtKlage.vilkårsvurderinger.vedtakId)
            ?: return KunneIkkeOversendeKlage.FantIkkeJournalpostIdKnyttetTilVedtaket.left().tapLeft {
                log.error("Kunne ikke iverksette klage ${oversendtKlage.id} fordi vi ikke fant journalpostId til vedtak ${oversendtKlage.vilkårsvurderinger.vedtakId} (kan tyde på at klagen er knyttet til et vedtak vi ikke har laget brev for eller at databasen er i en ugyldig tilstand.)")
            }

        class KunneIkkeOversendeTilKlageinstansEx : RuntimeException()
        try {
            sessionFactory.withTransactionContext {
                brevService.lagreDokument(dokument, it)
                klageRepo.lagre(oversendtKlage, it)

                klageClient.sendTilKlageinstans(
                    klage = oversendtKlage,
                    journalpostIdForVedtak = journalpostIdForVedtak,
                ).getOrHandle { throw KunneIkkeOversendeTilKlageinstansEx() }
            }
        } catch (_: KunneIkkeOversendeTilKlageinstansEx) {
            return KunneIkkeOversendeKlage.KunneIkkeOversendeTilKlageinstans.left()
        }
        oppgaveService.lukkOppgave(oversendtKlage.oppgaveId)
        observers.notify(StatistikkEvent.Behandling.Klage.Oversendt(oversendtKlage))
        return oversendtKlage.right()
    }

    override fun iverksettAvvistKlage(
        klageId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteAvvistKlage, IverksattAvvistKlage> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeIverksetteAvvistKlage.FantIkkeKlage.left()

        if (klage !is KlageTilAttestering.Avvist) {
            return KunneIkkeIverksetteAvvistKlage.UgyldigTilstand(
                klage::class,
            ).left()
        }

        val avvistKlage = klage.iverksett(
            Attestering.Iverksatt(
                attestant = attestant,
                opprettet = Tidspunkt.now(clock),
            ),
        ).getOrHandle {
            return it.left()
        }

        val vedtak = Klagevedtak.Avvist.fromIverksattAvvistKlage(avvistKlage, clock)
        val dokument = klage.lagBrevRequest(
            hentNavnForNavIdent = { identClient.hentNavnForNavIdent(klage.saksbehandler) },
            hentVedtakDato = { klageRepo.hentKnyttetVedtaksdato(klage.id) },
            hentPerson = { personService.hentPerson(klage.fnr) },
            clock = clock,
        ).mapLeft {
            return KunneIkkeIverksetteAvvistKlage.KunneIkkeLageBrevRequest(it).left()
        }.flatMap {
            it.tilDokument(clock) { brevRequest ->
                brevService.lagBrev(brevRequest).mapLeft {
                    LagBrevRequest.KunneIkkeGenererePdf
                }
            }.mapLeft {
                KunneIkkeLageBrevForKlage.KunneIkkeGenererePDF
            }.map { dokumentUtenMetadata ->
                dokumentUtenMetadata.leggTilMetadata(
                    Dokument.Metadata(
                        klageId = klage.id,
                        sakId = klage.sakId,
                        vedtakId = vedtak.id,
                        bestillBrev = true,
                    ),
                )
            }
        }.getOrHandle {
            return KunneIkkeIverksetteAvvistKlage.KunneIkkeLageBrev(it).left()
        }

        try {
            sessionFactory.withTransactionContext {
                klageRepo.lagre(avvistKlage, it)
                vedtakService.lagre(vedtak)
                brevService.lagreDokument(dokument, it)
            }
        } catch (_: Exception) {
            return KunneIkkeIverksetteAvvistKlage.FeilVedLagringAvDokumentOgKlage.left()
        }

        oppgaveService.lukkOppgave(avvistKlage.oppgaveId)
        observers.notify(StatistikkEvent.Behandling.Klage.Avvist(vedtak))
        return avvistKlage.right()
    }

    override fun brevutkast(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLageBrevutkast, ByteArray> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeLageBrevutkast.FantIkkeKlage.left()

        return klage.lagBrevRequest(
            hentNavnForNavIdent = { identClient.hentNavnForNavIdent(saksbehandler) },
            hentVedtakDato = { klageRepo.hentKnyttetVedtaksdato(klage.id) },
            hentPerson = { personService.hentPerson(klage.fnr) },
            clock = clock,
        ).mapLeft {
            KunneIkkeLageBrevutkast.FeilVedBrevRequest(it)
        }.flatMap {
            brevService.lagBrev(it).mapLeft { kunneIkkeLageBrev ->
                when (kunneIkkeLageBrev) {
                    no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev.FantIkkePerson -> KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(
                        KunneIkkeLageBrevForKlage.FantIkkePerson,
                    )

                    no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev.KunneIkkeGenererePDF -> KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(
                        KunneIkkeLageBrevForKlage.KunneIkkeGenererePDF,
                    )
                }
            }
        }
    }

    override fun avslutt(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
    ): Either<KunneIkkeAvslutteKlage, AvsluttetKlage> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeAvslutteKlage.FantIkkeKlage.left()

        return klage.avslutt(
            saksbehandler = saksbehandler,
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = Tidspunkt.now(clock),
        ).tap {
            klageRepo.lagre(it)
            oppgaveService.lukkOppgave(it.oppgaveId)
            observers.notify(StatistikkEvent.Behandling.Klage.Avsluttet(it))
        }
    }
}
