package com.pfe.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Active STOMP au-dessus de WebSocket pour les notifications temps réel.
 *
 * Flux : le client (Angular ou Flutter) ouvre une connexion WebSocket sur
 * /ws, envoie une frame CONNECT avec un header "Authorization: Bearer <jwt>",
 * puis s'abonne à /user/queue/notifications pour recevoir SES notifications.
 *
 * Le serveur pousse une notification via
 * SimpMessagingTemplate.convertAndSendToUser(email, "/queue/notifications", dto)
 * depuis NotificationService.creer(...).
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Pas de SockJS : un WebSocket natif suffit et simplifie le client
        // mobile (stomp_dart_client) qui ne gère pas le fallback SockJS.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic  -> diffusions générales (non utilisé pour l'instant)
        // /queue  -> messages point-à-point, combiné à /user pour le ciblage
        //            par utilisateur (voir userDestinationPrefix)
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Authentifie chaque frame CONNECT via le JWT transmis en header
        // STOMP (voir StompAuthChannelInterceptor).
        registration.interceptors(stompAuthChannelInterceptor);
    }
}