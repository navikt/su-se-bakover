package no.nav.su.se.bakover.web.tilbakekreving

import dokument.domain.hendelser.DistribuertDokumentHendelse
import dokument.domain.hendelser.GenerertDokumentHendelse
import dokument.domain.hendelser.JournalførtDokumentHendelse
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.dokument.application.consumer.DistribuerDokumentHendelserKonsument
import no.nav.su.se.bakover.dokument.application.consumer.JournalførDokumentHendelserKonsument
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsekonsumenterPostgresRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import tilbakekreving.application.service.consumer.GenererDokumentForForhåndsvarselTilbakekrevingKonsument
import tilbakekreving.application.service.consumer.LukkOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.application.service.consumer.OppdaterOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.application.service.consumer.OpprettOppgaveForTilbakekrevingshendelserKonsument
import java.util.UUID

/**
 *
 */
internal fun AppComponents.kjøreAlleTilbakekrevingskonsumenter() {
    // --- oppgaver ---
    this.kjørOpprettOppgaveKonsument()
    // TODO tilbakekreving jah: Fjern versjonsgreiene fra verifiseringa.
    this.oppdaterOppgave(1)
    this.lukkOppgave(1)

    // --- dokumenter ---
    this.genererDokumenterForForhåndsvarsel(1)
    this.journalførDokumenter(1)
    this.distribuerDokumenter(1)
}

/**
 * Merk at dette er totalen, så du må ta høyde for alle steg.
 */
internal fun AppComponents.kjøreAlleVerifiseringer(
    sakId: String,
    antallOpprettetOppgaver: Int = 0,
    antallOppdatertOppgaveHendelser: Int = 0,
    antallLukketOppgaver: Int = 0,
    antallGenererteForhåndsvarsler: Int = 0,
    antallJournalførteDokumenter: Int = 0,
    antallDistribuertDokumenter: Int = 0,
) {
    this.verifiserOpprettetOppgaveKonsument(antallOpprettetOppgaver)
    this.verifiserOppdatertOppgaveKonsument(antallOppdatertOppgaveHendelser)
    this.verifiserLukketOppgaveKonsument(antallLukketOppgaver)
    this.verifiserOppgaveHendelser(
        sakId = sakId,
        antallOppdaterteOppgaver = antallOppdatertOppgaveHendelser,
        antallLukketOppgaver = antallLukketOppgaver,
    )

    this.verifiserGenererDokumentForForhåndsvarselKonsument(antallGenererteForhåndsvarsler)
    this.verifiserJournalførDokumenterKonsument(antallJournalførteDokumenter)
    this.verifiserDistribuerteDokumenterKonsument(antallDistribuertDokumenter)
    this.verifiserDokumentHendelser(
        sakId = sakId,
        antallGenererteDokumenter = antallGenererteForhåndsvarsler,
        antallJournalførteDokumenter = antallJournalførteDokumenter,
        antallDistribuerteDokumenter = antallDistribuertDokumenter,
    )
}

/**
 * Kjører [OpprettOppgaveForTilbakekrevingshendelserKonsument].
 *
 * @return Denne funksjonen bumper saksversjon med 1 uavhengig om vi faktisk oppretter en oppgave eller ikke.
 */
internal fun AppComponents.kjørOpprettOppgaveKonsument() {
    this.tilbakekrevingskomponenter.services.opprettOppgaveForTilbakekrevingshendelserKonsument.opprettOppgaver(
        correlationId = CorrelationId.generate(),
    )
}

/**
 * Kjører [OppdaterOppgaveForTilbakekrevingshendelserKonsument].
 *
 * @return Denne funksjonen bumper saksversjon med 1 uavhengig om vi faktisk oppretter en oppgave eller ikke.
 */
internal fun AppComponents.oppdaterOppgave(saksversjon: Long): Long {
    this.tilbakekrevingskomponenter.services.oppdaterOppgaveForTilbakekrevingshendelserKonsument.oppdaterOppgaver(
        correlationId = CorrelationId.generate(),
    )
    return saksversjon + 1
}

/**
 * Kjører [LukkOppgaveForTilbakekrevingshendelserKonsument].
 *
 * @return Denne funksjonen bumper saksversjon med 1 uavhengig om vi faktisk oppretter en oppgave eller ikke.
 */
internal fun AppComponents.lukkOppgave(saksversjon: Long): Long {
    this.tilbakekrevingskomponenter.services.lukkOppgaveForTilbakekrevingshendelserKonsument.lukkOppgaver(
        correlationId = CorrelationId.generate(),
    )
    return saksversjon + 1
}

/**
 * Kjører [GenererDokumentForForhåndsvarselTilbakekrevingKonsument].
 *
 * @return Denne funksjonen bumper saksversjon med 1 uavhengig om vi faktisk oppretter en oppgave eller ikke.
 */
internal fun AppComponents.genererDokumenterForForhåndsvarsel(saksversjon: Long): Long {
    this.tilbakekrevingskomponenter.services.genererDokumentForForhåndsvarselTilbakekrevingKonsument.genererDokumenter(
        correlationId = CorrelationId.generate(),
    )
    return saksversjon + 1
}

/**
 * Kjører [JournalførDokumentHendelserKonsument].
 *
 * @return Denne funksjonen bumper saksversjon med 1 uavhengig om vi faktisk oppretter en oppgave eller ikke.
 */
internal fun AppComponents.journalførDokumenter(saksversjon: Long): Long {
    this.dokumentHendelseKomponenter.services.journalførtDokumentHendelserKonsument.journalførDokumenter(
        correlationId = CorrelationId.generate(),
    )
    return saksversjon + 1
}

/**
 * Kjører [DistribuerDokumentHendelserKonsument].
 *
 * @return Denne funksjonen bumper saksversjon med 1 uavhengig om vi faktisk oppretter en oppgave eller ikke.
 */
internal fun AppComponents.distribuerDokumenter(saksversjon: Long): Long {
    this.dokumentHendelseKomponenter.services.distribuerDokumentHendelserKonsument.distribuer(
        correlationId = CorrelationId.generate(),
    )
    return saksversjon + 1
}

internal fun AppComponents.verifiserJournalførDokumenterKonsument(antallJournalførteDokumenter: Int) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'JournalførDokumentHendelserKonsument'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size shouldBe antallJournalførteDokumenter
        }
    }
}

internal fun AppComponents.verifiserDistribuerteDokumenterKonsument(antallDistribuerteDokumenter: Int) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'DistribuerDokumentHendelserKonsument'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size shouldBe antallDistribuerteDokumenter
        }
    }
}

internal fun AppComponents.verifiserDokumentHendelser(
    sakId: String,
    antallGenererteDokumenter: Int,
    antallJournalførteDokumenter: Int,
    antallDistribuerteDokumenter: Int,
) {
    val dokumenter = this.databaseRepos.dokumentHendelseRepo.hentForSak(UUID.fromString(sakId))
    dokumenter.size shouldBe antallGenererteDokumenter + antallJournalførteDokumenter + antallDistribuerteDokumenter

    dokumenter.filterIsInstance<GenerertDokumentHendelse>().size shouldBe antallGenererteDokumenter
    dokumenter.filterIsInstance<JournalførtDokumentHendelse>().size shouldBe antallJournalførteDokumenter
    dokumenter.filterIsInstance<DistribuertDokumentHendelse>().size shouldBe antallDistribuerteDokumenter
}

internal fun AppComponents.verifiserOppgaveHendelser(
    sakId: String,
    antallOppdaterteOppgaver: Int,
    antallLukketOppgaver: Int,
) {
    val oppgaveHendelser = this.databaseRepos.oppgaveHendelseRepo.hentForSak(UUID.fromString(sakId))
    // nr 1 er opprettet
    oppgaveHendelser.size shouldBe 1 + antallOppdaterteOppgaver + antallLukketOppgaver

    // en serie med tilbakekreving vil bare ha kun 1 opprettet oppgave
    oppgaveHendelser.filterIsInstance<OppgaveHendelse.Opprettet>().single()
    oppgaveHendelser.filterIsInstance<OppgaveHendelse.Oppdatert>().size shouldBe antallOppdaterteOppgaver
    oppgaveHendelser.filterIsInstance<OppgaveHendelse.Lukket>().size shouldBe antallLukketOppgaver
}

internal fun AppComponents.verifiserOpprettetOppgaveKonsument(antallOpprettetOppgaver: Int = 1) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'OpprettOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size.also {
                withClue("Forventet $antallOpprettetOppgaver rader med konsumentId OpprettOppgaveForTilbakekrevingsbehandlingHendelser, men var $it") {
                    it shouldBe antallOpprettetOppgaver
                }
            }
            """
                select * from hendelse where type = 'OPPRETTET_TILBAKEKREVINGSBEHANDLING'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size.let {
                withClue("Forventet $antallOpprettetOppgaver hendelser med type OPPRETTET_TILBAKEKREVINGSBEHANDLING, men var $it") {
                    it shouldBe antallOpprettetOppgaver
                }
            }
        }
    }
}

/**
 * Sletter ikke selve hendelsen, men kun verifikasjonen på at vi har kjørt konsumenten.
 * Dette for å verifisere at vi ikke oppretter oppgaver på nytt selvom dette skjer.
 * Siden vi skal ha en dedup. på hendelsene så vi oppretter oppgaver på nytt.
 */
internal fun AppComponents.slettOpprettetOppgaveKonsumentJobb() {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                delete * from hendelse_konsument where konsumentId = 'OpprettOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().oppdatering(emptyMap(), it)
        }
    }
}

internal fun AppComponents.verifiserOppdatertOppgaveKonsument(antallOppdatertHendelser: Int) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'OppdaterOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size shouldBe antallOppdatertHendelser
        }
    }
}

/**
 * Sletter ikke selve hendelsen, men kun verifikasjonen på at vi har kjørt konsumenten.
 * Dette for å verifisere at vi ikke oppretter oppgaver på nytt selvom dette skjer.
 * Siden vi skal ha en dedup. på hendelsene så vi oppretter oppgaver på nytt.
 */
internal fun AppComponents.slettOppdatertOppgaveKonsumentJobb() {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                delete * from hendelse_konsument where konsumentId = 'OppdaterOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().oppdatering(emptyMap(), it)
        }
    }
}

internal fun AppComponents.verifiserLukketOppgaveKonsument(antallLukketOppgaver: Int = 1) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'LukkOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size shouldBe antallLukketOppgaver
        }
    }
}

/**
 * Sletter ikke selve hendelsen, men kun verifikasjonen på at vi har kjørt konsumenten.
 * Dette for å verifisere at vi ikke oppretter oppgaver på nytt selvom dette skjer.
 * Siden vi skal ha en dedup. på hendelsene så vi oppretter oppgaver på nytt.
 */
internal fun AppComponents.slettLukketOppgaveKonsumentJobb() {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                delete * from hendelse_konsument where konsumentId = 'LukkOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().oppdatering(emptyMap(), it)
        }
    }
}

internal fun AppComponents.verifiserGenererDokumentForForhåndsvarselKonsument(antallGenerert: Int = 1) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'GenererDokumentForForhåndsvarselTilbakekrevingKonsument'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size shouldBe antallGenerert
        }
    }
}

/**
 * Sletter ikke selve hendelsen, men kun verifikasjonen på at vi har kjørt konsumenten.
 * Dette for å verifisere at vi ikke oppretter oppgaver på nytt selvom dette skjer.
 * Siden vi skal ha en dedup. på hendelsene så vi oppretter oppgaver på nytt.
 */
internal fun AppComponents.slettGenererDokumentForForhåndsvarselKonsumentJobb() {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                delete * from hendelse_konsument where konsumentId = 'GenererDokumentForForhåndsvarselTilbakekrevingKonsument'
            """.trimIndent().oppdatering(emptyMap(), it)
        }
    }
}

internal fun verifiserKravgrunnlagPåSak(
    sakId: String,
    client: HttpClient,
    forventerKravgrunnlag: Boolean,
    versjon: Int,
) {
    hentSak(sakId, client = client).also { sakJson ->
        // Kravgrunnlaget vil være utestående så lenge vi ikke har iverksatt tilbakekrevingsbehandlingen.
        JSONObject(sakJson).isNull("uteståendeKravgrunnlag") shouldBe !forventerKravgrunnlag
        JSONObject(sakJson).getInt("versjon") shouldBe versjon
    }
}
