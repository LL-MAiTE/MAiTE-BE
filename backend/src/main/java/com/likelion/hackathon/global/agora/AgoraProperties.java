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

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getAppCertificate() { return appCertificate; }
    public void setAppCertificate(String appCertificate) { this.appCertificate = appCertificate; }

    public String getCustomerKey() { return customerKey; }
    public void setCustomerKey(String customerKey) { this.customerKey = customerKey; }

    public String getCustomerSecret() { return customerSecret; }
    public void setCustomerSecret(String customerSecret) { this.customerSecret = customerSecret; }

    private String callbackUrl;
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
}
