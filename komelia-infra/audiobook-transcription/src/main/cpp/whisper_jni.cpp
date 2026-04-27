#include <jni.h>
#include "whisper.h"
#include <android/log.h>
#include <string>
#include <unordered_map>
#include <vector>
#include <thread>

// Script-seeding prompts for non-Latin languages.
// Without these, whisper may output transliterated Latin text instead of native script.
static const std::unordered_map<std::string, std::string> SCRIPT_PROMPTS = {
    {"he", "שלום"},
    {"ar", "مرحبا"},
    {"ja", "こんにちは"},
    {"zh", "你好"},
    {"ko", "안녕하세요"},
    {"th", "สวัสดี"},
    {"ru", "Привет"},
    {"uk", "Привіт"},
    {"el", "Γεια σας"},
};

#define LOG_TAG "WhisperJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_snd_komelia_transcription_WhisperJni_loadModel(JNIEnv *env, jobject, jstring modelPathJ) {
    const char *path = env->GetStringUTFChars(modelPathJ, nullptr);
    LOGI("loadModel: %s", path);
    whisper_context_params cparams = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPathJ, path);
    if (ctx == nullptr) {
        LOGE("loadModel failed");
        return 0L;
    }
    return (jlong) ctx;
}

JNIEXPORT jobjectArray JNICALL
Java_snd_komelia_transcription_WhisperJni_transcribeChunk(
        JNIEnv *env, jobject,
        jlong ctxL, jfloatArray pcmJ, jlong offsetMs, jstring langJ) {

    auto *ctx = (whisper_context *) ctxL;

    jsize len = env->GetArrayLength(pcmJ);
    jfloat *pcm = env->GetFloatArrayElements(pcmJ, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.token_timestamps = true;
    params.max_len = 0;
    params.single_segment = false;
    params.no_context = false;
    params.n_threads = std::min(4, (int) std::thread::hardware_concurrency());
    // Limit encoder to actual audio length (whisper default pads to 30s / 1500 frames).
    // 16kHz audio: 50 mel frames per second (20ms per frame after 2x conv downsampling).
    int audio_frames = (int)((float)len / 16000.0f * 50.0f);
    params.audio_ctx = std::max(64, std::min(audio_frames, 1500));

    const char *lang = nullptr;
    if (langJ != nullptr) {
        lang = env->GetStringUTFChars(langJ, nullptr);
        params.language = lang;
        auto it = SCRIPT_PROMPTS.find(lang);
        if (it != SCRIPT_PROMPTS.end()) {
            params.initial_prompt = it->second.c_str();
        }
    }

    int rc = whisper_full(ctx, params, pcm, (int) len);
    env->ReleaseFloatArrayElements(pcmJ, pcm, JNI_ABORT);
    if (langJ != nullptr && lang != nullptr) {
        env->ReleaseStringUTFChars(langJ, lang);
    }

    if (rc != 0) {
        LOGE("whisper_full failed: %d", rc);
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exClass, ("whisper_full failed: " + std::to_string(rc)).c_str());
        return nullptr;
    }

    int n = whisper_full_n_segments(ctx);
    LOGI("transcribeChunk: %d segments", n);

    jclass resultClass = env->FindClass("snd/komelia/transcription/WhisperResult");
    jmethodID ctor = env->GetMethodID(resultClass, "<init>", "(JJLjava/lang/String;)V");
    jobjectArray result = env->NewObjectArray(n, resultClass, nullptr);

    for (int i = 0; i < n; i++) {
        // whisper timestamps are in centiseconds — multiply by 10 to get ms
        int64_t t0 = offsetMs + whisper_full_get_segment_t0(ctx, i) * 10;
        int64_t t1 = offsetMs + whisper_full_get_segment_t1(ctx, i) * 10;
        const char *text = whisper_full_get_segment_text(ctx, i);
        jstring textJ = env->NewStringUTF(text);
        jobject seg = env->NewObject(resultClass, ctor, (jlong) t0, (jlong) t1, textJ);
        env->SetObjectArrayElement(result, i, seg);
        env->DeleteLocalRef(textJ);
        env->DeleteLocalRef(seg);
    }

    return result;
}

JNIEXPORT void JNICALL
Java_snd_komelia_transcription_WhisperJni_freeContext(JNIEnv *, jobject, jlong ctxL) {
    if (ctxL != 0L) {
        whisper_free((whisper_context *) ctxL);
        LOGI("freeContext done");
    }
}

} // extern "C"
