package com.c203.altteulbe.friend.web.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.c203.altteulbe.user.service.UserStatusService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class UserStatusWSController {
    private final UserStatusService userStatusService;

    // 온라인 상태 유지용 heartbeat
    @MessageMapping("/user/heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = Long.parseLong(headerAccessor.getSessionAttributes().get("userId").toString());
        userStatusService.refreshOnline(userId);
    }
}