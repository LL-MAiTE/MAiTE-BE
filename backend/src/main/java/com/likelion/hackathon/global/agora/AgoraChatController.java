package com.likelion.hackathon.global.agora;

import com.likelion.hackathon.global.agora.AgoraChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequiredArgsConstructor
public class AgoraChatController {

    private final AgoraChatService agoraChatService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Agora ConvAI LLM 엔드포인트 — OpenAI chat completions 호환 SSE.
     * Agora가 사용자 발화를 받으면 이 엔드포인트를 호출하고,
     * 우리가 3단계 파이프라인으로 응답을 결정해 스트리밍으로 반환한다.
     */
    @PostMapping(value = "/agora/chat-completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatCompletions(
            @RequestParam UUID meetingId,
            @RequestBody Map<String, Object> body) {

        Boolean stream = (Boolean) body.get("stream");
        if (!Boolean.TRUE.equals(stream)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stream must be true");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        String question = extractLastUserMessage(messages);

        SseEmitter emitter = new SseEmitter(60_000L);
        executor.execute(() -> agoraChatService.stream(meetingId, question, messages, emitter));
        return emitter;
    }

    private String extractLastUserMessage(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> msg = messages.get(i);
            if ("user".equals(msg.get("role"))) {
                return msg.getOrDefault("content", "");
            }
        }
        return "";
    }
}
