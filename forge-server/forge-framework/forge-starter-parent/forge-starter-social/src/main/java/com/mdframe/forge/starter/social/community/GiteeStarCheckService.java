package com.mdframe.forge.starter.social.community;

import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 用当前用户 Gitee access_token 检查是否已 Star 指定仓库。
 * 不落库 Token，仅在 OAuth 换票当下调用。
 */
@Slf4j
@Service
public class GiteeStarCheckService {

    private final GiteeCommunityLoginSupport communityLoginSupport;
    private final HttpClient httpClient;

    @Autowired
    public GiteeStarCheckService(GiteeCommunityLoginSupport communityLoginSupport) {
        this(communityLoginSupport, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    }

    GiteeStarCheckService(GiteeCommunityLoginSupport communityLoginSupport, HttpClient httpClient) {
        this.communityLoginSupport = communityLoginSupport;
        this.httpClient = httpClient;
    }

    public void assertStarred(String accessToken) {
        if (!communityLoginSupport.requireStar()) {
            return;
        }
        if (StrUtil.isBlank(accessToken)) {
            throw new BusinessException("未能获取 Gitee 授权，请重新登录");
        }
        GiteeCommunityLoginSupport.GiteeCommunitySettings config = communityLoginSupport.config();
        String owner = StrUtil.trimToEmpty(config.getOwner());
        String repo = StrUtil.trimToEmpty(config.getRepo());
        if (StrUtil.hasBlank(owner, repo)) {
            throw new BusinessException("Gitee 社区登录未配置仓库");
        }
        String url = "https://gitee.com/api/v5/user/starred/"
                + encode(owner) + "/" + encode(repo)
                + "?access_token=" + encode(accessToken);
        int timeoutMs = Math.max(500, config.getTimeoutMs());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Forge-Admin")
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            interpret(response.statusCode(), config.getRepoUrl());
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Gitee Star 校验被中断，请稍后重试");
        } catch (Exception exception) {
            log.warn("Gitee Star 校验失败: {}", exception.getMessage());
            throw new BusinessException("Gitee Star 校验超时或失败，请稍后重试");
        }
    }

    void interpret(int statusCode, String repoUrl) {
        if (statusCode == 204) {
            return;
        }
        if (statusCode == 404) {
            String link = StrUtil.blankToDefault(repoUrl, "https://gitee.com/ForgeLab/forge-admin");
            throw new BusinessException("请先给仓库点 Star 后再登录：" + link);
        }
        if (statusCode == 401 || statusCode == 403) {
            throw new BusinessException("Gitee 授权不足，请确认已勾选 projects 权限后重新登录");
        }
        throw new BusinessException("Gitee Star 校验失败，请稍后重试");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
