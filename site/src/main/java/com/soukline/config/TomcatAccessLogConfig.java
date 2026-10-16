package com.soukline.config;

import org.apache.catalina.valves.AccessLogValve;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Configuration personnalisée de la valve de journalisation Tomcat.
 * Garantit que les logs d'accès sont écrits dans logs/access.log à la racine du projet
 * au format Tomcat Common Log Format (CLF), avec rotation désactivée et vidage immédiat (non bufférisé).
 */
@Configuration
public class TomcatAccessLogConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> {
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            AccessLogValve valve = new AccessLogValve();
            valve.setDirectory(logsDir.getAbsolutePath());
            valve.setPrefix("access");
            valve.setSuffix(".log");
            valve.setPattern("common");
            valve.setRotatable(false);
            valve.setRenameOnRotate(false);
            valve.setBuffered(false); // Écriture immédiate pour la lecture en continu par Membre 5 (tail -f)
            valve.setCheckExists(true);

            factory.addEngineValves(valve);
        };
    }
}
