package com.likelion.hackathon.global.agora;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.zip.Deflater;

/**
 * Agora AccessToken2 (v007) — mirrors official AccessToken2.java logic exactly.
 *
 * Token format: "007" + base64(zlib(packBytes(sig) + buf))
 *
 * buf  = packString(appId) + LE4(issueTs) + LE4(expireTs) + LE4(salt)
 *        + LE2(svcCount) + [LE2(type) + intMap(privileges) + packString(channel) + packString(uid)]
 *
 * signingKey = HMAC(LE4(salt), HMAC(LE4(issueTs), appCert.getBytes("UTF-8")))
 * sig        = HMAC(signingKey, buf)
 */
public class AgoraTokenUtil {

    private static final short SERVICE_RTC        = 1;
    private static final short PRIV_JOIN_CHANNEL  = 1;
    private static final short PRIV_PUBLISH_AUDIO = 2;
    private static final short PRIV_PUBLISH_VIDEO = 3;
    private static final short PRIV_PUBLISH_DATA  = 4;

    public static String buildTokenWithUid(String appId, String appCertificate,
                                           String channelName, int uid,
                                           int expireSeconds) throws Exception {
        appId          = appId.trim();
        appCertificate = appCertificate.trim();

        int salt    = new Random().nextInt(99999998) + 1;
        int issueTs = (int) (System.currentTimeMillis() / 1000);
        int expireTs = issueTs + expireSeconds;

        String uidStr = uid == 0 ? "" : String.valueOf(uid);

        TreeMap<Short, Integer> privileges = new TreeMap<>();
        privileges.put(PRIV_JOIN_CHANNEL,  expireTs);
        privileges.put(PRIV_PUBLISH_AUDIO, expireTs);
        privileges.put(PRIV_PUBLISH_VIDEO, expireTs);
        privileges.put(PRIV_PUBLISH_DATA,  expireTs);

        // buf = packString(appId) + LE4(issueTs) + LE4(expireTs) + LE4(salt)
        //       + LE2(1) + service_pack
        Buf buf = new Buf();
        buf.putString(appId);
        buf.putInt(issueTs);
        buf.putInt(expireTs);
        buf.putInt(salt);
        buf.putShort((short) 1);  // svcCount
        buf.putShort(SERVICE_RTC);
        buf.putIntMap(privileges);
        buf.putString(channelName);
        buf.putString(uidStr);

        byte[] bufBytes = buf.toBytes();

        // signingKey — two-step HMAC; cert used as UTF-8 string bytes (NOT hex-decoded)
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(le4(issueTs), "HmacSHA256"));
        byte[] step1 = mac.doFinal(appCertificate.getBytes("UTF-8"));
        mac.init(new SecretKeySpec(le4(salt), "HmacSHA256"));
        byte[] signingKey = mac.doFinal(step1);

        // sig = HMAC(signingKey, buf)
        mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
        byte[] signature = mac.doFinal(bufBytes);

        // content = packBytes(sig) + buf (raw)
        Buf content = new Buf();
        content.putBytes(signature);   // LE2(len) + sig_bytes
        content.raw(bufBytes);         // raw copy of buf

        return "007" + Base64.getEncoder().encodeToString(zlibCompress(content.toBytes()));
    }

    // ── tiny little-endian packer ──────────────────────────────────────────

    private static class Buf {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(256);

        void putShort(short v) {
            out.write(v & 0xFF);
            out.write((v >> 8) & 0xFF);
        }

        void putInt(int v) {
            out.write(v & 0xFF);
            out.write((v >> 8) & 0xFF);
            out.write((v >> 16) & 0xFF);
            out.write((v >> 24) & 0xFF);
        }

        void putBytes(byte[] v) {
            putShort((short) v.length);
            try { out.write(v); } catch (Exception ignored) {}
        }

        void putString(String s) {
            try { putBytes(s.getBytes("UTF-8")); } catch (Exception ignored) {}
        }

        void putIntMap(TreeMap<Short, Integer> map) {
            putShort((short) map.size());
            for (Map.Entry<Short, Integer> e : map.entrySet()) {
                putShort(e.getKey());
                putInt(e.getValue());
            }
        }

        void raw(byte[] v) {
            try { out.write(v); } catch (Exception ignored) {}
        }

        byte[] toBytes() { return out.toByteArray(); }
    }

    private static byte[] le4(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }

    private static byte[] zlibCompress(byte[] input) {
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream buf = new ByteArrayOutputStream(input.length);
        byte[] tmp = new byte[1024];
        while (!deflater.finished()) {
            int n = deflater.deflate(tmp);
            buf.write(tmp, 0, n);
        }
        deflater.end();
        return buf.toByteArray();
    }
}
