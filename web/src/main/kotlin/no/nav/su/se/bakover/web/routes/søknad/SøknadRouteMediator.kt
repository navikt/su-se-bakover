package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.getOrElse
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.oppgave.Oppgave
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.*
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SøknadRouteMediator(
    private val repo: ObjectRepo,
    private val pdfGenerator: PdfGenerator,
    private val dokArkiv: DokArkiv,
    private val oppgave: Oppgave,
    private val personOppslag: PersonOppslag
) : SakEventObserver {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun nySøknad(søknadInnhold: SøknadInnhold): Sak {
        val sak = repo.hentSak(Fnr(søknadInnhold.personopplysninger.fnr))
            ?: repo.opprettSak(Fnr(søknadInnhold.personopplysninger.fnr))
        sak.addObserver(this)
        sak.nySøknad(søknadInnhold)

        return repo.hentSak(Fnr(søknadInnhold.personopplysninger.fnr))!!
    }

    override fun nySøknadEvent(nySøknadEvent: SakEventObserver.NySøknadEvent) {
        pdfGenerator.genererPdf(nySøknadEvent.søknadInnhold).fold(
            {
                log.error("$it")
            },
            { pdfByteArray ->
                val fnr = Fnr(nySøknadEvent.søknadInnhold.personopplysninger.fnr)
                dokArkiv.opprettJournalpost(
                    søknadInnhold = nySøknadEvent.søknadInnhold,
                    person = personOppslag.person(fnr).getOrElse { throw RuntimeException("Kunne ikke finne person") },
                    pdf = pdfByteArray,
                    sakId = nySøknadEvent.sakId.toString()
                ).fold(
                    {
                        log.error("$it")
                    },
                    { journalpostId ->
                        val aktørId :AktørId = personOppslag.aktørId(fnr).getOrElse { throw RuntimeException("Kunne ikke finne aktørid") }
                        oppgave.opprettOppgave(
                            journalpostId = journalpostId,
                            sakId = nySøknadEvent.sakId.toString(),
                            aktørId = aktørId.toString()
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
