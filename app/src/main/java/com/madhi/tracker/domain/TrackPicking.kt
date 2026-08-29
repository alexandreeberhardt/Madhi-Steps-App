package com.madhi.tracker.domain

/**
 * Quel point du tracé un doigt vise.
 *
 * Un point dessiné fait six pixels de large ; un doigt en couvre une
 * cinquantaine. Viser au pixel près serait injouable, surtout sur un guidon.
 * La tolérance est donc large, et c'est la distance qui départage.
 *
 * La géométrie vit ici, hors de Compose, pour la même raison que le reste de
 * `domain/Map*` : une sélection fausse se voit mal à l'œil et très bien en
 * test.
 */
object TrackPicking {

    /**
     * L'indice du point le plus proche de [tap], ou `null` si aucun n'est
     * assez près pour qu'on puisse dire qu'il était visé.
     *
     * À distance égale, le plus récent gagne : c'est la fin du tracé qu'on
     * regarde, et c'est elle qui est dessinée par-dessus.
     */
    fun nearest(
        points: List<NormalizedPoint>,
        tap: ScreenPoint,
        viewport: MapViewport,
        widthPixels: Double,
        heightPixels: Double,
        tolerancePixels: Double,
    ): Int? {
        if (points.isEmpty() || tolerancePixels <= 0.0) return null

        val maxSquared = tolerancePixels * tolerancePixels
        var bestIndex: Int? = null
        var bestSquared = Double.MAX_VALUE

        points.forEachIndexed { index, point ->
            val screen = viewport.toScreen(point, widthPixels, heightPixels)
            val dx = screen.x - tap.x
            val dy = screen.y - tap.y
            val squared = dx * dx + dy * dy
            // `<=` et non `<` : à égalité, le dernier vu l'emporte, donc le
            // plus récent.
            if (squared <= maxSquared && squared <= bestSquared) {
                bestSquared = squared
                bestIndex = index
            }
        }
        return bestIndex
    }
}
