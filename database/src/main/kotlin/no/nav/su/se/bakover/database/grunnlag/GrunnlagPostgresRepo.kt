package no.nav.su.se.bakover.database.grunnlag

internal class GrunnlagPostgresRepo(
    private val fradragsgrunnlagRepo: FradragsgrunnlagPostgresRepo,
    private val bosituasjongrunnlagRepo: BosituasjongrunnlagPostgresRepo,
) : GrunnlagRepo,
    FradragsgrunnlagRepo by fradragsgrunnlagRepo,
    BosituasjongrunnlagRepo by bosituasjongrunnlagRepo
