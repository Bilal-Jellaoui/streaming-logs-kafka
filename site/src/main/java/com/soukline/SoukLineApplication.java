package com.soukline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

@SpringBootApplication
public class SoukLineApplication {

    private static final Logger logger = LoggerFactory.getLogger(SoukLineApplication.class);

    public static void main(String[] args) {
        // Garantir que le dossier logs/ existe avant l'initialisation de Tomcat et Logback
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            boolean created = logsDir.mkdirs();
            if (created) {
                System.out.println("📁 Dossier logs/ créé avec succès à la racine du projet.");
            }
        }

        SpringApplication.run(SoukLineApplication.class, args);

        System.out.println("==================================================================");
        System.out.println("  🌟 SOUKLINE E-COMMERCE (Membre 1) DÉMARRÉ AVEC SUCCÈS !        ");
        System.out.println("==================================================================");
        System.out.println("  🌐 Site web:           http://localhost:8080/                  ");
        System.out.println("  🛒 Panier:             http://localhost:8080/panier            ");
        System.out.println("  💳 Checkout:           http://localhost:8080/checkout          ");
        System.out.println("  🔧 Mode Panne (Admin): http://localhost:8080/admin             ");
        System.out.println("  ⚡ Panne ON:           http://localhost:8080/admin/panne?etat=on");
        System.out.println("  ⚡ Panne OFF:          http://localhost:8080/admin/panne?etat=off");
        System.out.println("  📄 Journal Tomcat:     logs/access.log                         ");
        System.out.println("  🚨 Journal Erreurs:    logs/error.log                          ");
        System.out.println("==================================================================");
    }
}
