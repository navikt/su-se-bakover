package no.nav.su.se.bakover.service.historisk.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.SakInfoNy
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselDokumentCommand
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonRepo
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdYtelseForMåned
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadsavgrensning
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.OriginalHistoriskInfotrygdYtelsestidslinje
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdMånedsdata
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdVedtaksdata
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregning
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregningsgrunnlagForMåned
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdFradragsgrunnlag
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdManueltOpphør
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdMånedskilde
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingRepo
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdVedtaksbrevvalg
import no.nav.su.se.bakover.domain.historisk.revurdering.KunneIkkeOppretteHistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.beregnRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.brev.KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando
import no.nav.su.se.bakover.domain.historisk.revurdering.brev.lagVedtaksbrevkommando
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.brev.lagreForhandsvarselMedKopi
import satser.domain.SatsFactory
import satser.domain.historisk.HistoriskInfotrygdSatskategori
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class HistoriskInfotrygdRevurderingService(
    private val sakRepo: SakRepo,
    private val historiskAlderProjeksjonRepo: HistoriskAlderProjeksjonRepo,
    private val revurderingRepo: HistoriskInfotrygdRevurderingRepo,
    private val førsteInnvilgedeSuAppMåned: FørsteInnvilgedeSuAppMåned,
    private val brevService: BrevService,
    private val mottakerService: MottakerService,
    private val sessionFactory: SessionFactory,
    private val satsFactory: SatsFactory,
    private val clock: Clock,
) {
    fun opprett(
        command: OpprettHistoriskInfotrygdRevurderingCommand,
    ): Either<KunneIkkeOppretteHistoriskInfotrygdRevurderingService, HistoriskInfotrygdRevurdering> {
        if (!command.periode.erHeleMåneder()) {
            return KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .PeriodenMåBeståAvHeleMåneder
                .left()
        }
        val sak = hentEllerOpprettAlderssak(command.fnr)
        val førsteInnvilgedeMåned = førsteInnvilgedeSuAppMåned.hent(sak.sakId)
        if (førsteInnvilgedeMåned != null && command.periode.tilOgMed >= førsteInnvilgedeMåned.fraOgMed) {
            return KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .OverlapperInnvilgetSuAppYtelse(førsteInnvilgedeMåned)
                .left()
        }

        val projeksjonId = historiskAlderProjeksjonRepo
            .hentSisteFullførteProjeksjonIdForPerson(command.fnr.value)
            ?: return KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .FantIngenFullførtHistoriskProjeksjon
                .left()
        val grunnlag = historiskAlderProjeksjonRepo.hentOriginalTidslinjegrunnlag(
            projeksjonId = projeksjonId,
            personident = command.fnr.value,
            periode = command.periode,
        )
        val original = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
            projeksjonId = projeksjonId,
            periode = command.periode,
            grunnlag = grunnlag,
        )
        val førsteMånedUtenHistoriskVedtak = original.måneder
            .entries
            .firstOrNull { (_, ytelse) -> !ytelse.harHistoriskVedtak() }
            ?.key
        if (førsteMånedUtenHistoriskVedtak != null) {
            return KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .MånedManglerHistoriskVedtak(førsteMånedUtenHistoriskVedtak)
                .left()
        }
        val gjeldende = GjeldendeHistoriskInfotrygdVedtaksdata.bygg(
            original = original,
            effekter = revurderingRepo.hentIverksatteEffekter(sak.sakId, command.periode),
        )
        val kreverKontrollAvHistoriskForsørgingstillegg = grunnlag
            .map { it.stønadsavgrensning }
            .distinctBy(HistoriskStønadsavgrensning::stønadId)
            .any { stønadsavgrensning ->
                stønadsavgrensning.fraOgMed?.isBefore(forsørgingstilleggSkjæringsdato) == true
            }
        val revurdering = HistoriskInfotrygdRevurdering.opprett(
            sakId = sak.sakId,
            projeksjonId = projeksjonId,
            periode = command.periode,
            saksbehandler = command.saksbehandler,
            tidspunkt = Tidspunkt.now(clock),
            gjeldendeVedtaksdata = gjeldende,
            kreverKontrollAvHistoriskForsørgingstillegg =
            kreverKontrollAvHistoriskForsørgingstillegg,
        ).fold(
            ifLeft = {
                return KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                    .UgyldigHistoriskGrunnlag(it.toString())
                    .left()
            },
            ifRight = { it },
        )

        return revurderingRepo.opprett(revurdering).mapLeft {
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService.LagringFeilet(it)
        }
    }

    fun hent(id: HistoriskInfotrygdRevurderingId) = revurderingRepo.hent(id)

    fun hentForPerson(fnr: Fnr): List<HistoriskInfotrygdRevurdering> =
        sakRepo.hentSakInfoForIdent(fnr, Sakstype.ALDER)
            ?.let { revurderingRepo.hentForSak(it.sakId) }
            ?: emptyList()

    fun hentVedtakForUtbetaling(utbetalingId: UUID30) =
        revurderingRepo.hentVedtakForUtbetaling(utbetalingId)

    fun hentMedSakInfo(
        id: HistoriskInfotrygdRevurderingId,
    ): Pair<SakInfo, HistoriskInfotrygdRevurdering>? {
        val revurdering = revurderingRepo.hent(id) ?: return null
        val sakInfo = sakRepo.hentSakInfo(revurdering.sakId) ?: return null
        return sakInfo to revurdering
    }

    fun hentMånedsgrunnlag(
        id: HistoriskInfotrygdRevurderingId,
    ): HistoriskInfotrygdMånedsgrunnlag? {
        val (sakInfo, revurdering) = hentMedSakInfo(id) ?: return null
        val grunnlag = historiskAlderProjeksjonRepo.hentOriginalTidslinjegrunnlag(
            projeksjonId = revurdering.projeksjonId,
            personident = sakInfo.fnr.value,
            periode = revurdering.periode,
        )
        val original = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
            projeksjonId = revurdering.projeksjonId,
            periode = revurdering.periode,
            grunnlag = grunnlag,
        )
        val gjeldende = GjeldendeHistoriskInfotrygdVedtaksdata.bygg(
            original = original,
            effekter = revurderingRepo.hentIverksatteEffekter(revurdering.sakId, revurdering.periode),
        )
        val stønadsstart = grunnlag.associate {
            it.stønadsavgrensning.stønadId to it.stønadsavgrensning.fraOgMed
        }
        return HistoriskInfotrygdMånedsgrunnlag(
            revurdering = revurdering,
            måneder = gjeldende.tidslinje.values.map { måned ->
                måned.tilMånedsgrunnlag(stønadsstart)
            },
        )
    }

    fun beregn(
        id: HistoriskInfotrygdRevurderingId,
        command: BeregnHistoriskInfotrygdRevurderingCommand,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBeregneHistoriskInfotrygdRevurderingService, HistoriskInfotrygdBeregningResultat> {
        val sakInfo = hentMedSakInfo(id)?.first
            ?: return KunneIkkeBeregneHistoriskInfotrygdRevurderingService.FantIkkeBehandling.left()
        var resultat: HistoriskInfotrygdBeregningResultat? = null
        return endre(id) { eksisterende ->
            val gjeldende = byggGjeldendeVedtaksdata(eksisterende, sakInfo.fnr)
            eksisterende.verifiserAtVedtakeneSomRevurderesIkkeHarForandretSeg(gjeldende).mapLeft {
                KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString())
            }.flatMap {
                gjeldende.beregnRevurdering(command.månedsgrunnlag).mapLeft {
                    KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString())
                }
            }.flatMap { beregning ->
                beregning.lagResultat(gjeldende).mapLeft {
                    KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString())
                }.flatMap { beregningsresultat ->
                    eksisterende.oppdaterGrunnlag(
                        begrunnelse = command.begrunnelse,
                        beregning = beregning,
                        saksbehandler = saksbehandler,
                        tidspunkt = Tidspunkt.now(clock),
                    ).mapLeft {
                        KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString())
                    }.map { oppdatert ->
                        resultat = beregningsresultat.copy(revurdering = oppdatert)
                        oppdatert
                    }
                }
            }
        }.mapLeft {
            when (it) {
                KunneIkkeEndreHistoriskInfotrygdRevurdering.FantIkkeBehandling ->
                    KunneIkkeBeregneHistoriskInfotrygdRevurderingService.FantIkkeBehandling
                is KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand ->
                    KunneIkkeBeregneHistoriskInfotrygdRevurderingService.UgyldigGrunnlag(it.begrunnelse)
            }
        }.map {
            requireNotNull(resultat)
        }
    }

    fun sendTilAttestering(
        id: HistoriskInfotrygdRevurderingId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> =
        endre(id) { eksisterende ->
            eksisterende.sendTilAttestering(
                saksbehandler = saksbehandler,
                tidspunkt = Tidspunkt.now(clock),
            ).mapLeft { KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString()) }
        }

    fun bekreftKontrollAvHistoriskForsørgingstillegg(
        id: HistoriskInfotrygdRevurderingId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> =
        endre(id) { eksisterende ->
            eksisterende.bekreftKontrollAvHistoriskForsørgingstillegg(
                saksbehandler = saksbehandler,
                tidspunkt = Tidspunkt.now(clock),
            ).mapLeft { KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString()) }
        }

    fun underkjenn(
        id: HistoriskInfotrygdRevurderingId,
        attestant: NavIdentBruker.Attestant,
        begrunnelse: String,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> =
        endre(id) { eksisterende ->
            eksisterende.underkjenn(
                attestant = attestant,
                begrunnelse = begrunnelse,
                tidspunkt = Tidspunkt.now(clock),
            ).mapLeft { KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString()) }
        }

    fun attester(
        id: HistoriskInfotrygdRevurderingId,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> =
        endre(id) { eksisterende ->
            eksisterende.attester(
                attestant = attestant,
                tidspunkt = Tidspunkt.now(clock),
            ).mapLeft { KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString()) }
        }

    data class HistoriskInfotrygdMånedsgrunnlag(
        val revurdering: HistoriskInfotrygdRevurdering,
        val måneder: List<HistoriskInfotrygdMånedsgrunnlagForMåned>,
    )

    sealed interface HistoriskInfotrygdMånedsgrunnlagForMåned {
        val måned: Måned

        data class Ytelse(
            override val måned: Måned,
            val stønadsstart: LocalDate?,
            val opprinneligStønadId: Long,
            val opprinneligVedtakId: Long,
            val oppdragId: String?,
            val kilde: String,
            val historiskSats: BigDecimal,
            val historiskFradrag: BigDecimal,
            val historiskFradragskoder: List<String>,
            val historiskBeløp: BigDecimal,
            val foreslåttSatskategori: String?,
            val kreverKontrollAvHistoriskForsørgingstillegg: Boolean,
        ) : HistoriskInfotrygdMånedsgrunnlagForMåned

        data class IngenYtelse(
            override val måned: Måned,
            val opprinneligStønadId: Long?,
            val opprinneligVedtakId: Long?,
            val oppdragId: String?,
            val kilde: String,
        ) : HistoriskInfotrygdMånedsgrunnlagForMåned
    }

    private fun GjeldendeHistoriskInfotrygdMånedsdata.tilMånedsgrunnlag(
        stønadsstart: Map<HistoriskStønadId, LocalDate?>,
    ): HistoriskInfotrygdMånedsgrunnlagForMåned = when (this) {
        is GjeldendeHistoriskInfotrygdMånedsdata.Ytelse -> {
            val startdato = stønadsstart[opprinneligStønadId]
            HistoriskInfotrygdMånedsgrunnlagForMåned.Ytelse(
                måned = måned,
                stønadsstart = startdato,
                opprinneligStønadId = opprinneligStønadId.value,
                opprinneligVedtakId = opprinneligVedtakId.value,
                oppdragId = oppdragId,
                kilde = kilde.tilResponsverdi(),
                historiskSats = sats,
                historiskFradrag = fradrag,
                historiskFradragskoder = when (val grunnlag = fradragsgrunnlag) {
                    is HistoriskInfotrygdFradragsgrunnlag.OriginaleKoder -> grunnlag.koder
                    is HistoriskInfotrygdFradragsgrunnlag.RevurderteFradrag -> emptyList()
                },
                historiskBeløp = beløp,
                foreslåttSatskategori = bosituasjon?.tilSatskategori(),
                kreverKontrollAvHistoriskForsørgingstillegg =
                startdato?.isBefore(forsørgingstilleggSkjæringsdato) == true,
            )
        }
        is GjeldendeHistoriskInfotrygdMånedsdata.IngenYtelse ->
            HistoriskInfotrygdMånedsgrunnlagForMåned.IngenYtelse(
                måned = måned,
                opprinneligStønadId = opprinneligStønadId?.value,
                opprinneligVedtakId = opprinneligVedtakId?.value,
                oppdragId = oppdragId,
                kilde = kilde.tilResponsverdi(),
            )
    }

    private fun HistoriskInfotrygdMånedskilde.tilResponsverdi(): String = when (this) {
        is HistoriskInfotrygdMånedskilde.OriginalProjeksjon -> "ORIGINAL_PROJEKSJON"
        is HistoriskInfotrygdMånedskilde.Revurderingsvedtak -> "HISTORISK_REVURDERINGSVEDTAK"
    }

    private fun HistoriskBosituasjon.tilSatskategori(): String = when (this) {
        HistoriskBosituasjon.ENSLIG -> "EN"
        HistoriskBosituasjon.EPS_UNDER_67 -> "EU"
        HistoriskBosituasjon.EPS_OVER_67 -> "EO"
        HistoriskBosituasjon.ENSLIG_MED_BOFELLESSKAP -> "EV"
    }

    private val forsørgingstilleggSkjæringsdato = LocalDate.of(2015, 1, 1)

    fun avslutt(
        id: HistoriskInfotrygdRevurderingId,
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> =
        endre(id) { eksisterende ->
            eksisterende.avslutt(
                saksbehandler = saksbehandler,
                begrunnelse = begrunnelse,
                tidspunkt = Tidspunkt.now(clock),
            ).mapLeft { KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString()) }
        }

    fun oppdaterVedtaksbrev(
        id: HistoriskInfotrygdRevurderingId,
        valg: HistoriskInfotrygdVedtaksbrevvalg.Valgt,
        fritekst: String?,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> =
        endre(id) { eksisterende ->
            eksisterende.oppdaterVedtaksbrev(
                valg = valg,
                fritekst = fritekst,
                saksbehandler = saksbehandler,
                tidspunkt = Tidspunkt.now(clock),
            ).mapLeft { KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString()) }
        }

    fun lagVedtaksbrevutkast(
        id: HistoriskInfotrygdRevurderingId,
    ): Either<KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast, PdfA> {
        val (sakInfo, revurdering) = hentMedSakInfo(id)
            ?: return KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.FantIkkeBehandling.left()
        if (revurdering.vedtaksbrevvalg != HistoriskInfotrygdVedtaksbrevvalg.SEND) {
            return KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.SkalIkkeSendeBrev.left()
        }
        return revurdering.lagVedtaksbrevkommando(
            sakInfo = sakInfo,
            satsFactory = satsFactory,
        ).mapLeft {
            KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.KunneIkkeLageBrevgrunnlag(it)
        }.flatMap { command ->
            brevService.lagDokumentPdf(command).mapLeft {
                KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.KunneIkkeGenererePdf(it)
            }.map { it.generertDokument }
        }
    }

    fun lagForhåndsvarselutkast(
        id: HistoriskInfotrygdRevurderingId,
        fritekst: String,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLageHistoriskInfotrygdForhåndsvarsel, PdfA> {
        val (sakInfo, revurdering) = hentMedSakInfo(id)
            ?: return KunneIkkeLageHistoriskInfotrygdForhåndsvarsel.FantIkkeBehandling.left()
        if (revurdering.beregning == null) {
            return KunneIkkeLageHistoriskInfotrygdForhåndsvarsel.ManglerBeregning.left()
        }
        if (fritekst.isBlank()) {
            return KunneIkkeLageHistoriskInfotrygdForhåndsvarsel.ManglerFritekst.left()
        }
        return brevService.lagDokumentPdf(
            ForhåndsvarselDokumentCommand(
                fødselsnummer = sakInfo.fnr,
                saksnummer = sakInfo.saksnummer,
                sakstype = sakInfo.type,
                saksbehandler = saksbehandler,
                fritekst = fritekst,
            ),
        ).mapLeft {
            KunneIkkeLageHistoriskInfotrygdForhåndsvarsel.KunneIkkeGenererePdf(it)
        }.map { it.generertDokument }
    }

    fun velgÅIkkeSendeForhåndsvarsel(
        id: HistoriskInfotrygdRevurderingId,
        begrunnelse: String,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> =
        endre(id) { eksisterende ->
            eksisterende.velgÅIkkeSendeForhåndsvarsel(
                begrunnelse = begrunnelse,
                saksbehandler = saksbehandler,
                tidspunkt = Tidspunkt.now(clock),
            ).mapLeft { KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString()) }
        }

    fun sendForhåndsvarsel(
        id: HistoriskInfotrygdRevurderingId,
        fritekst: String,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel, HistoriskInfotrygdRevurdering> {
        val (sakInfo, eksisterende) = hentMedSakInfo(id)
            ?: return KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel.FantIkkeBehandling.left()
        val oppdatert = eksisterende.markerForhåndsvarselSomSendt(
            fritekst = fritekst,
            saksbehandler = saksbehandler,
            tidspunkt = Tidspunkt.now(clock),
        ).mapLeft {
            KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel.UgyldigTilstand(it.toString())
        }.fold(ifLeft = { return it.left() }, ifRight = { it })
        val dokumentUtenMetadata = brevService.lagDokumentPdf(
            ForhåndsvarselDokumentCommand(
                fødselsnummer = sakInfo.fnr,
                saksnummer = sakInfo.saksnummer,
                sakstype = sakInfo.type,
                saksbehandler = saksbehandler,
                fritekst = fritekst,
            ),
        ).mapLeft {
            KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel.KunneIkkeGenererePdf(it)
        }.fold(ifLeft = { return it.left() }, ifRight = { it })
        val dokument = when (dokumentUtenMetadata) {
            is Dokument.UtenMetadata.Informasjon.Viktig ->
                dokumentUtenMetadata.leggTilMetadata(
                    Dokument.Metadata(
                        sakId = eksisterende.sakId,
                        revurderingId = eksisterende.id.value,
                    ),
                    distribueringsadresse = null,
                )
            else -> return KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel.UventetDokumenttype.left()
        }
        val lagreDokument = lagreForhandsvarselMedKopi(
            brevService = brevService,
            mottakerService = mottakerService,
            referanseType = ReferanseTypeMottaker.REVURDERING,
            referanseId = eksisterende.id.value,
            sakId = eksisterende.sakId,
        )
        sessionFactory.withTransactionContext { tx ->
            lagreDokument(dokument, tx)
            revurderingRepo.lagre(oppdatert, tx)
        }
        return oppdatert.right()
    }

    private fun endre(
        id: HistoriskInfotrygdRevurderingId,
        endring: (
            HistoriskInfotrygdRevurdering,
        ) -> Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering>,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> {
        val eksisterende = revurderingRepo.hent(id)
            ?: return KunneIkkeEndreHistoriskInfotrygdRevurdering.FantIkkeBehandling.left()
        return endring(eksisterende).map { oppdatert ->
            revurderingRepo.lagre(oppdatert)
            oppdatert
        }
    }

    private fun hentEllerOpprettAlderssak(fnr: Fnr): SakInfo {
        sakRepo.hentSakInfoForIdent(fnr, Sakstype.ALDER)?.let { return it }
        val nySak = SakInfoNy(
            sakId = UUID.randomUUID(),
            fnr = fnr,
            type = Sakstype.ALDER,
        )
        sakRepo.opprettSak(nySak)
        return requireNotNull(sakRepo.hentSakInfoForIdent(fnr, Sakstype.ALDER)) {
            "Fant ikke alderssak etter opprettelse"
        }
    }

    private fun byggGjeldendeVedtaksdata(
        revurdering: HistoriskInfotrygdRevurdering,
        fnr: Fnr,
    ): GjeldendeHistoriskInfotrygdVedtaksdata {
        val grunnlag = historiskAlderProjeksjonRepo.hentOriginalTidslinjegrunnlag(
            projeksjonId = revurdering.projeksjonId,
            personident = fnr.value,
            periode = revurdering.periode,
        )
        return GjeldendeHistoriskInfotrygdVedtaksdata.bygg(
            original = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
                projeksjonId = revurdering.projeksjonId,
                periode = revurdering.periode,
                grunnlag = grunnlag,
            ),
            effekter = revurderingRepo.hentIverksatteEffekter(revurdering.sakId, revurdering.periode),
        )
    }
}

data class BeregnHistoriskInfotrygdRevurderingCommand(
    val begrunnelse: String,
    val månedsgrunnlag: List<HistoriskInfotrygdBeregningsgrunnlagForMåned>,
)

data class HistoriskInfotrygdBeregningResultat(
    val revurdering: HistoriskInfotrygdRevurdering?,
    val økonomiskRetning: HistoriskInfotrygdØkonomiskRetning,
    val måneder: List<HistoriskInfotrygdBeregningResultatForMåned>,
)

data class HistoriskInfotrygdBeregningResultatForMåned(
    val måned: Måned,
    val satskategori: HistoriskInfotrygdSatskategori,
    val fradrag: List<FradragForMåned>,
    val manueltOpphør: HistoriskInfotrygdManueltOpphør?,
    val gammeltBeløp: BigDecimal,
    val nyttBeløp: BigDecimal,
    val differanse: BigDecimal,
    val nyttResultat: String,
    val opphørsgrunn: String?,
    val begrunnelse: String?,
    val gjeninnvilgelsesbegrunnelse: String?,
)

enum class HistoriskInfotrygdØkonomiskRetning {
    INGEN_ENDRING,
    ETTERBETALING,
    FEILUTBETALING,
}

sealed interface KunneIkkeBeregneHistoriskInfotrygdRevurderingService {
    data object FantIkkeBehandling : KunneIkkeBeregneHistoriskInfotrygdRevurderingService
    data class UgyldigGrunnlag(val begrunnelse: String) : KunneIkkeBeregneHistoriskInfotrygdRevurderingService
}

private fun HistoriskInfotrygdBeregning.lagResultat(
    gjeldende: GjeldendeHistoriskInfotrygdVedtaksdata,
): Either<String, HistoriskInfotrygdBeregningResultat> {
    val måneder = månedsresultater.map { (måned, resultat) ->
        val gammeltBeløp = when (val gammelt = gjeldende.forMåned(måned)) {
            is GjeldendeHistoriskInfotrygdMånedsdata.Ytelse -> gammelt.beløp
            is GjeldendeHistoriskInfotrygdMånedsdata.IngenYtelse -> BigDecimal.ZERO
            null -> return "Mangler gjeldende data for $måned".left()
        }
        val nyttBeløp = when (resultat) {
            is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> resultat.beløp
            is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> BigDecimal.ZERO
        }
        HistoriskInfotrygdBeregningResultatForMåned(
            måned = måned,
            satskategori = resultat.bosituasjon.tilSatskategori(),
            fradrag = resultat.fradrag,
            manueltOpphør = (resultat as? HistoriskInfotrygdRevurdertMånedsresultat.Opphør)
                ?.takeIf { it.begrunnelse != null }
                ?.let {
                    HistoriskInfotrygdManueltOpphør(
                        opphørsgrunn = it.opphørsgrunn,
                        begrunnelse = requireNotNull(it.begrunnelse),
                    )
                },
            gammeltBeløp = gammeltBeløp,
            nyttBeløp = nyttBeløp,
            differanse = nyttBeløp - gammeltBeløp,
            nyttResultat = when (resultat) {
                is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> "YTELSE"
                is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> "OPPHØR"
            },
            opphørsgrunn = (resultat as? HistoriskInfotrygdRevurdertMånedsresultat.Opphør)
                ?.opphørsgrunn
                ?.name,
            begrunnelse = (resultat as? HistoriskInfotrygdRevurdertMånedsresultat.Opphør)?.begrunnelse,
            gjeninnvilgelsesbegrunnelse =
            (resultat as? HistoriskInfotrygdRevurdertMånedsresultat.Ytelse)
                ?.gjeninnvilgelsesbegrunnelse,
        )
    }
    val harEtterbetaling = måneder.any { it.differanse.signum() > 0 }
    val harFeilutbetaling = måneder.any { it.differanse.signum() < 0 }
    if (harEtterbetaling && harFeilutbetaling) {
        return "Behandlingen har både etterbetaling og feilutbetaling og må deles".left()
    }
    return HistoriskInfotrygdBeregningResultat(
        revurdering = null,
        økonomiskRetning = when {
            harEtterbetaling -> HistoriskInfotrygdØkonomiskRetning.ETTERBETALING
            harFeilutbetaling -> HistoriskInfotrygdØkonomiskRetning.FEILUTBETALING
            else -> HistoriskInfotrygdØkonomiskRetning.INGEN_ENDRING
        },
        måneder = måneder,
    ).right()
}

private val HistoriskInfotrygdRevurdertMånedsresultat.bosituasjon: HistoriskBosituasjon
    get() = when (this) {
        is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> bosituasjon
        is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> bosituasjon
    }

private val HistoriskInfotrygdRevurdertMånedsresultat.fradrag: List<FradragForMåned>
    get() = when (this) {
        is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> fradrag
        is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> fradrag
    }

private fun HistoriskBosituasjon.tilSatskategori(): HistoriskInfotrygdSatskategori = when (this) {
    HistoriskBosituasjon.ENSLIG -> HistoriskInfotrygdSatskategori.EN
    HistoriskBosituasjon.EPS_UNDER_67 -> HistoriskInfotrygdSatskategori.EU
    HistoriskBosituasjon.EPS_OVER_67 -> HistoriskInfotrygdSatskategori.EO
    HistoriskBosituasjon.ENSLIG_MED_BOFELLESSKAP -> HistoriskInfotrygdSatskategori.EV
}

data class OpprettHistoriskInfotrygdRevurderingCommand(
    val fnr: Fnr,
    val periode: Periode,
    val saksbehandler: NavIdentBruker.Saksbehandler,
)

sealed interface KunneIkkeOppretteHistoriskInfotrygdRevurderingService {
    data object FantIngenFullførtHistoriskProjeksjon : KunneIkkeOppretteHistoriskInfotrygdRevurderingService
    data object PeriodenMåBeståAvHeleMåneder : KunneIkkeOppretteHistoriskInfotrygdRevurderingService
    data class MånedManglerHistoriskVedtak(val måned: Måned) : KunneIkkeOppretteHistoriskInfotrygdRevurderingService

    data class OverlapperInnvilgetSuAppYtelse(val førsteInnvilgedeMåned: Måned) : KunneIkkeOppretteHistoriskInfotrygdRevurderingService

    data class UgyldigHistoriskGrunnlag(val begrunnelse: String) : KunneIkkeOppretteHistoriskInfotrygdRevurderingService

    data class LagringFeilet(val feil: KunneIkkeOppretteHistoriskInfotrygdRevurdering) : KunneIkkeOppretteHistoriskInfotrygdRevurderingService
}

sealed interface KunneIkkeEndreHistoriskInfotrygdRevurdering {
    data object FantIkkeBehandling : KunneIkkeEndreHistoriskInfotrygdRevurdering
    data class UgyldigTilstand(val begrunnelse: String) : KunneIkkeEndreHistoriskInfotrygdRevurdering
}

sealed interface KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast {
    data object FantIkkeBehandling : KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast
    data object SkalIkkeSendeBrev : KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast
    data class KunneIkkeLageBrevgrunnlag(
        val feil: KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando,
    ) : KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast
    data class KunneIkkeGenererePdf(
        val feil: KunneIkkeLageDokument,
    ) : KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast
}

sealed interface KunneIkkeLageHistoriskInfotrygdForhåndsvarsel {
    data object FantIkkeBehandling : KunneIkkeLageHistoriskInfotrygdForhåndsvarsel
    data object ManglerBeregning : KunneIkkeLageHistoriskInfotrygdForhåndsvarsel
    data object ManglerFritekst : KunneIkkeLageHistoriskInfotrygdForhåndsvarsel
    data class KunneIkkeGenererePdf(val feil: KunneIkkeLageDokument) : KunneIkkeLageHistoriskInfotrygdForhåndsvarsel
}

sealed interface KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel {
    data object FantIkkeBehandling : KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel
    data object UventetDokumenttype : KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel
    data class UgyldigTilstand(val begrunnelse: String) : KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel
    data class KunneIkkeGenererePdf(val feil: KunneIkkeLageDokument) : KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel
}

fun interface FørsteInnvilgedeSuAppMåned {
    fun hent(sakId: UUID): Måned?
}

class FørsteInnvilgedeSuAppMånedFraVedtak(
    private val vedtakRepo: VedtakRepo,
) : FørsteInnvilgedeSuAppMåned {
    override fun hent(sakId: UUID): Måned? =
        vedtakRepo.hentVedtakSomKanRevurderesForSak(sakId)
            .asSequence()
            .filter { it.erInnvilget() }
            .map { it.periode.fraOgMed }
            .minOrNull()
            ?.let { Måned.fra(YearMonth.from(it)) }
}

private fun HistoriskInfotrygdYtelseForMåned.harHistoriskVedtak(): Boolean = when (this) {
    is HistoriskInfotrygdYtelseForMåned.Ytelse -> true
    is HistoriskInfotrygdYtelseForMåned.IngenYtelse -> vedtakId != null
}

private fun Periode.erHeleMåneder(): Boolean =
    fraOgMed.dayOfMonth == 1 && tilOgMed == YearMonth.from(tilOgMed).atEndOfMonth()
