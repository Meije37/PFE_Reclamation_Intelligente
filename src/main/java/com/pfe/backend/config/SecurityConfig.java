package com.pfe.backend.config;

import com.pfe.backend.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService customUserDetailsService;

    @Bean
    public UserDetailsService userDetailsService() {
        return customUserDetailsService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // Page d'erreur de Spring : sans cette ligne, toute erreur serveur (500)
                        // arrive au client sous la forme d'un faux « 403 accès refusé ».
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/agent/**").hasAnyRole("AGENT", "ADMIN")
                        .requestMatchers("/api/citoyen/**").hasAnyRole("CITOYEN", "ADMIN")
                        .requestMatchers("/api/reclamations/assigner/**", "/api/reclamations/statut/**").hasAnyRole("ADMIN", "AGENT")
                        .anyRequest().authenticated()
                )
                // Jeton absent, invalide ou EXPIRÉ → 401 (et non 403) : l'app mobile déconnecte
                // proprement (« Votre session a expiré ») et le web tente un renouvellement du jeton.
                // Un utilisateur connecté mais sans le bon rôle reçoit toujours 403.
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"statut\":401,\"message\":\"Votre session a expiré. Veuillez vous reconnecter.\"}");
                }))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String allowedOriginsProp;

    /** Origines web autorisées (propriété app.cors.allowed-origins, séparées par des virgules). */
    public static List<String> origines(String propriete) {
        return Arrays.stream(propriete.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.equals("*"))   // « * » refusé : n'importe quel site pourrait appeler l'API
                .toList();
    }

    /**
     * CORS = quels SITES WEB (autres que l'API elle-même) ont le droit d'appeler l'API depuis un navigateur.
     * Règle unique pour toute l'application (plus aucun @CrossOrigin dans les contrôleurs) :
     *  - seul le tableau de bord Angular (http://localhost:4200 en développement) est autorisé ;
     *  - l'application mobile n'est pas concernée : le CORS ne s'applique qu'aux navigateurs.
     * En production : variable d'environnement APP_CORS_ALLOWED_ORIGINS=https://admin.mon-domaine.mr
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origines(allowedOriginsProp));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
        // Lisibles par le navigateur : nom du fichier Excel exporté, délai après « trop de tentatives ».
        configuration.setExposedHeaders(Arrays.asList("Content-Disposition", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);   // le navigateur garde la réponse « preflight » 1 h

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        // Ta version impose de donner le service dès la création (le constructeur)
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());

        // On ajoute ensuite l'encodeur de mot de passe
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}