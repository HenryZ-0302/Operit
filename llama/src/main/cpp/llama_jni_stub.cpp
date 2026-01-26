#include <jni.h>

#include <android/log.h>

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#if defined(OPERIT_HAS_LLAMA_CPP) && OPERIT_HAS_LLAMA_CPP
#include "llama.h"
#include <cstdlib>
#include <ctime>
#include <algorithm>
#include <memory>
#include <sstream>
#endif

#define TAG "LlamaNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::string jstringToString(JNIEnv * env, jstring jstr) {
    if (jstr == nullptr) return "";
    const char * cstr = env->GetStringUTFChars(jstr, nullptr);
    std::string out(cstr);
    env->ReleaseStringUTFChars(jstr, cstr);
    return out;
}

#if defined(OPERIT_HAS_LLAMA_CPP) && OPERIT_HAS_LLAMA_CPP
static llama_sampler * createSamplerChain(
        float temperature,
        float topP,
        int32_t topK,
        int32_t penaltyLastN,
        float repeatPenalty,
        float frequencyPenalty,
        float presencePenalty,
        uint32_t seed
) {
    if (topP < 0.0f) topP = 0.0f;
    if (topP > 1.0f) topP = 1.0f;
    if (topK < 0) topK = 0;
    if (penaltyLastN < -1) penaltyLastN = -1;
    if (repeatPenalty < 0.0f) repeatPenalty = 0.0f;

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler * chain = llama_sampler_chain_init(sparams);
    if (!chain) return nullptr;

    // order follows llama.cpp common sampling: penalties -> top-k -> top-p -> temp -> dist
    llama_sampler_chain_add(chain, llama_sampler_init_penalties(
            penaltyLastN,
            repeatPenalty,
            frequencyPenalty,
            presencePenalty
    ));

    llama_sampler_chain_add(chain, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(seed));

    return chain;
}
#endif

static jstring stringToJstring(JNIEnv * env, const std::string & str) {
    return env->NewStringUTF(str.c_str());
}

static jstring bytesUtf8ToJstring(JNIEnv * env, const std::string & bytes) {
    std::u16string out;
    out.reserve(bytes.size());

    const unsigned char * s = reinterpret_cast<const unsigned char *>(bytes.data());
    size_t i = 0;
    while (i < bytes.size()) {
        uint32_t cp = 0;
        const unsigned char c0 = s[i];

        if (c0 < 0x80) {
            cp = c0;
            i += 1;
        } else if ((c0 & 0xE0) == 0xC0 && i + 1 < bytes.size()) {
            const unsigned char c1 = s[i + 1];
            if ((c1 & 0xC0) != 0x80) {
                cp = 0xFFFD;
                i += 1;
            } else {
                cp = ((c0 & 0x1F) << 6) | (c1 & 0x3F);
                if (cp < 0x80) cp = 0xFFFD;
                i += 2;
            }
        } else if ((c0 & 0xF0) == 0xE0 && i + 2 < bytes.size()) {
            const unsigned char c1 = s[i + 1];
            const unsigned char c2 = s[i + 2];
            if (((c1 & 0xC0) != 0x80) || ((c2 & 0xC0) != 0x80)) {
                cp = 0xFFFD;
                i += 1;
            } else {
                cp = ((c0 & 0x0F) << 12) | ((c1 & 0x3F) << 6) | (c2 & 0x3F);
                if (cp < 0x800) cp = 0xFFFD;
                i += 3;
            }
        } else if ((c0 & 0xF8) == 0xF0 && i + 3 < bytes.size()) {
            const unsigned char c1 = s[i + 1];
            const unsigned char c2 = s[i + 2];
            const unsigned char c3 = s[i + 3];
            if (((c1 & 0xC0) != 0x80) || ((c2 & 0xC0) != 0x80) || ((c3 & 0xC0) != 0x80)) {
                cp = 0xFFFD;
                i += 1;
            } else {
                cp = ((c0 & 0x07) << 18) | ((c1 & 0x3F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F);
                if (cp < 0x10000 || cp > 0x10FFFF) cp = 0xFFFD;
                i += 4;
            }
        } else {
            cp = 0xFFFD;
            i += 1;
        }

        if (cp <= 0xFFFF) {
            out.push_back(static_cast<char16_t>(cp));
        } else {
            cp -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
        }
    }

    return env->NewString(reinterpret_cast<const jchar *>(out.data()), static_cast<jsize>(out.size()));
}

#if !(defined(OPERIT_HAS_LLAMA_CPP) && OPERIT_HAS_LLAMA_CPP)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeIsAvailable(JNIEnv * env, jclass clazz) {
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeGetUnavailableReason(JNIEnv * env, jclass clazz) {
    const char * msg = "llama.cpp native backend is not built. Ensure llama/third_party/llama.cpp submodule exists and CMake links target 'llama'.";
    return env->NewStringUTF(msg);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeCreateSession(JNIEnv * env, jclass clazz, jstring pathModel, jint nThreads, jint nCtx) {
    (void) env;
    (void) clazz;
    (void) pathModel;
    (void) nThreads;
    (void) nCtx;
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeReleaseSession(JNIEnv * env, jclass clazz, jlong sessionPtr) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeCancel(JNIEnv * env, jclass clazz, jlong sessionPtr) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeCountTokens(JNIEnv * env, jclass clazz, jlong sessionPtr, jstring text) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
    (void) text;
    return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeSetSamplingParams(
        JNIEnv * env,
        jclass clazz,
        jlong sessionPtr,
        jfloat temperature,
        jfloat topP,
        jint topK,
        jfloat repetitionPenalty,
        jfloat frequencyPenalty,
        jfloat presencePenalty,
        jint penaltyLastN
) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
    (void) temperature;
    (void) topP;
    (void) topK;
    (void) repetitionPenalty;
    (void) frequencyPenalty;
    (void) presencePenalty;
    (void) penaltyLastN;
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeApplyChatTemplate(
        JNIEnv * env,
        jclass clazz,
        jlong sessionPtr,
        jobjectArray roles,
        jobjectArray contents,
        jboolean addAssistant
) {
    (void) clazz;
    (void) sessionPtr;
    (void) roles;
    (void) contents;
    (void) addAssistant;
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeGenerateStream(JNIEnv * env, jclass clazz, jlong sessionPtr, jstring prompt, jint maxTokens, jobject callback) {
    (void) env;
    (void) clazz;
    (void) sessionPtr;
    (void) prompt;
    (void) maxTokens;
    (void) callback;
    return JNI_FALSE;
}

#else

namespace {

struct LlamaSessionNative {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    llama_sampler * sampler = nullptr;
    std::atomic_bool cancel{false};
};

static std::once_flag gBackendInitOnce;

static void ensureBackendInit() {
    std::call_once(gBackendInitOnce, []() {
        llama_backend_init();
        std::srand(static_cast<unsigned int>(std::time(nullptr)));
        LOGI("llama_backend_init done");
    });
}

static bool abortCallback(void * user_data) {
    auto * session = reinterpret_cast<LlamaSessionNative *>(user_data);
    return session != nullptr && session->cancel.load();
}

static int32_t tokenizeText(const llama_vocab * vocab, const std::string & text, bool addSpecial) {
    if (vocab == nullptr) return 0;
    int32_t capacity = static_cast<int32_t>(text.size()) + 8;
    std::vector<llama_token> tokens;
    tokens.resize(std::max(16, capacity));

    int32_t n = llama_tokenize(
        vocab,
        text.c_str(),
        static_cast<int32_t>(text.size()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        addSpecial,
        true
    );

    if (n < 0) {
        tokens.resize(static_cast<size_t>(-n));
        n = llama_tokenize(
            vocab,
            text.c_str(),
            static_cast<int32_t>(text.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            addSpecial,
            true
        );
    }

    return std::max<int32_t>(0, n);
}

static bool tokenToPiece(const llama_vocab * vocab, llama_token token, std::string & out) {
    if (vocab == nullptr) return false;
    std::vector<char> buf;
    buf.resize(256);

    int32_t n = llama_token_to_piece(vocab, token, buf.data(), static_cast<int32_t>(buf.size()), 0, true);
    if (n < 0) {
        buf.resize(static_cast<size_t>(-n));
        n = llama_token_to_piece(vocab, token, buf.data(), static_cast<int32_t>(buf.size()), 0, true);
    }
    if (n <= 0) return false;
    out.assign(buf.data(), buf.data() + n);
    return true;
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeIsAvailable(JNIEnv * env, jclass clazz) {
    (void) env;
    (void) clazz;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeGetUnavailableReason(JNIEnv * env, jclass clazz) {
    (void) clazz;
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeCreateSession(JNIEnv * env, jclass clazz, jstring pathModel, jint nThreads, jint nCtx) {
    (void) clazz;
    ensureBackendInit();

    const std::string modelPath = jstringToString(env, pathModel);
    LOGI("Creating llama session. model=%s threads=%d n_ctx=%d", modelPath.c_str(), (int) nThreads, (int) nCtx);

    auto * session = new (std::nothrow) LlamaSessionNative();
    if (!session) {
        LOGE("Failed to allocate session");
        return 0;
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    mparams.use_mmap = true;
    mparams.use_mlock = false;

    session->model = llama_model_load_from_file(modelPath.c_str(), mparams);
    if (!session->model) {
        LOGE("Failed to load model from file");
        delete session;
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx > 0 ? static_cast<uint32_t>(nCtx) : 0;
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    cparams.abort_callback = abortCallback;
    cparams.abort_callback_data = session;

    session->ctx = llama_init_from_model(session->model, cparams);
    if (!session->ctx) {
        LOGE("Failed to create context");
        llama_model_free(session->model);
        delete session;
        return 0;
    }

    llama_set_n_threads(session->ctx, nThreads, nThreads);

    llama_sampler * chain = createSamplerChain(
            1.0f,   // temperature
            1.0f,   // top_p
            0,      // top_k
            64,     // penalty_last_n
            1.0f,   // repetition penalty
            0.0f,   // frequency penalty
            0.0f,   // presence penalty
            static_cast<uint32_t>(std::rand())
    );
    if (!chain) {
        LOGE("Failed to create sampler chain");
        llama_free(session->ctx);
        llama_model_free(session->model);
        delete session;
        return 0;
    }
    session->sampler = chain;
    session->cancel.store(false);

    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeReleaseSession(JNIEnv * env, jclass clazz, jlong sessionPtr) {
    (void) env;
    (void) clazz;

    if (sessionPtr == 0) return;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);

    if (session->sampler) {
        llama_sampler_free(session->sampler);
        session->sampler = nullptr;
    }

    if (session->ctx) {
        llama_free(session->ctx);
        session->ctx = nullptr;
    }

    if (session->model) {
        llama_model_free(session->model);
        session->model = nullptr;
    }

    delete session;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeCancel(JNIEnv * env, jclass clazz, jlong sessionPtr) {
    (void) env;
    (void) clazz;
    if (sessionPtr == 0) return;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    session->cancel.store(true);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeCountTokens(JNIEnv * env, jclass clazz, jlong sessionPtr, jstring text) {
    (void) clazz;
    if (sessionPtr == 0) return 0;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    if (!session->model) return 0;
    const llama_vocab * vocab = llama_model_get_vocab(session->model);
    const std::string input = jstringToString(env, text);
    return static_cast<jint>(tokenizeText(vocab, input, true));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeSetSamplingParams(
        JNIEnv * env,
        jclass clazz,
        jlong sessionPtr,
        jfloat temperature,
        jfloat topP,
        jint topK,
        jfloat repetitionPenalty,
        jfloat frequencyPenalty,
        jfloat presencePenalty,
        jint penaltyLastN
) {
    (void) env;
    (void) clazz;

    if (sessionPtr == 0) return JNI_FALSE;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    if (!session->ctx || !session->model) return JNI_FALSE;

    llama_sampler * next = createSamplerChain(
            (float) temperature,
            (float) topP,
            (int32_t) topK,
            (int32_t) penaltyLastN,
            (float) repetitionPenalty,
            (float) frequencyPenalty,
            (float) presencePenalty,
            static_cast<uint32_t>(std::rand())
    );
    if (!next) return JNI_FALSE;

    if (session->sampler) {
        llama_sampler_free(session->sampler);
        session->sampler = nullptr;
    }
    session->sampler = next;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeApplyChatTemplate(
    JNIEnv * env,
    jclass clazz,
    jlong sessionPtr,
    jobjectArray roles,
    jobjectArray contents,
    jboolean addAssistant
) {
    (void) clazz;

    if (sessionPtr == 0 || roles == nullptr || contents == nullptr) return nullptr;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    if (!session->model) return nullptr;

    const jsize nRoles = env->GetArrayLength(roles);
    const jsize nContents = env->GetArrayLength(contents);
    if (nRoles <= 0 || nContents <= 0 || nRoles != nContents) return nullptr;

    std::vector<std::string> roleBuf;
    std::vector<std::string> contentBuf;
    roleBuf.reserve(static_cast<size_t>(nRoles));
    contentBuf.reserve(static_cast<size_t>(nRoles));

    for (jsize i = 0; i < nRoles; i++) {
        auto jrole = (jstring) env->GetObjectArrayElement(roles, i);
        auto jcontent = (jstring) env->GetObjectArrayElement(contents, i);
        roleBuf.push_back(jstringToString(env, jrole));
        contentBuf.push_back(jstringToString(env, jcontent));
        if (jrole) env->DeleteLocalRef(jrole);
        if (jcontent) env->DeleteLocalRef(jcontent);
    }

    std::vector<llama_chat_message> msgs;
    msgs.reserve(static_cast<size_t>(nRoles));
    for (jsize i = 0; i < nRoles; i++) {
        llama_chat_message m;
        m.role = roleBuf[static_cast<size_t>(i)].c_str();
        m.content = contentBuf[static_cast<size_t>(i)].c_str();
        msgs.push_back(m);
    }

    const char * tmpl = llama_model_chat_template(session->model, nullptr);
    if (!tmpl) return nullptr;

    int32_t need = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), addAssistant == JNI_TRUE, nullptr, 0);
    if (need < 0) return nullptr;

    std::vector<char> buf;
    buf.resize(static_cast<size_t>(need));

    int32_t res = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), addAssistant == JNI_TRUE, buf.data(), static_cast<int32_t>(buf.size()));
    if (res < 0) return nullptr;
    if (res > (int32_t) buf.size()) {
        buf.resize(static_cast<size_t>(res));
        res = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), addAssistant == JNI_TRUE, buf.data(), static_cast<int32_t>(buf.size()));
        if (res < 0) return nullptr;
    }

    std::string out(buf.data(), buf.data() + res);
    return bytesUtf8ToJstring(env, out);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_llama_LlamaNative_nativeGenerateStream(JNIEnv * env, jclass clazz, jlong sessionPtr, jstring prompt, jint maxTokens, jobject callback) {
    (void) clazz;

    if (sessionPtr == 0 || callback == nullptr) return JNI_FALSE;
    auto * session = reinterpret_cast<LlamaSessionNative *>(sessionPtr);
    if (!session->model || !session->ctx || !session->sampler) return JNI_FALSE;

    session->cancel.store(false);

    // reset KV + sampler for a clean generation per request
    if (session->ctx) {
        llama_memory_t mem = llama_get_memory(session->ctx);
        if (mem) {
            llama_memory_clear(mem, true);
        }
    }
    if (session->sampler) {
        llama_sampler_reset(session->sampler);
    }

    const std::string promptStr = jstringToString(env, prompt);
    const llama_vocab * vocab = llama_model_get_vocab(session->model);

    // Resolve callback method
    jclass cbCls = env->GetObjectClass(callback);
    if (!cbCls) return JNI_FALSE;
    jmethodID midOnToken = env->GetMethodID(cbCls, "onToken", "(Ljava/lang/String;)Z");
    if (!midOnToken) return JNI_FALSE;

    // Tokenize prompt
    int32_t capacity = static_cast<int32_t>(promptStr.size()) + 8;
    std::vector<llama_token> promptTokens;
    promptTokens.resize(std::max(16, capacity));
    int32_t nPrompt = llama_tokenize(
        vocab,
        promptStr.c_str(),
        static_cast<int32_t>(promptStr.size()),
        promptTokens.data(),
        static_cast<int32_t>(promptTokens.size()),
        true,
        true
    );
    if (nPrompt < 0) {
        promptTokens.resize(static_cast<size_t>(-nPrompt));
        nPrompt = llama_tokenize(
            vocab,
            promptStr.c_str(),
            static_cast<int32_t>(promptStr.size()),
            promptTokens.data(),
            static_cast<int32_t>(promptTokens.size()),
            true,
            true
        );
    }
    if (nPrompt <= 0) {
        LOGE("Tokenize prompt failed");
        return JNI_FALSE;
    }
    promptTokens.resize(static_cast<size_t>(nPrompt));

    // Avoid prompts that end with EOG/EOS tokens (some vocabs add EOS automatically when add_special=true)
    while (!promptTokens.empty() && llama_vocab_is_eog(vocab, promptTokens.back())) {
        promptTokens.pop_back();
    }
    if (promptTokens.empty()) {
        LOGE("Prompt tokenization resulted in only EOG/EOS tokens");
        return JNI_FALSE;
    }

    int32_t n_past = 0;

    // Evaluate prompt
    llama_batch batch = llama_batch_get_one(promptTokens.data(), static_cast<int32_t>(promptTokens.size()));
    // llama_batch_get_one() may leave batch.logits == nullptr (default behavior is: only last token outputs logits)
    // so never write to it unless it's allocated.
    if (batch.logits != nullptr && batch.n_tokens > 0) {
        batch.logits[batch.n_tokens - 1] = 1;
    }

    if (llama_model_has_encoder(session->model)) {
        if (llama_encode(session->ctx, batch) != 0) {
            LOGE("llama_encode failed");
            return JNI_FALSE;
        }

        llama_token decoder_start_token_id = llama_model_decoder_start_token(session->model);
        if (decoder_start_token_id == -1) {
            decoder_start_token_id = llama_vocab_bos(vocab);
        }

        batch = llama_batch_get_one(&decoder_start_token_id, 1);
        if (batch.logits != nullptr) {
            batch.logits[0] = 1;
        }
    }

    int32_t ret = llama_decode(session->ctx, batch);
    if (ret != 0 && ret != 1) {
        // 1 is a warning; 2 is aborted
        if (ret == 2) {
            LOGI("decode aborted (prompt)");
        } else {
            LOGE("llama_decode failed for prompt ret=%d", ret);
        }
        return JNI_FALSE;
    }

    // n_past for subsequent single-token decoding
    n_past = llama_model_has_encoder(session->model)
        ? 1
        : static_cast<int32_t>(promptTokens.size());

    // Generation loop
    int maxNew = maxTokens <= 0 ? 256 : static_cast<int>(maxTokens);

    std::vector<llama_token> generatedTokens;
    generatedTokens.reserve(static_cast<size_t>(maxNew));
    std::string prevDecoded;
    std::vector<char> detokBuf;

    for (int i = 0; i < maxNew; i++) {
        if (session->cancel.load()) {
            LOGI("generation cancelled");
            break;
        }

        const llama_token newToken = llama_sampler_sample(session->sampler, session->ctx, -1);
        llama_sampler_accept(session->sampler, newToken);

        if (i == 0) {
            LOGI("first sampled token=%d eog=%d", (int) newToken, (int) llama_vocab_is_eog(vocab, newToken));
        }

        if (llama_vocab_is_eog(vocab, newToken)) {
            break;
        }

        // Detokenize the generated token sequence to produce valid UTF-8 text.
        // Token pieces may split multi-byte sequences; emitting per-token pieces often results in mojibake.
        generatedTokens.push_back(newToken);

        int32_t detokCap = std::max<int32_t>(64, static_cast<int32_t>(generatedTokens.size() * 8 + 32));
        detokBuf.resize(static_cast<size_t>(detokCap));

        int32_t nDetok = llama_detokenize(
            vocab,
            generatedTokens.data(),
            static_cast<int32_t>(generatedTokens.size()),
            detokBuf.data(),
            static_cast<int32_t>(detokBuf.size()),
            true,
            false
        );
        if (nDetok < 0) {
            detokBuf.resize(static_cast<size_t>(-nDetok));
            nDetok = llama_detokenize(
                vocab,
                generatedTokens.data(),
                static_cast<int32_t>(generatedTokens.size()),
                detokBuf.data(),
                static_cast<int32_t>(detokBuf.size()),
                true,
                false
            );
        }

        std::string decodedNow;
        if (nDetok > 0) {
            decodedNow.assign(detokBuf.data(), detokBuf.data() + nDetok);
        }

        std::string delta;
        if (!prevDecoded.empty() && decodedNow.rfind(prevDecoded, 0) == 0) {
            delta = decodedNow.substr(prevDecoded.size());
        } else {
            delta = decodedNow;
        }
        prevDecoded = decodedNow;

        if (!delta.empty()) {
            jstring jdelta = bytesUtf8ToJstring(env, delta);
            if (jdelta == nullptr || env->ExceptionCheck()) {
                env->ExceptionClear();
            } else {
                const jboolean keepGoing = env->CallBooleanMethod(callback, midOnToken, jdelta);
                env->DeleteLocalRef(jdelta);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    LOGE("Java callback threw exception; stopping generation");
                    break;
                }
                if (!keepGoing) {
                    break;
                }
            }
        }

        llama_token next = newToken;
        batch = llama_batch_get_one(&next, 1);
        if (batch.pos != nullptr) {
            batch.pos[0] = n_past;
        }
        if (batch.logits != nullptr) {
            batch.logits[0] = 1;
        }
        ret = llama_decode(session->ctx, batch);
        if (ret != 0 && ret != 1) {
            if (ret == 2) {
                LOGI("decode aborted");
                break;
            }
            LOGE("llama_decode failed ret=%d", ret);
            return JNI_FALSE;
        }

        n_past += 1;
    }

    return JNI_TRUE;
}

#endif
