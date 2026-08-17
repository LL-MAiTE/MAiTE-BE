package com.likelion.hackathon;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.Inflater;

/**
 * 콘솔에서 발급받은 토큰의 내부 바이너리 구조를 출력하는 진단용 코드.
 * 여기에 콘솔 토큰을 붙여넣고 main 실행 → 구조 확인 후 AgoraTokenUtil 수정.
 */
public class AgoraTokenDecodeTest {

    // ★ 콘솔에서 새로 발급받은 토큰 붙여넣기 (방금 전 것 말고 새 것으로)
    private static final String CONSOLE_TOKEN = "";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Agora Token Decoder ===");
        System.out.println("Token: " + CONSOLE_TOKEN.substring(0, 40) + "...");
        System.out.println("Prefix: " + CONSOLE_TOKEN.substring(0, 3));

        String rest = CONSOLE_TOKEN.substring(3);

        // 32글자가 유효한 hex appId인지 확인
        String maybe32 = rest.substring(0, Math.min(32, rest.length()));
        boolean looksLikeHexAppId = maybe32.matches("[0-9a-fA-F]{32}");
        System.out.println("\n--- AppId location ---");
        System.out.println("Chars 3-35: " + maybe32);
        System.out.println("Is valid hex appId (32 chars): " + looksLikeHexAppId);

        // case A: appId가 평문으로 앞에 있는 경우
        String b64A = looksLikeHexAppId ? rest.substring(32) : null;
        // case B: "007" 바로 뒤가 base64
        String b64B = rest;

        System.out.println("\n--- Trying Case A (007 + appId + base64) ---");
        if (b64A != null) tryDecode("Case A", b64A);

        System.out.println("\n--- Trying Case B (007 + base64) ---");
        tryDecode("Case B", b64B);
    }

    private static void tryDecode(String label, String b64) {
        try {
            byte[] compressed = Base64.getDecoder().decode(b64);
            System.out.println(label + " base64 decode OK, bytes: " + compressed.length);

            byte[] raw = zlibDecompress(compressed);
            System.out.println(label + " zlib decompress OK, bytes: " + raw.length);
            System.out.println(label + " hex dump (first 80 bytes):");
            printHex(raw, Math.min(80, raw.length));

            // packed string 읽기 시도 (LE2 length prefix)
            if (raw.length >= 2) {
                int strLen = (raw[0] & 0xFF) | ((raw[1] & 0xFF) << 8);
                System.out.println(label + " first field LE2 length = " + strLen);
                if (strLen > 0 && strLen < raw.length - 2) {
                    String str = new String(raw, 2, strLen, "UTF-8");
                    System.out.println(label + " first packed string = \"" + str + "\"");
                }
            }
        } catch (Exception e) {
            System.out.println(label + " FAILED: " + e.getMessage());
        }
    }

    private static byte[] zlibDecompress(byte[] input) throws Exception {
        Inflater inflater = new Inflater();
        inflater.setInput(input);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[1024];
        while (!inflater.finished()) {
            int n = inflater.inflate(tmp);
            if (n == 0) break;
            buf.write(tmp, 0, n);
        }
        inflater.end();
        return buf.toByteArray();
    }

    private static void printHex(byte[] data, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X ", data[i]));
            if ((i + 1) % 16 == 0) sb.append("\n");
        }
        System.out.println(sb.toString());
    }
}
