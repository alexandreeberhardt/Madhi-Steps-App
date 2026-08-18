package com.madhi.tracker.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Vérifie le schéma Room **exporté**, pas celui compilé.
 *
 * Le fichier JSON est ce sur quoi s'appuient les tests de migration : s'il
 * n'était pas exporté, ou pas commité, la première migration serait écrite à
 * l'aveugle et une mise à jour d'APK pourrait effacer des positions non
 * synchronisées (ADR-005).
 *
 * Ce test échoue donc si quelqu'un désactive `exportSchema` — y compris par
 * inadvertance en nettoyant le build.
 */
class SchemaContractTest {

    private val schemaDirectory = File("schemas/com.madhi.tracker.adapter.output.persistence.room.TrackerDatabase")

    private fun schema(version: Int): JsonObject {
        val file = File(schemaDirectory, "$version.json")
        assertTrue(
            "Schéma version $version absent. exportSchema est-il toujours activé, " +
                "et le fichier est-il commité ?",
            file.exists(),
        )
        return Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
    }

    private fun locationsTable(version: Int): JsonObject =
        schema(version).getValue("entities").jsonArray.first().jsonObject

    private fun fieldsOf(version: Int) =
        locationsTable(version).getValue("fields").jsonArray.map { it.jsonObject }

    @Test
    fun `le schema de la version courante est exporte et commite`() {
        assertEquals(1, schema(version = 1).getValue("version").jsonPrimitive.content.toInt())
    }

    @Test
    fun `la table des positions porte les colonnes du contrat`() {
        val columns = fieldsOf(1).map { it.getValue("columnName").jsonPrimitive.content }

        assertEquals(
            listOf(
                "id", "latitude", "longitude", "recorded_at",
                "accuracy_m", "altitude_m", "speed_mps", "battery_percent",
                "sync_state", "attempt_count", "last_attempt_at", "last_error_code",
            ),
            columns,
        )
    }

    @Test
    fun `les champs obligatoires du contrat ne sont pas nullables`() {
        // arch/00 §5 : ces champs ne changent pas en V2. Les rendre nullables
        // permettrait d'ecrire un point inexploitable par le serveur.
        val notNull = fieldsOf(1)
            .filter { it["notNull"]?.jsonPrimitive?.content == "true" }
            .map { it.getValue("columnName").jsonPrimitive.content }

        assertTrue(notNull.containsAll(listOf("id", "latitude", "longitude", "recorded_at", "sync_state")))
    }

    @Test
    fun `l'index qui sert a chaque envoi existe`() {
        // Sans lui, chaque lot balaierait toute la table : environ cent mille
        // lignes au bout d'un an.
        val indices = locationsTable(1).getValue("indices").jsonArray
            .map { it.jsonObject.getValue("columnNames").jsonArray.toString() }

        assertTrue(indices.any { it.contains("sync_state") && it.contains("recorded_at") })
    }

    @Test
    fun `l'identifiant est la cle primaire, base de l'idempotence`() {
        val primaryKey = locationsTable(1).getValue("primaryKey").jsonObject
            .getValue("columnNames").jsonArray

        assertEquals(1, primaryKey.size)
        assertEquals("id", primaryKey.first().jsonPrimitive.content)
    }

    @Test
    fun `aucune migration destructrice n'est configuree`() {
        // fallbackToDestructiveMigration effacerait les positions en attente
        // a la premiere migration manquante. Interdit, y compris en debug.
        // On cherche l'appel, pas le mot : les commentaires qui expliquent
        // pourquoi c'est interdit doivent pouvoir le nommer.
        val call = Regex("""\.fallbackToDestructiveMigration""")
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .filter { call.containsMatchIn(it.readText()) }
            .toList()

        assertTrue("fallbackToDestructiveMigration trouvé dans $offenders", offenders.isEmpty())
    }

    @Test
    fun `le DAO n'expose aucune suppression`() {
        // La V1 ne supprime rien : l'absence de la requete le garantit mieux
        // qu'un commentaire.
        val dao = File("src/main/java/com/madhi/tracker/adapter/output/persistence/room/LocationDao.kt").readText()

        assertFalse(dao.contains("DELETE"))
        assertFalse(dao.contains("@Delete"))
    }
}
