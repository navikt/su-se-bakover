package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import dokument.domain.KunneIkkeGenererePdf
import dokument.domain.PdfGenerator
import dokument.domain.PensjonPdfByggerKlient
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.SammenslåPdf
import dokument.domain.generer.bygger.GenerellData
import dokument.domain.generer.bygger.PdfBestilling
import dokument.domain.generer.bygger.maler.ForhåndsvarselPdfInnhold
import dokument.domain.generer.bygger.maler.FritekstPdfInnhold
import dokument.domain.generer.bygger.maler.InnkallingTilKontrollsamtalePdfInnhold
import dokument.domain.generer.bygger.maler.InnvilgetSøknadsbehandlingPdfInnhold
import dokument.domain.generer.bygger.maler.KlagePdfInnhold
import dokument.domain.generer.bygger.maler.KontrollnotatPdfInnholdNy
import dokument.domain.generer.bygger.maler.TrukketSøknadPdfInnhold
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.RuntimeEnvironment.Dev
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.RuntimeEnvironment.Local
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.RuntimeEnvironment.Prod
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.RuntimeEnvironment.Test
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock

class PdfByggerService(
    val klient: PensjonPdfByggerKlient,
    val gammelKlient: PdfGenerator,
    val runtimeEnv: ApplicationConfig.RuntimeEnvironment,
    val clock: Clock,
    val brukPdfByggerLokalt: Boolean = false,
    val brukPdfgenLokalt: Boolean = false,
    private val log: Logger = LoggerFactory.getLogger(PdfByggerService::class.java),
) : PdfGenerator {
    override fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA> {
        val mal = pdfInnhold.dokumentMal

        if (skalBrukeGammelLøsning(mal, runtimeEnv)) {
            return genererMedGammelKlient(pdfInnhold)
        }

        val bestilling = pdfInnhold.tilPdfBestilling()
        if (bestilling == null) {
            log.error("Aktivert PDF-mal {} mangler bygger for innholdstype {}", mal.navn, pdfInnhold.javaClass.name)
            return KunneIkkeGenererePdf.left()
        }
        return klient.lagPdf(bestilling).fold(
            ifLeft = {
                val lokalHjelp = if (runtimeEnv == Local) {
                    " Kontroller at pdf-bygger kjører på localhost:8082. Start den med docker compose up -d pdf-bygger."
                } else {
                    ""
                }
                log.error("Kunne ikke generere PDF med pdf-bygger for mal {}.{}", mal.navn, lokalHjelp)
                KunneIkkeGenererePdf.left()
            },
            ifRight = {
                PdfA(it).right()
            },
        ).let {
            if (runtimeEnv == Dev || (runtimeEnv == Local && brukPdfByggerLokalt && brukPdfgenLokalt)) {
                either {
                    val nyPdf = it.bind()
                    val gammelPdf = genererMedGammelKlient(pdfInnhold).bind()
                    SammenslåPdf.leggTilVedlegg(nyPdf, gammelPdf.getContent()).mapLeft {
                        log.error("Kunne ikke slå sammen PDF fra pdf-bygger og su-pdfgen for mal {}.", mal.navn)
                        KunneIkkeGenererePdf
                    }.bind()
                }
            } else {
                it
            }
        }
    }

    private fun genererMedGammelKlient(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA> =
        gammelKlient.genererPdf(pdfInnhold).onLeft {
            val lokalHjelp = if (runtimeEnv == Local) {
                " Kontroller at su-pdfgen kjører på localhost:8081 når PDFGEN_LOCAL=true, " +
                    "eller sett PDFGEN_LOCAL=false hvis du ikke trenger PDF fra su-pdfgen."
            } else {
                ""
            }
            log.error("Kunne ikke generere PDF med su-pdfgen for mal {}.{}", pdfInnhold.dokumentMal.navn, lokalHjelp)
        }

    private fun skalBrukeGammelLøsning(mal: DokumentMal, runtimeEnv: ApplicationConfig.RuntimeEnvironment) =
        when (runtimeEnv) {
            Prod,
            -> !mal.pdfByggerProd

            // Lokal pdf-bygger startes med docker compose og slås på med PDFBYGGER_LOCAL.
            Local,
            -> !brukPdfByggerLokalt || !mal.pdfByggerDev

            Test,
            Dev,
            -> !mal.pdfByggerDev
        }
}

internal fun PdfInnhold.tilPdfBestilling(): PdfBestilling? = when (this) {
    is KontrollnotatPdfInnholdNy -> dokumentMal.lagBestilling(this)
    is ForhåndsvarselPdfInnhold -> dokumentMal.lagBestilling(this)
    is InnkallingTilKontrollsamtalePdfInnhold -> dokumentMal.lagBestilling(this)
    is FritekstPdfInnhold -> dokumentMal.lagBestilling(this)
    is KlagePdfInnhold.Oppretthold -> dokumentMal.lagBestiling(this)
    is KlagePdfInnhold.Avvist -> dokumentMal.lagBestiling(this)
    is InnvilgetSøknadsbehandlingPdfInnhold -> dokumentMal.lagBestilling(this)
    is TrukketSøknadPdfInnhold -> dokumentMal.lagBestilling(this)
    is SøknadPdfInnhold -> dokumentMal.lagBestilling(GenerellData.parse(toJson()))
    // is AvslagSøknadsbehandlingPdfInnhold -> dokumentMal.lagBestilling(this)
    // is AvvistSøknadVedtakPdfInnhold -> dokumentMal.lagBestilling(this)
    // is AvvistSøknadFritekstPdfInnhold -> dokumentMal.lagBestilling(this)
    // is OpphørsvedtakPdfInnhold -> dokumentMal.lagBestilling(this)
    // is ForhåndsvarselTilbakekrevingsbehandlingPdfInnhold.Vanlig -> dokumentMal.lagBestilling(this)
    // is ForhåndsvarselTilbakekrevingsbehandlingPdfInnhold.Dødsbo -> dokumentMal.lagBestilling(this)
    // is VedtaksbrevTilbakekrevingsbehandlingPdfInnhold -> dokumentMal.lagBestilling(this) // VedtaksbrevTilbakekrevingsbehandling og VedtaksbrevTilbakekrevingsbehandlingDødsbo
    // is PåminnelseNyStønadsperiodePdfInnhold -> dokumentMal.lagBestilling(this)
    // is RevurderingAvInntektPdfInnhold -> dokumentMal.lagBestilling(this)
    // is AvsluttRevurderingPdfInnhold -> dokumentMal.lagBestilling(this)
    // is VedtakVedReguleringPdfInnhold -> dokumentMal.lagBestilling(this)
    else -> null
}
