package com.likelion.hackathon.global.agora;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agora")
public class AgoraProperties {
    private String appId;
    private String appCertificate;
    private String customerKey;
    private String customerSecret;
    private String callbackUrl;
    private String agentPipelineId;
    private String asrResourceId;
    private String ttsResourceId;
    private String liveavatarApiKey;
    private String liveavatarAvatarId;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getAppCertificate() { return appCertificate; }
    public void setAppCertificate(String appCertificate) { this.appCertificate = appCertificate; }

    public String getCustomerKey() { return customerKey; }
    public void setCustomerKey(String customerKey) { this.customerKey = customerKey; }

    public String getCustomerSecret() { return customerSecret; }
    public void setCustomerSecret(String customerSecret) { this.customerSecret = customerSecret; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public String getAgentPipelineId() { return agentPipelineId; }
    public void setAgentPipelineId(String agentPipelineId) { this.agentPipelineId = agentPipelineId; }

    public String getAsrResourceId() { return asrResourceId; }
    public void setAsrResourceId(String asrResourceId) { this.asrResourceId = asrResourceId; }

    public String getTtsResourceId() { return ttsResourceId; }
    public void setTtsResourceId(String ttsResourceId) { this.ttsResourceId = ttsResourceId; }

    public String getLiveavatarApiKey() { return liveavatarApiKey; }
    public void setLiveavatarApiKey(String liveavatarApiKey) { this.liveavatarApiKey = liveavatarApiKey; }

    public String getLiveavatarAvatarId() { return liveavatarAvatarId; }
    public void setLiveavatarAvatarId(String liveavatarAvatarId) { this.liveavatarAvatarId = liveavatarAvatarId; }
}
