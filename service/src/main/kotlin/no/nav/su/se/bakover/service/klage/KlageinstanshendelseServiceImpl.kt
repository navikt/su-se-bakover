package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.flatMap
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.KlageinstanshendelseRepo
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.UprosessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import org.slf4j.LoggerFactory
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

class KlageinstanshendelseServiceImpl(
    private val klageinstanshendelseRepo: KlageinstanshendelseRepo,
    private val klageRepo: KlageRepo,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : KlageinstanshendelseService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagre(hendelse: UprosessertKlageinstanshendelse) {
        klageinstanshendelseRepo.lagre(hendelse)
    }

    override fun håndterUtfallFraKlageinstans(
        deserializeAndMap: (id: UUID, opprettet: Tidspunkt, json: String) -> Either<KunneIkkeTolkeKlageinstanshendelse, TolketKlageinstanshendelse>,
    ) {
        val ubehandletKlageinstanshendelser = klageinstanshendelseRepo.hentUbehandlaKlageinstanshendelser()

        ubehandletKlageinstanshendelser.forEach { uprosessertKlageinstanshendelse ->
            deserializeAndMap(
                uprosessertKlageinstanshendelse.id,
                uprosessertKlageinstanshendelse.opprettet,
                uprosessertKlageinstanshendelse.metadata.value,
            )
                .onLeft {
                    log.error(
                        "Deserialisering av hendelse fra Klageinstans feilet for hendelseId: ${uprosessertKlageinstanshendelse.metadata.hendelseId}",
                        it,
                    )
                    klageinstanshendelseRepo.markerSomFeil(uprosessertKlageinstanshendelse.id)
                }
                .onRight { prosesserTolketKlageinstanshendelse(it) }
        }
    }

    private fun prosesserTolketKlageinstanshendelse(hendelse: TolketKlageinstanshendelse) {
        // TODO jah: Vi har ikke håndtert muligheten for at det kan komme en oversendelse fra klageinstansen mens vi er i en annen tilstand enn [OversendtKlage]
        //  Vi kan løse problemet dersom det dukker opp.
        val klage = klageRepo.hentKlage(hendelse.klageId) as? OversendtKlage

        if (klage == null) {
            log.error("Kunne ikke prosessere melding fra Klageinstans. Fant ikke klage med klageId: ${hendelse.klageId}")
            return klageinstanshendelseRepo.markerSomFeil(hendelse.id)
        }

        klage.leggTilNyKlageinstanshendelse(
            hendelse,
        ) {
            lagOppgaveConfig(
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                utfall = hendelse.utfall,
                journalpostIDer = hendelse.journalpostIDer,
                avsluttetTidspunkt = hendelse.avsluttetTidspunkt,
            )
        }.onLeft {
            when (it) {
                Klage.KunneIkkeLeggeTilNyKlageinstansHendelse.KunneIkkeHenteAktørId -> log.error("Feil skjedde i prosessering av klageinstanshendelse: Kunne ikke hente aktørId for id ${hendelse.id}")
                is Klage.KunneIkkeLeggeTilNyKlageinstansHendelse.KunneIkkeLageOppgave -> log.error("Feil skjedde i prosessering av klageinstanshendelse: Kall mot oppgave feilet for id ${hendelse.id}")
                is Klage.KunneIkkeLeggeTilNyKlageinstansHendelse.MåVæreEnOversendtKlage -> log.error("Feil skjedde i prosessering av klageinstanshendelse: Må være i tilstand ${OversendtKlage::class.java.name} men var ${it.menVar.java.name} for id ${hendelse.id}")
            }
            /* Disse lagres som FEIL i databasen uten videre håndtering. Tanken er at vi får håndtere
             * de casene som intreffer og så må vi manuellt putte de til 'UPROSESSERT' vid senere tidspunkt.
             */
            klageinstanshendelseRepo.markerSomFeil(hendelse.id)
        }
            .onRight {
                when (it) {
                    is OpprettetKlage,
                    is VilkårsvurdertKlage,
                    is AvvistKlage,
                    is KlageTilAttestering,
                    is IverksattAvvistKlage,
                    is AvsluttetKlage,
                    -> {
                        log.error("Støtter kun Vurderte klager, og Oversendte klager i retur fra leggTilNyKlageinstanshendelse(). klageId: ${it.id} ")
                        throw IllegalStateException("Kan ikke lagre klage og prosessert klageinstanshendelse fra tilstand ${it::class}")
                    }

                    is VurdertKlage,
                    -> sessionFactory.withTransactionContext { tx ->
                        klageRepo.lagre(it, tx)
                        klageinstanshendelseRepo.lagre(it.klageinstanshendelser.last(), tx)
                    }

                    is OversendtKlage -> sessionFactory.withTransactionContext { tx ->
                        klageRepo.lagre(it, tx)
                        klageinstanshendelseRepo.lagre(it.klageinstanshendelser.last(), tx)
                    }
                }
                log.info("Prosessert og laget oppgave for klageinstanshendelse med id: ${hendelse.id}")
            }
    }

    private fun lagOppgaveConfig(
        saksnummer: Saksnummer,
        fnr: Fnr,
        utfall: KlageinstansUtfall,
        avsluttetTidspunkt: Tidspunkt,
        journalpostIDer: List<JournalpostId>,
    ): Either<Klage.KunneIkkeLeggeTilNyKlageinstansHendelse, OppgaveId> {
        return personService.hentAktørIdMedSystembruker(fnr).map { aktørId ->
            when (utfall) {
                KlageinstansUtfall.TRUKKET,
                KlageinstansUtfall.AVVIST,
                KlageinstansUtfall.STADFESTELSE,
                -> OppgaveConfig.Klage.Klageinstanshendelse.Informasjon(
                    saksnummer = saksnummer,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                    clock = clock,
                    utfall = utfall,
                    avsluttetTidspunkt = avsluttetTidspunkt,
                    journalpostIDer = journalpostIDer,
                )

                KlageinstansUtfall.RETUR,
                KlageinstansUtfall.OPPHEVET,
                KlageinstansUtfall.MEDHOLD,
                KlageinstansUtfall.DELVIS_MEDHOLD,
                KlageinstansUtfall.UGUNST,
                -> OppgaveConfig.Klage.Klageinstanshendelse.Handling(
                    saksnummer = saksnummer,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                    clock = clock,
                    utfall = utfall,
                    avsluttetTidspunkt = avsluttetTidspunkt,
                    journalpostIDer = journalpostIDer,
                )
            }
        }.mapLeft { Klage.KunneIkkeLeggeTilNyKlageinstansHendelse.KunneIkkeHenteAktørId }
            .flatMap {
                oppgaveService.opprettOppgaveMedSystembruker(it).map {
                    it.oppgaveId
                }.mapLeft { Klage.KunneIkkeLeggeTilNyKlageinstansHendelse.KunneIkkeLageOppgave }
            }
    }
}
