package com.pfe.backend;

import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.repository.UtilisateurRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class BackendApplication {


    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
    @Bean
    CommandLineRunner initAdmin(UtilisateurRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (repo.findByEmail("admin@pfe.mr").isEmpty()) {
                Utilisateur admin = Utilisateur.builder()
                        .nom("Admin")
                        .prenom("Principal")
                        .email("admin@pfe.mr")
                        .telephone("22222222")
                        .motDePasse(encoder.encode("Admin123"))
                        .role(Role.ADMIN) // Ici on met ADMIN
                        .actif(true)
                        .build();
                repo.save(admin);
                System.out.println("Compte Administrateur créé par défaut !");
            }
        };

    }
    @Bean
    public RestTemplate restTemplate() {
        // Timeouts courts : le microservice IA tourne en local (127.0.0.1),
        // si Python est lent/planté on ne doit pas faire attendre le citoyen
        // plus de quelques secondes avant de retomber sur le score de secours.
        // SimpleClientHttpRequestFactory est utilisé ici plutôt que
        // RestTemplateBuilder : classe stable de Spring Framework depuis
        // longtemps, contrairement au chemin de RestTemplateBuilder qui a
        // changé entre les versions récentes de Spring Boot.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); // 2 secondes
        factory.setReadTimeout(5000);    // 5 secondes
        return new RestTemplate(factory);
    }
}