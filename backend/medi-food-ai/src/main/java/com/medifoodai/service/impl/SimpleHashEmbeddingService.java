package com.medifoodai.service.impl;

import com.medifoodai.service.EmbeddingService;

import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

public class SimpleHashEmbeddingService implements EmbeddingService {

    public static final int DIMS = 256;

    @Override
    public float[] embed(String text) {
        float[] vec = new float[DIMS];
        if (text == null || text.isEmpty()) return vec;

        StringTokenizer st = new StringTokenizer(text, " \t\n\r，。！？,.!?;；:：()（）[]【】\"'\"''/");
        while (st.hasMoreTokens()) {
            String token = st.nextToken().toLowerCase();
            int h = Math.abs(hash(token)) % DIMS;
            vec[h] += 1.0f;
        }

        float norm = 0f;
        for (float v : vec) norm += v * v;
        norm = (float) Math.sqrt(Math.max(norm, 1e-6));
        for (int i = 0; i < vec.length; i++) vec[i] /= norm;
        return vec;
    }

    private int hash(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int h = 0;
        for (byte b : bytes) {
            h = h * 31 + b;
        }
        return h;
    }

    @Override
    public int dimensions() {
        return DIMS;
    }

    @Override
    public String modelName() {
        return "hash-embedding";
    }
}