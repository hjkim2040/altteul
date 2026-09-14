package com.c203.altteulbe.config;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.c203.altteulbe.common.security.utils.JWTUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	private final JWTUtil jwtUtil;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
			.setAllowedOriginPatterns(
				"http://localhost:80",
				"http://localhost:443",
				"http://localhost:5173",
				"http://frontend:80",
				"http://frontend:443",
				"http://frontend:5173",
				"http://host.docker.internal:80",
				"http://host.docker.internal:443",
				"http://host.docker.internal:5173",
				"https://localhost:80",
				"https://localhost:443",
				"https://localhost:5173",
				"https://frontend:80",
				"https://frontend:443",
				"https://frontend:5173",
				"https://host.docker.internal:80",
				"https://host.docker.internal:443",
				"https://host.docker.internal:5173",
				"https://i12c203.p.ssafy.io",
				"https://i12c203.p.ssafy.io:443",
				"https://i12c203.p.ssafy.io:8443"
			)
			.withSockJS();

		registry.addEndpoint("/ws")
			.setAllowedOriginPatterns(
				"http://localhost:80",
				"http://localhost:5173",
				"http://localhost:5080",
				"http://frontend:80",
				"http://frontend:5173",
				"http://host.docker.internal:80",
				"http://host.docker.internal:5173",
				"https://localhost:80",
				"https://localhost:5173",
				"https://frontend:80",
				"https://frontend:5173",
				"https://host.docker.internal:80",
				"https://host.docker.internal:5173"
			);

		// .setAllowedOrigins("http://localhost:3000", "http://localhost:80")

	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/sub");
		registry.setApplicationDestinationPrefixes("/pub");
		registry.setPreservePublishOrder(true); // configureMessageConverters를 사용하기 위해 추가
	}

	// websocket 연결 전에 jwt 토큰으로 인증 처리
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(new ChannelInterceptor() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
				StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
				if (StompCommand.CONNECT == accessor.getCommand()) {
					Optional<String> tokenOptional = Optional.ofNullable(
						accessor.getFirstNativeHeader("Authorization"));
					String jwtToken = tokenOptional
						.filter(token -> token.startsWith("Bearer "))
						.map(token -> token.substring(7))
						.filter(token -> !jwtUtil.isExpired(token))
						.orElseThrow(() -> new RuntimeException("유효하지 않은 토큰 입니다."));
					Long userId = jwtUtil.getId(jwtToken);
					accessor.getSessionAttributes().put("userId", userId);
				}
				return message;
			}
		});
	}

	// Payload를 json(dto객체)로 입력했을 경우를 위한 로직
	@Override
	public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
		DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
		resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);

		ObjectMapper objectMapper = new ObjectMapper();
		// Java 8 시간 타입을 처리하기 위한 모듈 등록
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setObjectMapper(objectMapper);
		converter.setContentTypeResolver(resolver);

		messageConverters.add(converter);
		return false;
	}
}
