package logs.collector;

/**
 * Un résultat publié par un moteur dans "stats-a" ou "stats-b" :
 * {"engine":"A","windowStart":1790000000000,"url":"/checkout","requests":42,"errors5xx":7}
 *
 * @param engine      "A" ou "B"
 * @param windowStart début de la fenêtre, en millisecondes depuis 1970
 * @param url         URL normalisée (ex : /produits/{id})
 * @param requests    nombre de requêtes pour cette URL dans cette fenêtre
 * @param errors5xx   dont erreurs serveur (500 à 599)
 */
public record StatEvent(String engine, long windowStart, String url, long requests, long errors5xx) { }
