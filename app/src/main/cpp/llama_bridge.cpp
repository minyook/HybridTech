#include "llama.h"

#include <jni.h>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdlib>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

#ifdef FIELDGUARD_SNAPDRAGON
#include <android/log.h>
#include <dlfcn.h>
#endif

namespace {
constexpr int prefill_batch = 128;

struct Engine {
    std::unique_ptr<llama_model, decltype(&llama_model_free)> model{nullptr, llama_model_free};
    std::unique_ptr<llama_context, decltype(&llama_free)> context{nullptr, llama_free};
    int context_tokens = 0;
};

std::string read_bytes(JNIEnv *env, jbyteArray bytes) {
    std::string result(env->GetArrayLength(bytes), '\0');
    env->GetByteArrayRegion(bytes, 0, result.size(), reinterpret_cast<jbyte *>(result.data()));
    return result;
}

void report_error(JNIEnv *env, const std::exception &error) {
    if (!env->ExceptionCheck()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    }
}

// A GGUF token piece can end in the middle of a Korean character or emoji.
size_t complete_utf8_prefix(const std::string &bytes) {
    size_t start = bytes.size();
    while (start > 0 && (static_cast<unsigned char>(bytes[start - 1]) & 0xc0) == 0x80) {
        --start;
    }
    if (start == 0) return bytes.size();
    const auto lead = static_cast<unsigned char>(bytes[start - 1]);
    const size_t width = lead >= 0xf0 ? 4 : lead >= 0xe0 ? 3 : lead >= 0xc2 ? 2 : 1;
    return bytes.size() - start + 1 < width ? start - 1 : bytes.size();
}

bool send_piece(JNIEnv *env, jobject stream, jmethodID accept, std::string &pending) {
    const auto count = static_cast<jsize>(complete_utf8_prefix(pending));
    jbyteArray bytes = nullptr;
    if (count > 0) {
        bytes = env->NewByteArray(count);
        if (!bytes) return false;
        env->SetByteArrayRegion(bytes, 0, count, reinterpret_cast<const jbyte *>(pending.data()));
        pending.erase(0, count);
    }
    const bool keep_going = !env->ExceptionCheck() && env->CallBooleanMethod(stream, accept, bytes);
    if (bytes) env->DeleteLocalRef(bytes);
    return keep_going && !env->ExceptionCheck();
}

using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;

Sampler create_sampler(const llama_vocab *vocab, int numbered_items) {
    if (numbered_items < 2 || numbered_items > 12) {
        return Sampler(llama_sampler_init_greedy(), llama_sampler_free);
    }
    std::string grammar = "root ::=";
    for (int item = 1; item <= numbered_items; ++item) {
        grammar += item == 1 ? " \"" : " \"\\n";
        grammar += std::to_string(item) + ". \" sentence";
    }
    grammar += R"( "\n"?
sentence ::= [^ \t\r\n.!?。！？] ([^\r\n.!?。！？] | "." [0-9])* [.!?。！？]
)";
    Sampler format(llama_sampler_init_grammar(vocab, grammar.c_str(), "root"), llama_sampler_free);
    if (!format) throw std::runtime_error("Could not prepare the numbered answer format.");
    Sampler sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
    llama_sampler_chain_add(sampler.get(), format.release());
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
    return sampler;
}

llama_token sample_next(llama_sampler *sampler, llama_context *context) {
    auto *grammar = llama_sampler_chain_get(sampler, 0);
    if (grammar) {
        // Validate the most likely token first. Mask the full vocabulary only if it violates the format.
        auto *greedy = llama_sampler_chain_get(sampler, 1);
        llama_token_data token{llama_sampler_sample(greedy, context, -1), 0.0f, 0.0f};
        llama_token_data_array candidate{&token, 1, -1, true};
        llama_sampler_apply(grammar, &candidate);
        if (std::isfinite(token.logit)) {
            llama_sampler_accept(grammar, token.id);
            return token.id;
        }
    }
    return llama_sampler_sample(sampler, context, -1);
}

void initialize_llama() {
    static std::once_flag backend;
    std::call_once(backend, [] {
#ifdef FIELDGUARD_SNAPDRAGON
        llama_log_set([](ggml_log_level level, const char *message, void *) {
            if (level >= GGML_LOG_LEVEL_INFO) {
                __android_log_write(ANDROID_LOG_INFO, "FieldGuardLlama", message);
            }
        }, nullptr);
#endif
        llama_backend_init();
    });
}

#ifdef FIELDGUARD_SNAPDRAGON
std::mutex model_open_mutex;

ggml_backend_dev_t accelerator_device(const std::string &library_dir, const std::string &dsp_dir, bool npu) {
    // Registered devices remain valid for the process lifetime, so retain the library handles.
    static std::once_flag registration[2];
    static ggml_backend_reg_t backends[2] = {};
    const int index = npu ? 0 : 1;
    std::call_once(registration[index], [&] {
        if (npu && setenv("GGML_HEXAGON_LIBHTP_DIR", dsp_dir.c_str(), 1) != 0) {
            throw std::runtime_error("Could not configure the Hexagon DSP library directory.");
        }
        const auto path = library_dir + (npu ? "/libggml-hexagon.so" : "/libggml-opencl.so");
        auto *library = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!library) {
            const auto *error = dlerror();
            throw std::runtime_error(error ? error : "Could not load the accelerator library.");
        }
        using Register = ggml_backend_reg_t (*)();
        const auto register_backend = reinterpret_cast<Register>(
            dlsym(library, npu ? "ggml_backend_hexagon_reg" : "ggml_backend_opencl_reg"));
        if (!register_backend) {
            dlclose(library);
            throw std::runtime_error("Accelerator library is missing its registration entry point.");
        }
        backends[index] = register_backend();
        if (backends[index]) ggml_backend_register(backends[index]);
    });
    const auto backend = backends[index];
    if (!backend || ggml_backend_reg_dev_count(backend) == 0) {
        throw std::runtime_error(npu ? "No Hexagon NPU available." : "No OpenCL GPU available.");
    }
    return ggml_backend_reg_dev_get(backend, 0);
}
#endif

jlong open_engine(JNIEnv *env, jbyteArray path, jint context_tokens, jint threads, ggml_backend_dev_t accelerator) {
    try {
        initialize_llama();
        const auto model_path = read_bytes(env, path);
        if (env->ExceptionCheck()) return 0;
        auto engine = std::make_unique<Engine>();
        ggml_backend_dev_t devices[] = {accelerator, nullptr};
        auto model_params = llama_model_default_params();
        model_params.devices = devices;
        model_params.n_gpu_layers = accelerator ? -1 : 0;
        model_params.split_mode = LLAMA_SPLIT_MODE_NONE;
        engine->model.reset(llama_model_load_from_file(model_path.c_str(), model_params));
        if (!engine->model) throw std::runtime_error("Failed to load the EXAONE GGUF file.");

        auto params = llama_context_default_params();
        params.n_ctx = context_tokens;
        params.n_batch = prefill_batch;
        params.n_ubatch = prefill_batch;
        params.n_threads = threads;
        params.n_threads_batch = threads;
        params.offload_kqv = accelerator != nullptr;
        params.op_offload = accelerator != nullptr;
        engine->context.reset(llama_init_from_model(engine->model.get(), params));
        if (!engine->context) throw std::runtime_error("Failed to allocate the EXAONE context.");
        engine->context_tokens = std::min(context_tokens, static_cast<jint>(llama_n_ctx(engine->context.get())));
        return reinterpret_cast<jlong>(engine.release());
    } catch (const std::exception &error) {
        report_error(env, error);
        return 0;
    }
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_minyook_sllm2_model_LlamaNative_open(
    JNIEnv *env, jobject, jbyteArray path, jint context_tokens, jint threads
) {
#ifdef FIELDGUARD_SNAPDRAGON
    std::lock_guard<std::mutex> lock(model_open_mutex);
#endif
    return open_engine(env, path, context_tokens, threads, nullptr);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_minyook_sllm2_model_LlamaNative_openAccelerator(
    JNIEnv *env, jobject, jbyteArray path, jint context_tokens, jint threads,
    jbyteArray library_dir, jbyteArray dsp_dir, jboolean npu
) {
    try {
#ifdef FIELDGUARD_SNAPDRAGON
        std::lock_guard<std::mutex> lock(model_open_mutex);
        initialize_llama();
        const auto library_path = read_bytes(env, library_dir);
        if (env->ExceptionCheck()) return 0;
        const auto dsp_path = read_bytes(env, dsp_dir);
        if (env->ExceptionCheck()) return 0;
        const auto device = accelerator_device(library_path, dsp_path, npu);
        return open_engine(env, path, context_tokens, threads, device);
#else
        throw std::runtime_error("This ABI uses the CPU backend.");
#endif
    } catch (const std::exception &error) {
        report_error(env, error);
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_minyook_sllm2_model_LlamaNative_close(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<Engine *>(handle);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_minyook_sllm2_model_LlamaNative_generate(
    JNIEnv *env, jobject, jlong handle, jbyteArray input, jint output_limit, jobject stream, jint numbered_items
) {
    try {
        auto &engine = *reinterpret_cast<Engine *>(handle);
        auto *context = engine.context.get();
        auto *vocab = llama_model_get_vocab(engine.model.get());
        const auto prompt = read_bytes(env, input);
        const auto accept = env->GetMethodID(env->GetObjectClass(stream), "accept", "([B)Z");
        if (!accept || env->ExceptionCheck()) return nullptr;

        std::vector<llama_token> tokens(engine.context_tokens);
        const int prompt_tokens = llama_tokenize(
            vocab, prompt.data(), prompt.size(), tokens.data(), tokens.size(), true, true);
        if (prompt_tokens <= 0 || prompt_tokens >= engine.context_tokens || output_limit <= 0) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                "EXAONE prompt exceeds the available context.");
            return nullptr;
        }
        const int limit = std::min(output_limit, engine.context_tokens - prompt_tokens);
        llama_memory_clear(llama_get_memory(context), false);
        for (int offset = 0; offset < prompt_tokens; offset += prefill_batch) {
            if (!env->CallBooleanMethod(stream, accept, nullptr) || env->ExceptionCheck()) return nullptr;
            const int count = std::min(prefill_batch, prompt_tokens - offset);
            if (llama_decode(context, llama_batch_get_one(tokens.data() + offset, count)) != 0) {
                throw std::runtime_error("EXAONE failed to process the prompt.");
            }
        }

        auto sampler = create_sampler(vocab, numbered_items);
        const auto decode_start = std::chrono::steady_clock::now();
        int generated = 0;
        std::string pending;
        std::vector<char> piece(256);
        while (generated < limit) {
            if (!env->CallBooleanMethod(stream, accept, nullptr) || env->ExceptionCheck()) return nullptr;
            auto token = sample_next(sampler.get(), context);
            if (llama_vocab_is_eog(vocab, token)) break;
            int length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            if (length < 0) {
                piece.resize(-length);
                length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            }
            if (length < 0) throw std::runtime_error("EXAONE failed to decode an output token.");
            pending.append(piece.data(), length);
            ++generated;
            if (!send_piece(env, stream, accept, pending)) return nullptr;
            if (generated < limit && llama_decode(context, llama_batch_get_one(&token, 1)) != 0) {
                throw std::runtime_error("EXAONE failed to generate the next token.");
            }
        }
        const auto elapsed = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now() - decode_start).count();
        const jlong stats[] = {prompt_tokens, generated, elapsed, generated == limit, engine.context_tokens};
        auto result = env->NewLongArray(5);
        if (result) env->SetLongArrayRegion(result, 0, 5, stats);
        return result;
    } catch (const std::exception &error) {
        report_error(env, error);
        return nullptr;
    }
}
