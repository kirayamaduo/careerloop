package com.group1.career.service;

/**
 * Wraps DashScope Paraformer (ASR) + CosyVoice (TTS) so the rest of the
 * codebase doesn't have to know about WebSocket SDK quirks.
 *
 * <p>Both backends authenticate with the same {@code aliyun.ai.api-key} that
 * Qwen uses; opening Paraformer / CosyVoice in the bailian console is a
 * one-click step on the same key.</p>
 */
public interface VoiceService {

    /**
     * Recognise a single short audio clip (<=10 MB / ~60 s) and return the
     * concatenated UTF-8 transcript. Throws if the clip is empty or the
     * service hard-fails; an empty transcript (silence) is returned as "".
     *
     * @param audioBytes raw audio file bytes
     * @param format     one of {@code mp3 / wav / aac / opus / pcm / amr / speex}
     */
    String transcribe(byte[] audioBytes, String format);

    /**
     * Synthesize a short utterance to speech, upload it to OSS, and return
     * the bare object key together with a coarse duration estimate (ms).
     * Callers are expected to ask {@link FileService#presignedUrl} for a
     * playable URL; we deliberately don't sign here so the same record can
     * be reused by report / replay views with their own TTL.
     */
    TtsResult synthesize(Long userId, String text);

    /**
     * @param objectKey    OSS object key of the uploaded mp3 (folder {@code tts/{userId}/})
     * @param durationMs   coarse duration estimate in milliseconds; UI should
     *                     prefer the {@code <audio>} loadedmetadata value once
     *                     it's available, this is just a fallback so the
     *                     digital-human mouth animation can start ticking
     *                     before the audio metadata loads.
     */
    record TtsResult(String objectKey, long durationMs) {}
}
