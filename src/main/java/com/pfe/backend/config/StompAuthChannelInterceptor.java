package com.pfe.backend.config;

import com.pfe.backend.service.CustomUserDetailsService;
import com.pfe.backend.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * La sécurité HTTP (SecurityConfig) laisse passer /ws sans vérifier de JWT,
 * car le token n'est pas disponible sous forme de header HTTP au moment du
 * handshake WebSocket (les clients STOMP l'envoient dans les headers de la
 * frame CONNECT elle-même). C'est donc ici, au niveau STOMP, que
 * l'authentification a réellement lieu.
 *
 * Sans Principal valide sur la frame CONNECT, convertAndSendToUser(email, ...)
 * ne pourrait jamais retrouver la session de l'utilisateur ciblé.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new IllegalArgumentException("Connexion WebSocket refusée : token JWT manquant.");
            }

            String jwt = authHeader.substring(7);
            String email = jwtService.extractUsername(jwt);

            if (email == null) {
                throw new IllegalArgumentException("Connexion WebSocket refusée : token JWT invalide.");
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (!jwtService.isTokenValid(jwt, userDetails.getUsername())) {
                throw new IllegalArgumentException("Connexion WebSocket refusée : token JWT expiré ou invalide.");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

            // Le Principal attaché ici devient le "user" utilisé par
            // convertAndSendToUser(...) côté serveur : son getName() doit
            // correspondre à l'email, exactement comme pour les endpoints
            // REST classiques (Authentication.getName() dans les contrôleurs).
            accessor.setUser(authentication);
        }

        return message;
    }
}