package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.kabal.KlageClient
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.IverksattKlage
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class KlageServiceImpl(
    private val sakRepo: SakRepo,
    private val klageRepo: KlageRepo,
    private val vedtakRepo: VedtakRepo,
    private val brevService: BrevService,
    private val personService: PersonService,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val klageClient: KlageClient,
    private val sessionFactory: SessionFactory,
    private val oppgaveService: OppgaveService,
    val clock: Clock,
) : KlageService {

    override fun opprett(request: NyKlageRequest): Either<KunneIkkeOppretteKlage, OpprettetKlage> {

        val sak = sakRepo.hentSak(request.sakId) ?: return KunneIkkeOppretteKlage.FantIkkeSak.left()

        sak.hentÅpneKlager().ifNotEmpty {
            // TODO jah: Justere denne sjekken når vi har konseptet lukket klage.
            return KunneIkkeOppretteKlage.FinnesAlleredeEnÅpenKlage.left()
        }
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
        }.right()
    }

    override fun vilkårsvurder(request: VurderKlagevilkårRequest): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return request.toDomain().flatMap {
            it.vilkårsvurderinger.vedtakId?.let { vedtakId ->
                if (vedtakRepo.hentForVedtakId(vedtakId) == null) {
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
        return request.toDomain().flatMap {
            val klage = klageRepo.hentKlage(it.klageId) ?: return KunneIkkeVurdereKlage.FantIkkeKlage.left()

            klage.vurder(
                saksbehandler = it.saksbehandler,
                vurderinger = it.vurderinger,
            ).tap { vurdertKlage ->
                klageRepo.lagre(vurdertKlage)
            }
        }
    }

    override fun bekreftVurderinger(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VurdertKlage.Bekreftet> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeBekrefteKlagesteg.FantIkkeKlage.left()

        return klage.bekreftVurderinger(saksbehandler = saksbehandler).tap {
            klageRepo.lagre(it)
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
                    OppgaveConfig.Klage.Saksbehandler(
                        saksnummer = klage.saksnummer,
                        aktørId = aktørId,
                        journalpostId = klage.journalpostId,
                        tilordnetRessurs = (klage as? VurdertKlage)?.attesteringer?.map { it.attestant }?.lastOrNull(),
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

    override fun underkjenn(request: UnderkjennKlageRequest): Either<KunneIkkeUnderkjenne, VurdertKlage.Bekreftet> {
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

    override fun iverksett(
        sakId: UUID,
        klageId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteKlage, IverksattKlage> {
        val sak = sakRepo.hentSak(sakId) ?: return KunneIkkeIverksetteKlage.FantIkkeSak.left()
        val klage = sak.hentKlage(klageId) ?: return KunneIkkeIverksetteKlage.FantIkkeKlage.left()

        val iverksattKlage = klage.iverksett(
            Attestering.Iverksatt(
                attestant = attestant,
                opprettet = Tidspunkt.now(clock),
            ),
        ).getOrHandle { return it.left() }

        val dokument = lagBrevRequest(iverksattKlage, sak.fnr).fold(
            ifLeft = { return KunneIkkeIverksetteKlage.KunneIkkeLageBrevRequest.left() },
            ifRight = {
                it.tilDokument { brevRequest ->
                    brevService.lagBrev(brevRequest).mapLeft { LagBrevRequest.KunneIkkeGenererePdf }
                }
            },
        ).map {
            it.leggTilMetadata(
                Dokument.Metadata(
                    klageId = klage.id,
                    sakId = klage.sakId,
                    bestillBrev = true,
                ),
            )
        }.getOrHandle { return KunneIkkeIverksetteKlage.DokumentGenereringFeilet.left() }

        val journalpostIdForVedtak = vedtakRepo.hentJournalpostId(iverksattKlage.vilkårsvurderinger.vedtakId)
            ?: return KunneIkkeIverksetteKlage.FantIkkeVedtak.left()

        sessionFactory.withTransactionContext {
            brevService.lagreDokument(dokument, it)
            klageRepo.lagre(iverksattKlage, it)

            klageClient.sendTilKlageinstans(iverksattKlage, sak, journalpostIdForVedtak)
                .getOrHandle { throw RuntimeException("Kall mot kabal feilet") }
        }
        oppgaveService.lukkOppgave(iverksattKlage.oppgaveId)
        return iverksattKlage.right()
    }

    override fun brevutkast(
        sakId: UUID,
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
        hjemler: Hjemler.Utfylt,
    ): Either<KunneIkkeLageBrevutkast, ByteArray> {
        val sak = sakRepo.hentSak(sakId) ?: return KunneIkkeLageBrevutkast.FantIkkeSak.left()
        val klage = sak.hentKlage(klageId) ?: return KunneIkkeLageBrevutkast.FantIkkeKlage.left()

        val vedtaksdato =
            klageRepo.hentKnyttetVedtaksdato(klageId) ?: return KunneIkkeLageBrevutkast.FantIkkeKnyttetVedtak.left()
        val saksbehandlerNavn = microsoftGraphApiClient.hentNavnForNavIdent(saksbehandler)
            .getOrElse { return KunneIkkeLageBrevutkast.FantIkkeSaksbehandler.left() }

        return personService.hentPerson(sak.fnr)
            .fold(
                ifLeft = { KunneIkkeLageBrevutkast.FantIkkePerson.left() },
                ifRight = { person ->
                    val brevRequest = lagBrevRequestForOppretthold(
                        person,
                        saksbehandlerNavn,
                        hjemler,
                        fritekst,
                        klage.datoKlageMottatt,
                        vedtaksdato,
                    )
                    brevService.lagBrev(brevRequest)
                        .mapLeft { KunneIkkeLageBrevutkast.GenereringAvBrevFeilet }
                },
            )
    }

    private fun lagBrevRequest(
        klage: IverksattKlage,
        fnr: Fnr,
    ): Either<KunneIkkeLageBrevRequest, LagBrevRequest.Klage.Oppretthold> {
        val saksbehandlerNavn = microsoftGraphApiClient.hentNavnForNavIdent(klage.saksbehandler)
            .getOrElse { return KunneIkkeLageBrevRequest.FantIkkeSaksbehandler.left() }
        val vedtakDato =
            klageRepo.hentKnyttetVedtaksdato(klage.id) ?: return KunneIkkeLageBrevRequest.FantIkkeKnyttetVedtak.left()

        return personService.hentPerson(fnr)
            .fold(
                ifLeft = { KunneIkkeLageBrevRequest.FantIkkePerson.left() },
                ifRight = { person ->
                    when (val vurdering = klage.vurderinger.vedtaksvurdering) {
                        is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Omgjør -> throw RuntimeException("Har ikke støtte for Omgjør")
                        is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold -> lagBrevRequestForOppretthold(
                            person = person,
                            saksbehandlerNavn = saksbehandlerNavn,
                            hjemler = vurdering.hjemler,
                            fritekst = klage.vurderinger.fritekstTilBrev,
                            klageDato = klage.datoKlageMottatt,
                            vedtakDato = vedtakDato,
                        ).right()
                    }
                },
            )
    }

    private fun lagBrevRequestForOppretthold(
        person: Person,
        saksbehandlerNavn: String,
        hjemler: Hjemler.Utfylt,
        fritekst: String,
        klageDato: LocalDate,
        vedtakDato: LocalDate,
    ): LagBrevRequest.Klage.Oppretthold {
        return LagBrevRequest.Klage.Oppretthold(
            person = person,
            dagensDato = LocalDate.now(clock),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = fritekst,
            hjemler = hjemler.map {
                it.paragrafnummer
            },
            klageDato = klageDato,
            vedtakDato = vedtakDato,
        )
    }
}
