package no.nav.su.se.bakover.service.mottaker

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Brevtype
import dokument.domain.DokumentRepo
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.mottaker.FeilkoderMottaker
import no.nav.su.se.bakover.domain.mottaker.LagreMottaker
import no.nav.su.se.bakover.domain.mottaker.MottakerDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerIdentifikator
import no.nav.su.se.bakover.domain.mottaker.MottakerRepo
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.OppdaterMottaker
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.domain.mottaker.erTillattForMottaker
import no.nav.su.se.bakover.domain.mottaker.ugyldigBrevtypeMelding
import no.nav.su.se.bakover.domain.mottaker.ugyldigKombinasjonReferanseTypeOgBrevtypeMelding
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.collections.joinToString

class MottakerServiceImpl(
    private val mottakerRepo: MottakerRepo,
    private val dokumentRepo: DokumentRepo,
    private val vedtakRepo: VedtakRepo,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val erProd: Boolean = false,
) : MottakerService {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private fun erGyldigBrevtype(
        brevtype: Brevtype,
    ): Boolean = brevtype.erTillattForMottaker()

    private fun erGyldigKombinasjonReferanseTypeOgBrevtype(
        referanseType: ReferanseTypeMottaker,
        brevtype: Brevtype,
    ): Boolean {
        return when (referanseType) {
            ReferanseTypeMottaker.SØKNAD -> brevtype == Brevtype.VEDTAK
            ReferanseTypeMottaker.REVURDERING -> brevtype == Brevtype.VEDTAK || brevtype == Brevtype.FORHANDSVARSEL
            ReferanseTypeMottaker.KLAGE -> brevtype == Brevtype.VEDTAK || brevtype == Brevtype.OVERSENDELSE_KA
            ReferanseTypeMottaker.DØDSBO_TILBAKEKREVING -> brevtype == Brevtype.VEDTAK || brevtype == Brevtype.FORHANDSVARSEL
        }
    }

    /**
     * Alle dokumenter som kun har sakid men ingen annen id kan ikke ha flere mottakere da de er "automatiske"
     * Eller manuelle brev som er opprettet direkte på saken uten annen tilknytning og kan ikke unikt identifiseres
     * hvis vi skal støtte frie brev med flere mottakere mot som feks [lagreOgSendOpplastetPdfPåSak] må man ha en ekstra referanseid på dem
     */
    override fun hentMottaker(
        mottakerIdentifikator: MottakerIdentifikator,
        sakId: UUID,
        transactionContext: TransactionContext?,
    ): Either<FeilkoderMottaker, MottakerDomain?> {
        if (!erGyldigBrevtype(mottakerIdentifikator.brevtype)) {
            return FeilkoderMottaker.UgyldigMottakerRequest(ugyldigBrevtypeMelding(mottakerIdentifikator.brevtype.name))
                .left()
        }
        if (!erGyldigKombinasjonReferanseTypeOgBrevtype(
                mottakerIdentifikator.referanseType,
                mottakerIdentifikator.brevtype,
            )
        ) {
            return FeilkoderMottaker.UgyldigMottakerRequest(
                ugyldigKombinasjonReferanseTypeOgBrevtypeMelding(
                    referanseType = mottakerIdentifikator.referanseType,
                    brevtype = mottakerIdentifikator.brevtype,
                ),
            ).left()
        }
        val hentetMottaker = mottakerRepo.hentMottaker(mottakerIdentifikator, transactionContext)?.let {
            if (it.sakId != sakId) {
                return FeilkoderMottaker.ForespurtSakIdMatcherIkkeMottaker.left()
            } else {
                it
            }
        }
        return hentetMottaker.right()
    }

    private fun kanEndreForMottaker(mottaker: MottakerDomain): Boolean {
        return when (mottaker.referanseType) {
            ReferanseTypeMottaker.SØKNAD ->
                !vedtakRepo.finnesVedtakForSøknadsbehandlingId(SøknadsbehandlingId(mottaker.referanseId))

            ReferanseTypeMottaker.REVURDERING ->
                when (mottaker.brevtype) {
                    Brevtype.VEDTAK ->
                        !vedtakRepo.finnesVedtakForRevurderingId(RevurderingId(mottaker.referanseId))

                    Brevtype.FORHANDSVARSEL ->
                        dokumentRepo.hentForRevurdering(mottaker.referanseId).none {
                            it.brevtype == Brevtype.FORHANDSVARSEL
                        }

                    else -> false
                }

            ReferanseTypeMottaker.KLAGE ->
                dokumentRepo.hentForKlage(mottaker.referanseId).isEmpty()

            ReferanseTypeMottaker.DØDSBO_TILBAKEKREVING ->
                // TODO Fjern når testet tilstrekkelig
                when (erProd) {
                    true -> false
                    false -> when (mottaker.brevtype) {
                        // Dødsbo legges til samtidig som sending og vil aldri endres etter eneste lagring
                        Brevtype.FORHANDSVARSEL -> true
                        Brevtype.VEDTAK -> {
                            dokumentHendelseRepo.hentDokumentMedMetadataForSakId(mottaker.sakId).none {
                                it.brevtype == Brevtype.VEDTAK && it.metadata.tilbakekrevingsbehandlingId == mottaker.referanseId
                            }
                        }
                        else -> false
                    }
                }
        }
    }

    override fun lagreMottaker(
        mottaker: LagreMottaker,
        sakId: UUID,
    ): Either<FeilkoderMottaker, MottakerDomain> {
        val mottakerValidert = mottaker.toDomain(sakId).getOrElse {
            return FeilkoderMottaker.UgyldigMottakerRequest(it.joinToString(separator = ",")).left()
        }
        if (!erGyldigKombinasjonReferanseTypeOgBrevtype(mottakerValidert.referanseType, mottakerValidert.brevtype)) {
            return FeilkoderMottaker.UgyldigMottakerRequest(
                ugyldigKombinasjonReferanseTypeOgBrevtypeMelding(
                    referanseType = mottakerValidert.referanseType,
                    brevtype = mottakerValidert.brevtype,
                ),
            ).left()
        }
        val kanEndre = kanEndreForMottaker(mottakerValidert)
        return if (kanEndre) {
            mottakerRepo.lagreMottaker(mottakerValidert)
            mottakerValidert.right()
        } else {
            FeilkoderMottaker.KanIkkeLagreMottaker.left()
        }
    }

    override fun oppdaterMottaker(
        mottaker: OppdaterMottaker,
        sakId: UUID,
    ): Either<FeilkoderMottaker, Unit> {
        val mottakerValidert = mottaker.toDomain(sakId).getOrElse {
            return FeilkoderMottaker.UgyldigMottakerRequest(it.joinToString(separator = ",")).left()
        }
        if (!erGyldigBrevtype(mottakerValidert.brevtype)) {
            return FeilkoderMottaker.UgyldigMottakerRequest(ugyldigBrevtypeMelding(mottakerValidert.brevtype.name))
                .left()
        }
        if (!erGyldigKombinasjonReferanseTypeOgBrevtype(mottakerValidert.referanseType, mottakerValidert.brevtype)) {
            return FeilkoderMottaker.UgyldigMottakerRequest(
                ugyldigKombinasjonReferanseTypeOgBrevtypeMelding(
                    referanseType = mottakerValidert.referanseType,
                    brevtype = mottakerValidert.brevtype,
                ),
            ).left()
        }
        val kanEndre = kanEndreForMottaker(mottakerValidert)
        return if (kanEndre) {
            mottakerRepo.oppdaterMottaker(mottakerValidert).right()
        } else {
            FeilkoderMottaker.KanIkkeOppdatereMottaker.left()
        }
    }

    override fun slettMottaker(
        mottakerIdentifikator: MottakerIdentifikator,
        sakId: UUID,
    ): Either<FeilkoderMottaker, Unit> {
        if (!erGyldigBrevtype(mottakerIdentifikator.brevtype)) {
            return FeilkoderMottaker.UgyldigMottakerRequest(ugyldigBrevtypeMelding(mottakerIdentifikator.brevtype.name))
                .left()
        }
        if (!erGyldigKombinasjonReferanseTypeOgBrevtype(
                mottakerIdentifikator.referanseType,
                mottakerIdentifikator.brevtype,
            )
        ) {
            return FeilkoderMottaker.UgyldigMottakerRequest(
                ugyldigKombinasjonReferanseTypeOgBrevtypeMelding(
                    referanseType = mottakerIdentifikator.referanseType,
                    brevtype = mottakerIdentifikator.brevtype,
                ),
            ).left()
        }

        val mottaker = mottakerRepo.hentMottaker(mottakerIdentifikator)
        return if (mottaker == null) {
            log.info("Fant ikke mottaker for type ${mottakerIdentifikator.referanseType} id: ${mottakerIdentifikator.referanseId} ingenting å slette")
            Unit.right()
        } else {
            if (mottaker.sakId != sakId) {
                return FeilkoderMottaker.ForespurtSakIdMatcherIkkeMottaker.left()
            }
            val harDokument = when (mottaker.referanseType) {
                ReferanseTypeMottaker.SØKNAD ->
                    dokumentRepo.hentForSøknad(mottaker.referanseId).isNotEmpty()

                ReferanseTypeMottaker.REVURDERING ->
                    dokumentRepo.hentForRevurdering(mottaker.referanseId).any { dokument ->
                        when (mottaker.brevtype) {
                            Brevtype.VEDTAK -> dokument.brevtype == Brevtype.VEDTAK
                            Brevtype.FORHANDSVARSEL ->
                                dokument.brevtype == Brevtype.FORHANDSVARSEL

                            else -> false
                        }
                    }

                ReferanseTypeMottaker.KLAGE ->
                    dokumentRepo.hentForKlage(mottaker.referanseId).isNotEmpty()

                ReferanseTypeMottaker.DØDSBO_TILBAKEKREVING ->
                    when (mottaker.brevtype) {
                        // Dødsbo legges til samtidig som sending og vil ikke trenge sletting
                        Brevtype.FORHANDSVARSEL -> false
                        Brevtype.VEDTAK -> {
                            dokumentHendelseRepo.hentDokumentMedMetadataForSakId(mottaker.sakId).any {
                                it.brevtype == Brevtype.VEDTAK && it.metadata.tilbakekrevingsbehandlingId == mottaker.referanseId
                            }
                        }
                        else -> false
                    }
            }

            if (harDokument) {
                log.info("Kan ikke slette mottaker da det finnes et brev for referansen")
                return FeilkoderMottaker.BrevFinnesIDokumentBasen.left()
            }
            log.info("Sletter mottaker med id: ${mottaker.id} sakid ${mottaker.sakId} type ${mottaker.referanseType} id: ${mottaker.referanseId}")
            mottakerRepo.slettMottaker(mottaker.id).right()
        }
    }
}
