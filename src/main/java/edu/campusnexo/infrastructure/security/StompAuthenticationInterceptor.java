package edu.campusnexo.infrastructure.security;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * The live feed carries the occupancy of every room, so it needs a viewer too: the STOMP {@code CONNECT}
 * frame must bring {@code Authorization: Bearer <token>} (browsers cannot set headers on the WebSocket
 * handshake itself, and a token in the URL would end up in access logs). Clients may only subscribe to
 * the two dashboard topics and may not send messages.
 */
public final class StompAuthenticationInterceptor implements ChannelInterceptor {
    private static final Logger audit = LoggerFactory.getLogger("campusnexo.audit");
    private static final String SESSION_PRINCIPAL = Principal.class.getName();
    private static final Set<String> TOPICS = Set.of("/topic/twin", "/topic/traces");

    private final Authenticator authenticator;

    StompAuthenticationInterceptor(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        Map<String, Object> session = accessor.getSessionAttributes();
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            Optional<Principal> principal = authenticator.resolve(
                    accessor.getFirstNativeHeader(Authenticator.AUTHORIZATION_HEADER), null)
                    .filter(caller -> caller.role().satisfies(Role.Access.VIEWER));
            if (principal.isEmpty()) {
                audit.warn("AUDIT denegado motivo=websocket_sin_credenciales sesion={}", accessor.getSessionId());
                throw new MessageDeliveryException("Inicia sesión para recibir el gemelo en vivo");
            }
            if (session != null) {
                session.put(SESSION_PRINCIPAL, principal.get());
            }
        } else if (command == StompCommand.SUBSCRIBE) {
            if (session == null || !(session.get(SESSION_PRINCIPAL) instanceof Principal)) {
                throw new MessageDeliveryException("Sesión STOMP sin autenticar");
            }
            if (!TOPICS.contains(accessor.getDestination())) {
                throw new MessageDeliveryException("Solo se permiten /topic/twin y /topic/traces");
            }
        } else if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("El canal en vivo es de solo lectura");
        }
        return message;
    }
}
