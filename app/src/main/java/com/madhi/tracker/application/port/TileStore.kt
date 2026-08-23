package com.madhi.tracker.application.port

import com.madhi.tracker.domain.TileId

/**
 * D'où viennent les images de fond de la carte.
 *
 * Le port rend des octets et non une image décodée : le cœur n'a pas à
 * connaître le format bitmap d'Android. Il ne lève jamais : une tuile absente
 * est un cas normal — hors réseau, c'est même le cas courant — et doit laisser
 * la carte fonctionner avec le seul tracé.
 */
interface TileStore {

    /** Faux quand aucune source n'est configurée : la carte reste sur fond uni. */
    val isEnabled: Boolean

    /**
     * La mention légale à afficher sur la carte. Les licences de données
     * cartographiques l'imposent ; ce n'est pas une option d'affichage.
     */
    val attribution: String

    suspend fun tile(id: TileId): ByteArray?
}
