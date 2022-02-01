package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.BosituasjongrunnlagRepo
import no.nav.su.se.bakover.domain.grunnlag.FradragsgrunnlagRepo
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagRepo

internal class GrunnlagPostgresRepo(
    private val fradragsgrunnlagRepo: FradragsgrunnlagPostgresRepo,
    private val bosituasjongrunnlagRepo: BosituasjongrunnlagPostgresRepo,
) : GrunnlagRepo,
    FradragsgrunnlagRepo by fradragsgrunnlagRepo,
    BosituasjongrunnlagRepo by bosituasjongrunnlagRepo
