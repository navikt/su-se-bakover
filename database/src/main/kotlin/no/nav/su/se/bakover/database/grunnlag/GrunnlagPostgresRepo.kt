package no.nav.su.se.bakover.database.grunnlag

internal class GrunnlagPostgresRepo(
    private val fradragsgrunnlagRepo: FradragsgrunnlagPostgresRepo,
    private val bosituasjongrunnlagRepo: BosituasjongrunnlagRepo
) : GrunnlagRepo,
    FradragsgrunnlagRepo by fradragsgrunnlagRepo,
    BosituasjongrunnlagRepo by bosituasjongrunnlagRepo
