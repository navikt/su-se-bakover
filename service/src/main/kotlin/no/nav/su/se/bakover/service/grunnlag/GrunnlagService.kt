package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.database.grunnlag.GrunnlagRepo
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.singleOrThrow
import no.nav.su.se.bakover.domain.grunnlag.throwIfMultiple
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.util.UUID

interface GrunnlagService {
    fun lagreFradragsgrunnlag(behandlingId: UUID, fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>)
    fun hentFradragsgrunnlag(behandlingId: UUID): List<Grunnlag.Fradragsgrunnlag>
    fun lagreBosituasjongrunnlag(behandlingId: UUID, bosituasjongrunnlag: List<Grunnlag.Bosituasjon>)
    fun hentBosituasjongrunnlang(behandlingId: UUID): List<Grunnlag.Bosituasjon>

    sealed class KunneIkkeLeggeTilGrunnlagsdata {
        object FantIkkeBehandling : KunneIkkeLeggeTilGrunnlagsdata()
        object UgyldigTilstand : KunneIkkeLeggeTilGrunnlagsdata()
    }
}

internal class GrunnlagServiceImpl(
    private val grunnlagRepo: GrunnlagRepo,
) : GrunnlagService {
    override fun lagreFradragsgrunnlag(behandlingId: UUID, fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) {
        return grunnlagRepo.lagreFradragsgrunnlag(behandlingId, fradragsgrunnlag)
    }

    override fun hentFradragsgrunnlag(behandlingId: UUID): List<Grunnlag.Fradragsgrunnlag> {
        return grunnlagRepo.hentFradragsgrunnlag(behandlingId)
    }

    override fun lagreBosituasjongrunnlag(behandlingId: UUID, bosituasjongrunnlag: List<Grunnlag.Bosituasjon>) {
        // Se RevurderingServiceImpl og SøknadsbehandlingServiceImpl for opprydning av Behandlingsinformasjon.formue
        fjernEpsFradragHvisEpsHarEndretSeg(behandlingId, bosituasjongrunnlag)
        return grunnlagRepo.lagreBosituasjongrunnlag(behandlingId, bosituasjongrunnlag)
    }

    private fun fjernEpsFradragHvisEpsHarEndretSeg(
        behandlingId: UUID,
        nyBosituasjon: List<Grunnlag.Bosituasjon>,
    ) {
        grunnlagRepo.hentFradragsgrunnlag(behandlingId).ifNotEmpty {
            val fradrag: List<Grunnlag.Fradragsgrunnlag> = this
            grunnlagRepo.hentBosituasjongrunnlag(behandlingId).ifNotEmpty {
                val gjeldendeBosituasjon = this.singleOrThrow()
                val gjeldendeEpsFnr =
                    (gjeldendeBosituasjon as? Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer)?.fnr
                        ?: (gjeldendeBosituasjon as? Grunnlag.Bosituasjon.Ufullstendig.HarEps)?.fnr
                val nyEpsFnr =
                    (nyBosituasjon.throwIfMultiple() as? Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer)?.fnr
                        ?: (gjeldendeBosituasjon as? Grunnlag.Bosituasjon.Ufullstendig.HarEps)?.fnr
                // TODO jah: Dette holder ikke ved oppdatering av revurdering dersom man har endret fnr eller satt den til null
                if (gjeldendeEpsFnr != nyEpsFnr) {
                    if (fradrag.any { it.fradrag.tilhører == FradragTilhører.EPS }) {
                        // Fjerner de fradragene som angår EPS hvis det skjer en endring i bosituasjon
                        grunnlagRepo.lagreFradragsgrunnlag(
                            behandlingId,
                            fradrag.filter { it.fradrag.tilhører != FradragTilhører.EPS },
                        )
                    }
                }
            }
        }
    }

    override fun hentBosituasjongrunnlang(behandlingId: UUID): List<Grunnlag.Bosituasjon> {
        return grunnlagRepo.hentBosituasjongrunnlag(behandlingId)
    }
}
