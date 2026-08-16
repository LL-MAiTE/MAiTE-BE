package com.likelion.hackathon.global.github;

import com.likelion.hackathon.global.exception.CustomException;
import com.likelion.hackathon.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GitHub 저장소에서 문서(md/txt 등)를 읽어오는 최소 구현.
 * 인증은 Personal Access Token(PAT)을 Bearer로 사용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private static final String API_BASE = "https://api.github.com";
    private static final Set<String> DOC_EXTENSIONS = Set.of("md", "mdx", "txt", "rst");
    private static final int MAX_FILES = 30;
    private static final int MAX_CONTENT_LENGTH = 50_000;

    private final RestTemplate restTemplate;

    public record RemoteFile(String path, String content) {}

    /**
     * 저장소의 기본 브랜치에서 문서 확장자 파일들을 읽어온다.
     * repoFullName 형식: "owner/repo"
     */
    public List<RemoteFile> fetchDocuments(String repoFullName, String accessToken) {
        HttpHeaders headers = buildHeaders(accessToken);

        String defaultBranch = fetchDefaultBranch(repoFullName, headers);
        List<Map<String, Object>> tree = fetchTree(repoFullName, defaultBranch, headers);

        List<RemoteFile> results = new ArrayList<>();
        for (Map<String, Object> entry : tree) {
            if (results.size() >= MAX_FILES) break;
            if (!"blob".equals(entry.get("type"))) continue;

            String path = (String) entry.get("path");
            if (!isDocFile(path)) continue;

            String content = fetchFileContent(repoFullName, path, headers);
            if (content == null) continue;

            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH);
            }
            results.add(new RemoteFile(path, content));
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private String fetchDefaultBranch(String repoFullName, HttpHeaders headers) {
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    API_BASE + "/repos/" + repoFullName, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return (String) resp.getBody().get("default_branch");
        } catch (HttpClientErrorException e) {
            log.warn("GitHub repo lookup failed for {}: {}", repoFullName, e.getMessage());
            throw new CustomException(ErrorCode.GITHUB_REPO_ACCESS_FAILED);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchTree(String repoFullName, String branch, HttpHeaders headers) {
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    API_BASE + "/repos/" + repoFullName + "/git/trees/" + branch + "?recursive=1",
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Object tree = resp.getBody().get("tree");
            return (List<Map<String, Object>>) tree;
        } catch (HttpClientErrorException e) {
            log.warn("GitHub tree lookup failed for {}: {}", repoFullName, e.getMessage());
            throw new CustomException(ErrorCode.GITHUB_REPO_ACCESS_FAILED);
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchFileContent(String repoFullName, String path, HttpHeaders headers) {
        try {
            String encodedPath = UriUtils.encodePath(path, StandardCharsets.UTF_8);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    API_BASE + "/repos/" + repoFullName + "/contents/" + encodedPath,
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = resp.getBody();
            String encoding = (String) body.get("encoding");
            String content = (String) body.get("content");
            if ("base64".equals(encoding) && content != null) {
                byte[] decoded = Base64.getMimeDecoder().decode(content);
                return new String(decoded, StandardCharsets.UTF_8);
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to fetch GitHub file content {}: {}", path, e.getMessage());
            return null;
        }
    }

    private boolean isDocFile(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = path.substring(dot + 1).toLowerCase();
        return DOC_EXTENSIONS.contains(ext);
    }

    private HttpHeaders buildHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return headers;
    }
}
