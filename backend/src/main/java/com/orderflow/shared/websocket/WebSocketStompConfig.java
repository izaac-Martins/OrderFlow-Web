package com.orderflow.shared.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.shared.error.CodigoErro;
import com.orderflow.shared.error.OrderFlowException;
import com.orderflow.shared.error.ProblemResponse;
import com.orderflow.shared.id.UuidV7Generator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Configuração WebSocket com protocolo STOMP 1.2 nativo (sem SockJS) no endpoint {@code /ws},
 * heartbeat simétrico de {@code 10000,10000} (10s/10s) e serialização de erros em RFC 7807.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketStompConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final ObjectMapper objectMapper;

    public WebSocketStompConfig(
            StompAuthChannelInterceptor stompAuthChannelInterceptor,
            ObjectMapper objectMapper
    ) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.objectMapper = objectMapper;
    }

    @Bean
    public TaskScheduler stompHeartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:*", "https://*.local", "http://192.168.*.*:*", "http://10.*.*.*:*");
        registry.setErrorHandler(stompErrorHandler());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS})
                .setTaskScheduler(stompHeartbeatTaskScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Bean
    public StompSubProtocolErrorHandler stompErrorHandler() {
        return new StompSubProtocolErrorHandler() {
            @Override
            public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                CodigoErro codigoErro = CodigoErro.ACESSO_NEGADO;
                String detail = cause.getMessage();
                if (cause instanceof OrderFlowException ofe) {
                    codigoErro = ofe.getCodigoErro();
                    detail = ofe.getMessage();
                }

                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
                accessor.setMessage(codigoErro.name() + ": " + detail);
                accessor.setLeaveMutable(true);

                ProblemResponse problem = new ProblemResponse(
                        codigoErro.getTypeUri(),
                        codigoErro.getDefaultTitle(),
                        codigoErro.getHttpStatus().value(),
                        codigoErro,
                        detail,
                        "/ws",
                        UuidV7Generator.nextId().toString().replace("-", ""),
                        Collections.emptyList(),
                        null,
                        Collections.emptyList()
                );

                byte[] payload;
                try {
                    payload = objectMapper.writeValueAsBytes(problem);
                } catch (Exception jsonEx) {
                    payload = detail.getBytes(StandardCharsets.UTF_8);
                }
                return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
            }
        };
    }
}
