package com.likelion.hackathon.global.agora;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.likelion.hackathon.domain.meeting.service.MeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/agora/callback")
@RequiredArgsConstructor
public class AgoraCallbackController {

    private final MeetingService meetingService;

    @PostMapping
    public ResponseEntity<Void> handleCallback(@RequestBody ConversationCallback payload) {
        if (payload == null || payload.channel() == null) {
            return ResponseEntity.ok().build();
        }

        log.info("Agora callback: channel={}, user={}, agent={}",
                payload.channel(),
                payload.userTranscription(),
                payload.agentResponse());

        meetingService.saveConversationTurn(
                payload.channel(),
                payload.userTranscription(),
                payload.agentResponse(),
                payload.startMs()
        );

        return ResponseEntity.ok().build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConversationCallback(
            String channel,
            String agentId,
            Integer turnId,
            String userTranscription,
            String agentResponse,
            long startMs,
            long endMs
    ) {}
}
