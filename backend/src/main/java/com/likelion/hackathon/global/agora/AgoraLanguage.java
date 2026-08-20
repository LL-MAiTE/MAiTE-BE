package com.likelion.hackathon.global.agora;

import java.util.Arrays;

/**
 * 라이브 통화의 ASR(Deepgram)/TTS(MiniMax)/LLM 응답 언어를 한 곳에서 관리한다.
 *
 * 예전엔 이 세 군데(+인사말, +보류/스몰토크/메타 고정 문구)가 전부 한국어로 하드코딩돼
 * 있어서, agenda.counterpartLanguage를 "영어"로 설정해도 실제 통화는 한국어 ASR/TTS로
 * 진행되는 문제가 있었다(사용자가 직접 발견). 이제 이 enum이 그 다섯 가지를 언어별로
 * 한 묶음으로 들고 있고, AgoraLanguage.from(agenda.getCounterpartLanguage())로 고른다.
 *
 * TTS voice_id는 MiniMax 공식 문서(system voice id list)에서 확인한 예시값이다 —
 * 계정/모델에 따라 사용 불가능한 값일 수 있으니, join 실패 로그(agora ConvAI error)에
 * voice_id 관련 오류가 뜨면 이 값부터 의심할 것.
 */
public enum AgoraLanguage {
    KO(
            "ko", "Korean_CalmLady", "한국어",
            "안녕하세요. '%s' 회의를 위한 AI 협상 대리인입니다. 회의를 시작하겠습니다.",
            "확인이 필요한 사항입니다. 내부 검토 후 답변드리겠습니다.",
            "네, 안녕하세요.",
            "죄송합니다, 다시 한 번 말씀해 주시겠어요?"
    ),
    EN(
            "en", "English_CalmWoman", "영어(English)",
            "Hello. I'm the AI negotiation representative for '%s'. Let's begin the meeting.",
            "That requires internal review. We'll get back to you shortly.",
            "Hello, nice to meet you.",
            "Sorry, could you say that again?"
    ),
    JA(
            "ja", "Japanese_CalmLady", "일본어(Japanese)",
            "こんにちは。「%s」の会議のためのAI交渉代理人です。会議を始めましょう。",
            "確認が必要な事項です。内部検討の後、回答いたします。",
            "こんにちは。",
            "すみません、もう一度おっしゃっていただけますか？"
    ),
    DE(
            "de", "German_SweetLady", "독일어(German)",
            "Hallo. Ich bin der KI-Verhandlungsvertreter für '%s'. Lassen Sie uns beginnen.",
            "Das muss intern geprüft werden. Wir melden uns in Kürze.",
            "Hallo, schön Sie kennenzulernen.",
            "Entschuldigung, könnten Sie das wiederholen?"
    ),
    ZH(
            "zh", "Chinese (Mandarin)_Warm_Bestie", "중국어(Chinese)",
            "您好，我是「%s」会议的AI谈判代表，我们开始吧。",
            "这需要内部审核，我们会尽快回复您。",
            "您好。",
            "不好意思，您能再说一遍吗？"
    ),
    FR(
            "fr", "French_FemaleAnchor", "프랑스어(French)",
            "Bonjour. Je suis le représentant IA pour la négociation « %s ». Commençons.",
            "Cela nécessite un examen interne. Nous reviendrons vers vous bientôt.",
            "Bonjour, ravi de vous rencontrer.",
            "Désolé, pouvez-vous répéter ?"
    );

    // Deepgram nova-3가 실제로 받아들이는 bare 언어 코드(en-US 같은 지역 변형은 언어별로
    // 유효한 것도/아닌 것도 있어서(de-DE는 무효, en-US는 유효 등) 이 bare 코드로 통일한다.
    private final String deepgramCode;
    private final String ttsVoiceId;
    private final String displayName;
    private final String greetingTemplate;
    private final String holdMessage;
    private final String smallTalkFallback;
    private final String metaFallback;

    AgoraLanguage(String deepgramCode, String ttsVoiceId, String displayName, String greetingTemplate,
                  String holdMessage, String smallTalkFallback, String metaFallback) {
        this.deepgramCode = deepgramCode;
        this.ttsVoiceId = ttsVoiceId;
        this.displayName = displayName;
        this.greetingTemplate = greetingTemplate;
        this.holdMessage = holdMessage;
        this.smallTalkFallback = smallTalkFallback;
        this.metaFallback = metaFallback;
    }

    public String getDeepgramCode() {
        return deepgramCode;
    }

    public String getTtsVoiceId() {
        return ttsVoiceId;
    }

    /** LLM 프롬프트에 "이 언어로만 응답하라"고 지시할 때 쓰는 표시명 (한국어 표기 + 영문 병기). */
    public String getDisplayName() {
        return displayName;
    }

    public String buildGreeting(String meetingTitle) {
        return String.format(greetingTemplate, meetingTitle);
    }

    public String getHoldMessage() {
        return holdMessage;
    }

    public String getSmallTalkFallback() {
        return smallTalkFallback;
    }

    public String getMetaFallback() {
        return metaFallback;
    }

    /** agenda.counterpartLanguage(BCP-47, 예: "en-US") 기준 매핑. null/빈 값/미지원 언어면 한국어로 안전하게 폴백. */
    public static AgoraLanguage from(String bcp47OrNull) {
        if (bcp47OrNull == null || bcp47OrNull.isBlank()) return KO;
        String prefix = bcp47OrNull.trim().toLowerCase().split("-")[0];
        return Arrays.stream(values())
                .filter(l -> l.deepgramCode.equals(prefix))
                .findFirst()
                .orElse(KO);
    }
}
