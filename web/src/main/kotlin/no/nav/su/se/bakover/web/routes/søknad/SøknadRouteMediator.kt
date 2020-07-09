package no.nav.su.se.bakover.web.routes.søknad

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.oppgave.Oppgave
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakEventObserver
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
                dokArkiv.opprettJournalpost(
                    nySøknad = nySøknadEvent.søknadInnhold,
                    sakId = nySøknadEvent.sakId.toString(),
                    pdf = pdfByteArray
                ).fold(
                    {
                        log.error("$it")
                    },
                    { journalpostId ->
                        val aktørId = personOppslag.aktørId(Fnr(nySøknadEvent.søknadInnhold.personopplysninger.fnr))
                        oppgave.opprettOppgave(
                            journalpostId = journalpostId,
                            sakId = nySøknadEvent.sakId.toString(),
                            aktørId = aktørId
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
