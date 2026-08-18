package com.likelion.hackathon.global.notion;

import com.likelion.hackathon.global.exception.CustomException;
import com.likelion.hackathon.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Notion 페이지를 루트로 삼아, 그 하위 페이지(child_page)까지 재귀적으로 훑어
 * 텍스트 위주 블록을 이어붙여 문서화하는 최소 구현.
 * 인증은 Internal Integration 토큰을 Bearer로 사용 (해당 페이지에 그 통합이
 * 미리 공유되어 있어야 함 — Notion 특성상 GitHub PAT과 달리 페이지 단위 권한 부여 필요).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotionService {

    private static final String API_BASE = "https://api.notion.com/v1";
    private static final String NOTION_VERSION = "2022-06-28";
    private static final int MAX_PAGES = 30;
    private static final int MAX_CONTENT_LENGTH = 50_000;

    private final RestTemplate restTemplate;

    public record RemotePage(String pageId, String title, String content) {}

    /**
     * rootPageId부터 시작해 하위 페이지까지 BFS로 훑는다.
     * 루트 자체가 접근 불가하면(토큰/페이지ID 오류 등) 예외를 던지고,
     * 하위 페이지 개별 실패는 건너뛴다.
     */
    public List<RemotePage> fetchDocuments(String rootPageId, String accessToken) {
        HttpHeaders headers = buildHeaders(accessToken);

        List<RemotePage> results = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(rootPageId);

        while (!queue.isEmpty() && results.size() < MAX_PAGES) {
            String pageId = queue.poll();
            if (!visited.add(pageId)) continue;

            tryFetchPage(pageId, headers, queue).ifPresent(results::add);
        }

        if (results.isEmpty()) {
            throw new CustomException(ErrorCode.NOTION_PAGE_ACCESS_FAILED);
        }
        return results;
    }

    private Optional<RemotePage> tryFetchPage(String pageId, HttpHeaders headers, Deque<String> queue) {
        try {
            String title = fetchPageTitle(pageId, headers);
            List<String> childPageIds = new ArrayList<>();
            String content = fetchBlockChildrenText(pageId, headers, childPageIds);
            queue.addAll(childPageIds);
            return Optional.of(new RemotePage(pageId, title, truncate(content)));
        } catch (Exception e) {
            log.warn("Failed to fetch Notion page {}: {}", pageId, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchPageTitle(String pageId, HttpHeaders headers) {
        ResponseEntity<Map> resp = restTemplate.exchange(
                API_BASE + "/pages/" + pageId, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<String, Object> properties = (Map<String, Object>) resp.getBody().get("properties");
        for (Object value : properties.values()) {
            Map<String, Object> prop = (Map<String, Object>) value;
            if ("title".equals(prop.get("type"))) {
                String title = joinPlainText((List<Map<String, Object>>) prop.get("title"));
                return title.isBlank() ? "Untitled" : title;
            }
        }
        return "Untitled";
    }

    @SuppressWarnings("unchecked")
    private String fetchBlockChildrenText(String blockId, HttpHeaders headers, List<String> childPageIds) {
        StringBuilder sb = new StringBuilder();
        String cursor = null;
        do {
            String url = API_BASE + "/blocks/" + blockId + "/children?page_size=100"
                    + (cursor != null ? "&start_cursor=" + cursor : "");
            ResponseEntity<Map> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = resp.getBody();
            List<Map<String, Object>> blocks = (List<Map<String, Object>>) body.get("results");

            for (Map<String, Object> block : blocks) {
                String type = (String) block.get("type");
                if ("child_page".equals(type)) {
                    childPageIds.add((String) block.get("id"));
                    continue;
                }

                String text = extractRichText(block, type);
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }

                if (Boolean.TRUE.equals(block.get("has_children")) && !"child_database".equals(type)) {
                    sb.append(fetchBlockChildrenText((String) block.get("id"), headers, childPageIds));
                }
            }

            cursor = Boolean.TRUE.equals(body.get("has_more")) ? (String) body.get("next_cursor") : null;
        } while (cursor != null);

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractRichText(Map<String, Object> block, String type) {
        Object typeData = block.get(type);
        if (!(typeData instanceof Map)) return null;
        Object richText = ((Map<String, Object>) typeData).get("rich_text");
        if (!(richText instanceof List)) return null;
        return joinPlainText((List<Map<String, Object>>) richText);
    }

    private String joinPlainText(List<Map<String, Object>> richText) {
        if (richText == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> rt : richText) {
            Object plain = rt.get("plain_text");
            if (plain != null) sb.append(plain);
        }
        return sb.toString();
    }

    private String truncate(String content) {
        return content.length() > MAX_CONTENT_LENGTH ? content.substring(0, MAX_CONTENT_LENGTH) : content;
    }

    private HttpHeaders buildHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Notion-Version", NOTION_VERSION);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
