package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.getOrElse
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakEventObserver
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SøknadRouteMediator(
    private val repo: ObjectRepo,
    private val pdfGenerator: PdfGenerator,
    private val dokArkiv: DokArkiv,
    private val oppgaveClient: OppgaveClient,
    private val personOppslag: PersonOppslag
) : SakEventObserver {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun nySøknad(søknadInnhold: SøknadInnhold): Sak {
        val sak = repo.hentSak(søknadInnhold.personopplysninger.fnr)
            ?: repo.opprettSak(søknadInnhold.personopplysninger.fnr)
        sak.addObserver(this)
        sak.nySøknad(søknadInnhold)

        return repo.hentSak(søknadInnhold.personopplysninger.fnr)!!
    }

    override fun nySøknadEvent(nySøknadEvent: SakEventObserver.NySøknadEvent) {
        pdfGenerator.genererPdf(nySøknadEvent.søknadInnhold).fold(
            {
                log.error("$it")
            },
            { pdfByteArray ->
                val fnr = nySøknadEvent.søknadInnhold.personopplysninger.fnr
                dokArkiv.opprettJournalpost(
                    Journalpost.Søknadspost(
                        person = personOppslag.person(fnr).getOrElse {
                            log.error("Fant ikke person med gitt fødselsnummer")
                            throw RuntimeException("Kunne ikke finne person")
                        },
                        søknadInnhold = nySøknadEvent.søknadInnhold,
                        pdf = pdfByteArray,
                        sakId = nySøknadEvent.sakId.toString()
                    )
                ).fold(
                    {
                        log.error("$it")
                    },
                    { journalpostId ->
                        val aktørId: AktørId = personOppslag.aktørId(fnr).getOrElse {
                            log.error("Fant ikke aktør-id med gitt fødselsnummer")
                            throw RuntimeException("Kunne ikke finne aktørid")
                        }
                        oppgaveClient.opprettOppgave(
                            OppgaveConfig.Saksbehandling(
                                journalpostId = journalpostId,
                                sakId = nySøknadEvent.sakId.toString(),
                                aktørId = aktørId
                            )
                        ).mapLeft {
                            log.error("$it")
                        }
                    }
                )
            }
        )
    }

    fun hentSøknad(id: UUID) = repo.hentSøknad(id)
}
