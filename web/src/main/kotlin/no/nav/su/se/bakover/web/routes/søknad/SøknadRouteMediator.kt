package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.getOrElse
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.oppgave.Oppgave
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakEventObserver
import no.nav.su.se.bakover.domain.SøknadInnhold
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
        val sak = repo.hentSak(Fnr(søknadInnhold.personopplysninger.fnr.fnr))
            ?: repo.opprettSak(Fnr(søknadInnhold.personopplysninger.fnr.fnr))
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
                dokArkiv.opprettJournalpost(
                    søknadInnhold = nySøknadEvent.søknadInnhold,
                    pdf = pdfByteArray,
                    sakId = nySøknadEvent.sakId.toString()
                ).fold(
                    {
                        log.error("$it")
                    },
                    { journalpostId ->
                        val aktørId = personOppslag.aktørId(nySøknadEvent.søknadInnhold.personopplysninger.fnr)
                        oppgave.opprettOppgave(
                            journalpostId = journalpostId,
                            sakId = nySøknadEvent.sakId.toString(),
                            aktørId = aktørId.getOrElse { throw RuntimeException("Kunne ikke finne aktørid") }.aktørId
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
