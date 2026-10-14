package com.soukline.service;

import com.soukline.model.Product;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service gérant le catalogue de produits SoukLine en mémoire (pas de base de données requise).
 */
@Service
public class ProductService {

    private final Map<Long, Product> productMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void initProducts() {
        addProduct(new Product(
                1L,
                "Tapis Berbère Beni Ourain 100% Laine",
                "Textile & Tapis",
                1450.00,
                "Tapis berbère authentique noué à la main dans le Moyen Atlas.",
                "Ce tapis Beni Ourain est tissé à la main avec une laine vierge non teintée de première qualité. Motifs géométriques traditionnels noirs sur fond écru naturel. Épais, moelleux et ultra chaleureux pour sublimer votre intérieur.",
                12, 4.9, 38, "Best-seller",
                "🧶", "/images/tapis.jpg"
        ));

        addProduct(new Product(
                2L,
                "Tajine Artisanal de Safi Émaillé 30cm",
                "Art de la Table",
                190.00,
                "Tajine de cuisson traditionnel en terre cuite émaillée pour mijoter vos plats.",
                "Fabriqué par les maîtres potiers de Safi, ce plat à tajine allie esthétique et praticité. Sa cheminée conique permet une cuisson à l'étouffée parfaite, préservant toutes les saveurs de vos mets et viandes.",
                25, 4.8, 54, "Tradition",
                "🍲", "/images/tajine.jpg"
        ));

        addProduct(new Product(
                3L,
                "Huile d'Argan Pure Bio 250ml",
                "Bien-être & Terroir",
                180.00,
                "Huile d'argan cosmétique extra-vierge certifiée bio issue du Souss.",
                "Véritable trésor marocain récolté et pressé à froid par une coopérative féminine dans la région de Taroudant. Riche en vitamine E et acides gras essentiels, elle nourrit intensément peau et cheveux.",
                40, 5.0, 89, "Bio Certifié",
                "✨", "/images/argan.jpg"
        ));

        addProduct(new Product(
                4L,
                "Babouches Royales en Cuir de Fès",
                "Mode & Maroquinerie",
                220.00,
                "Babouches pointues en cuir d'agneau souple tanné selon la tradition.",
                "Fabriquées au cœur de la médina de Fès, ces babouches royales offrent un confort exceptionnel et une finesse de couture remarquable. Semelle cuir renforcée pour une durabilité optimale.",
                30, 4.7, 42, "Coup de Cœur",
                "👞", "/images/babouches.jpg"
        ));

        addProduct(new Product(
                5L,
                "Théière Traditionnelle de Fès en Maillechort",
                "Art de la Table",
                290.00,
                "Théière marocaine ciselée main avec bec col de cygne pour le thé à la menthe.",
                "L'accessoire indispensable pour la cérémonie du thé marocain. Fabriquée en maillechort argenté gravé de motifs arabesques floraux. Capacité 6 verres, poignée athermique ornée.",
                18, 4.9, 61, "Best-seller",
                "🫖", "/images/theiere.jpg"
        ));

        addProduct(new Product(
                6L,
                "Lanterne Marocaine Ajourée en Cuivre",
                "Décoration & Luminaire",
                340.00,
                "Suspension orientale en cuivre martelé et ajouré pour jeux d'ombres féériques.",
                "Cette lanterne artisanale projette des motifs géométriques enchanteurs sur les murs lorsqu'elle est allumée. Idéale pour créer une ambiance feutrée et chaleureuse dans un salon ou patio.",
                15, 4.6, 29, "Nouveau",
                "🏮", "/images/lanterne.jpg"
        ));

        addProduct(new Product(
                7L,
                "Pouf Rond en Cuir Véritable Tanné Végétal",
                "Décoration & Mobilier",
                420.00,
                "Pouf d'assise traditionnel cousu et brodé main en cuir pleine fleur de Marrakech.",
                "Assise d'appoint ou repose-pieds élégant, ce pouf est cousu main et rehaussé de broderies délicates de soie végétale (Sabra). Cuir souple sans odeur, fermeture zip discrète sous la base.",
                22, 4.8, 35, "Populaire",
                "🛋️", "/images/pouf.jpg"
        ));

        addProduct(new Product(
                8L,
                "Mélange Épices Ras el Hanout Royal 150g",
                "Épices & Terroir",
                75.00,
                "Mélange prestigieux de 35 épices d'exception moulu à la commande.",
                "Composé de cardamome, boutons de rose séchés, cannelle de Ceylan, muscade, poivre de Jamaïque et safran de Taliouine. Parfume avec raffinement couscous, pastillas et ragoûts.",
                60, 4.9, 112, "Incontournable",
                "🌶️", "/images/epices.jpg"
        ));

        addProduct(new Product(
                9L,
                "Coffret Rituel Hammam & Soin Naturel",
                "Bien-être & Terroir",
                140.00,
                "Trio d'exception : Savon noir à l'Eucalyptus, Ghassoul de l'Atlas & Gant Kessa.",
                "Retrouvez les bienfaits du rituel purifiant du hammam traditionnel chez vous. Élimine les impuretés, adoucit l'épiderme et revitalise le corps en profondeur.",
                45, 4.8, 73, "Coffret Cadeau",
                "🌿", "/images/hammam.jpg"
        ));

        addProduct(new Product(
                10L,
                "Plateau Rond en Cuivre Gravé 45cm",
                "Art de la Table",
                380.00,
                "Grand plateau de service artisanal martelé à la main par les dinandiers de Fès.",
                "Pièce maîtresse pour servir le thé ou décorer une table basse. Motifs géométriques précis gravés à la pointe d'acier. Finition dorée brillante anti-oxydation.",
                14, 4.7, 19, "Artisanat d'Art",
                "🪙", "/images/plateau.jpg"
        ));
    }

    private void addProduct(Product product) {
        productMap.put(product.getId(), product);
    }

    public List<Product> getAllProducts() {
        return new ArrayList<>(productMap.values());
    }

    public Optional<Product> getProductById(Long id) {
        return Optional.ofNullable(productMap.get(id));
    }

    public List<Product> getFeaturedProducts() {
        return productMap.values().stream()
                .filter(p -> "Best-seller".equals(p.getBadge()) || "Coup de Cœur".equals(p.getBadge()) || "Bio Certifié".equals(p.getBadge()))
                .limit(4)
                .collect(Collectors.toList());
    }

    public List<Product> searchProducts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllProducts();
        }
        String cleanQuery = query.toLowerCase().trim();
        return productMap.values().stream()
                .filter(p -> p.getName().toLowerCase().contains(cleanQuery)
                        || p.getDescription().toLowerCase().contains(cleanQuery)
                        || p.getCategory().toLowerCase().contains(cleanQuery)
                        || p.getShortDescription().toLowerCase().contains(cleanQuery))
                .collect(Collectors.toList());
    }

    public List<Product> getProductsByCategory(String category) {
        if (category == null || category.trim().isEmpty() || "all".equalsIgnoreCase(category)) {
            return getAllProducts();
        }
        return productMap.values().stream()
                .filter(p -> p.getCategory().equalsIgnoreCase(category.trim()))
                .collect(Collectors.toList());
    }

    public List<String> getAllCategories() {
        return productMap.values().stream()
                .map(Product::getCategory)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<Product> filterAndSort(String category, String query, String sort) {
        List<Product> list;
        if (query != null && !query.trim().isEmpty()) {
            list = searchProducts(query);
            if (category != null && !category.isEmpty() && !"all".equalsIgnoreCase(category)) {
                list = list.stream().filter(p -> p.getCategory().equalsIgnoreCase(category)).collect(Collectors.toList());
            }
        } else if (category != null && !category.isEmpty() && !"all".equalsIgnoreCase(category)) {
            list = getProductsByCategory(category);
        } else {
            list = getAllProducts();
        }

        if ("price_asc".equalsIgnoreCase(sort)) {
            list.sort(Comparator.comparingDouble(Product::getPrice));
        } else if ("price_desc".equalsIgnoreCase(sort)) {
            list.sort(Comparator.comparingDouble(Product::getPrice).reversed());
        } else if ("rating".equalsIgnoreCase(sort)) {
            list.sort(Comparator.comparingDouble(Product::getRating).reversed());
        }

        return list;
    }
}
