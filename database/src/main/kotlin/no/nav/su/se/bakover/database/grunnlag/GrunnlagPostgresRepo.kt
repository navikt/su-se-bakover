package no.nav.su.se.bakover.database.grunnlag

internal class GrunnlagPostgresRepo(
    private val uføregrunnlagRepo: UføregrunnlagRepo,
    private val fradragsgrunnlagRepo: FradragsgrunnlagRepo,
) : GrunnlagRepo,
    UføregrunnlagRepo by uføregrunnlagRepo,
    FradragsgrunnlagRepo by fradragsgrunnlagRepo
