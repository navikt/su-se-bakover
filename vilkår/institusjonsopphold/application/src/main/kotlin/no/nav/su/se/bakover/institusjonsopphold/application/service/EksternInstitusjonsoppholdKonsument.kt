package no.nav.su.se.bakover.institusjonsopphold.application.service

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.whenever
import no.nav.su.se.bakover.common.jsonNode
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Behandlinger.Companion.harBehandlingUnderArbeid
import no.nav.su.se.bakover.domain.hentSisteHendelse
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje.Companion.harInnvilgelse
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje.Companion.harStans
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.institusjonsopphold.application.json.EksternInstitusjonsoppholdHendelseJson
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * 1. Mottar eksterne institusjonsoppholdshendelser
 * 2. Avgjør om personen er knyttet til en av våre saker
 * 3.
 */
class EksternInstitusjonsoppholdKonsument(
    private val institusjonsoppholdHendelseRepo: InstitusjonsoppholdHendelseRepo,
    private val sakRepo: SakRepo,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun process(hendelseRå: String, correlationId: CorrelationId) {
        val jsontreeAvHendelse = jsonNode(hendelseRå)
        // Vi ønsker ikke å deserialisere hele før vi vet om hendelsen er relevant for oss.
        val norskIdentForHendelse = jsontreeAvHendelse.get("norskident")?.textValue() ?: throw IllegalStateException("NorskIdentForHendelse er null i hendelse")
        val norskIdent = Fnr.tryCreate(norskIdentForHendelse)
            ?: throw IllegalArgumentException("Ugyldig norskident i institusjonsoppholdshendelse")
        sakRepo.hentSaker(norskIdent).ifNotEmpty {
            val hendelseRå = deserialize<EksternInstitusjonsoppholdHendelseJson>(hendelseRå)
            if (hendelseRå.kilde == null) {
                log.warn("Hendelse ${hendelseRå.hendelseId} for opphold ${hendelseRå.oppholdId} har kilde=null. Dette er ikke forventet, og det er avtalt med team-rocket at de trygt kan ignorereres")
                return@process
            }
            val hendelse = hendelseRå.toDomain()

            this.forEach { sak ->
                institusjonsoppholdHendelseRepo.hentForSak(sak.id).let {
                    if (it.harEksternHendelse(hendelse.hendelseId)) {
                        log.info("Hopper over ekstern institusjonshendelse ${hendelse.hendelseId} - denne hendelsen har vi allerede lagret.")
                        return
                    }
                }
                sak.harBehandlingUnderArbeidEllerVedtakSomGirGrunnlagForInstHendelse(clock).ifTrue {
                    institusjonsoppholdHendelseRepo.hentTidligereInstHendelserForOpphold(sak.id, hendelse.oppholdId)
                        .whenever(
                            isEmpty = {
                                institusjonsoppholdHendelseRepo.lagre(
                                    hendelse = hendelse.nyHendelsePåSak(
                                        sakId = sak.id,
                                        nesteVersjon = sak.versjon.inc(),
                                        clock = clock,
                                    ),
                                    meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                                )
                            },
                            isNotEmpty = {
                                institusjonsoppholdHendelseRepo.lagre(
                                    hendelse = hendelse.nyHendelsePåSakLenketTilEksisterendeHendelse(
                                        tidligereHendelse = it.hentSisteHendelse(),
                                        nesteVersjon = sak.versjon.inc(),
                                        clock = clock,
                                    ),
                                    meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                                )
                            },
                        )
                }
            }
        }
    }

    private fun Sak.harBehandlingUnderArbeidEllerVedtakSomGirGrunnlagForInstHendelse(clock: Clock): Boolean {
        val tidslinje = this.vedtakstidslinje(Måned.now(clock))
        val harInnvilgetVedtak = tidslinje.harInnvilgelse()
        val harStansetVedtak = tidslinje.harStans()
        val harBehandlingUnderArbeid = this.behandlinger.søknadsbehandlinger.harBehandlingUnderArbeid()

        return harInnvilgetVedtak || harStansetVedtak || harBehandlingUnderArbeid
    }
}
