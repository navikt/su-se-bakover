package no.nav.su.se.bakover.database.grunnlag

internal class GrunnlagPostgresRepo(
    private val fradragsgrunnlagRepo: FradragsgrunnlagPostgresRepo,
) : GrunnlagRepo,
    FradragsgrunnlagRepo by fradragsgrunnlagRepo
