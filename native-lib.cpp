#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include "llama.cpp/include/llama.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "HoroAI", __VA_ARGS__)
static std::mutex g_mutex;

extern "C" JNIEXPORT jstring JNICALL
Java_ai_horoai_app_LlamaBridge_generateNative(JNIEnv* env, jclass,
        jstring jmodel, jstring jprompt, jint max_tokens, jint threads) {
    const char* model_path = env->GetStringUTFChars(jmodel, nullptr);
    const char* prompt_chars = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_chars ? prompt_chars : "");
    std::string result;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        llama_backend_init();
        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0;
        llama_model* model = llama_model_load_from_file(model_path, model_params);
        if (!model) {
            LOGE("Failed to load GGUF model: %s", model_path);
            result = "Не удалось загрузить локальную модель. Проверь файл GGUF и свободную память.";
        } else {
            llama_context_params ctx_params = llama_context_default_params();
            ctx_params.n_ctx = 2048;
            ctx_params.n_batch = 256;
            ctx_params.n_threads = threads > 0 ? threads : 4;
            ctx_params.n_threads_batch = ctx_params.n_threads;
            llama_context* ctx = llama_init_from_model(model, ctx_params);
            if (!ctx) {
                result = "Не удалось создать контекст нейросети. Закрой другие приложения и попробуй снова.";
            } else {
                const llama_vocab* vocab = llama_model_get_vocab(model);
                std::vector<llama_token> tokens(prompt.size() + 64);
                int n = llama_tokenize(vocab, prompt.c_str(), (int) prompt.size(), tokens.data(), (int) tokens.size(), true, true);
                if (n < 0) {
                    tokens.resize((size_t)(-n));
                    n = llama_tokenize(vocab, prompt.c_str(), (int) prompt.size(), tokens.data(), (int) tokens.size(), true, true);
                }
                if (n <= 0 || n > (int)ctx_params.n_ctx - 8) {
                    result = "Сообщение слишком длинное для текущей локальной модели. Начни новый короткий диалог.";
                } else {
                    tokens.resize((size_t)n);
                    llama_batch batch = llama_batch_get_one(tokens.data(), n);
                    int decoded = llama_decode(ctx, batch);
                    if (decoded != 0) {
                        result = "Ошибка обработки запроса локальной моделью.";
                    } else {
                        llama_sampler* sampler = llama_sampler_init_greedy();
                        const int limit = max_tokens > 0 ? max_tokens : 384;
                        for (int i = 0; i < limit; ++i) {
                            llama_token token = llama_sampler_sample(sampler, ctx, -1);
                            if (llama_vocab_is_eog(vocab, token)) break;
                            char piece[1024];
                            int len = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
                            if (len > 0) result.append(piece, (size_t)len);
                            batch = llama_batch_get_one(&token, 1);
                            if (llama_decode(ctx, batch) != 0) break;
                        }
                        llama_sampler_free(sampler);
                    }
                }
                llama_free(ctx);
            }
            llama_model_free(model);
        }
        llama_backend_free();
    }
    env->ReleaseStringUTFChars(jmodel, model_path);
    env->ReleaseStringUTFChars(jprompt, prompt_chars);
    return env->NewStringUTF(result.c_str());
}
