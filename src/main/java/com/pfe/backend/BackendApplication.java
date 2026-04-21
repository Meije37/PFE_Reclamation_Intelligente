package com.pfe.backend;

import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.repository.UtilisateurRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
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
        return new RestTemplate();
    }
}
