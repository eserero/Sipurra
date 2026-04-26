cmake_minimum_required(VERSION 3.22)
project(whisper_jni)
set(CMAKE_CXX_STANDARD 17)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# ggml + whisper static library
add_library(whisper STATIC
    ${WHISPER_DIR}/ggml/src/ggml.c
    ${WHISPER_DIR}/ggml/src/ggml-alloc.c
    ${WHISPER_DIR}/ggml/src/ggml-backend.cpp
    ${WHISPER_DIR}/ggml/src/ggml-backend-reg.cpp
    ${WHISPER_DIR}/ggml/src/ggml-quants.c
    ${WHISPER_DIR}/ggml/src/ggml-threading.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/ggml-cpu.c
    ${WHISPER_DIR}/ggml/src/ggml-cpu/ggml-cpu.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/ggml-cpu-aarch64.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/ggml-cpu-hbm.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/ggml-cpu-quants.c
    ${WHISPER_DIR}/ggml/src/ggml-cpu/ggml-cpu-traits.cpp
    ${WHISPER_DIR}/src/whisper.cpp
)

target_include_directories(whisper PUBLIC
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
    ${WHISPER_DIR}/ggml/src/ggml-cpu
    ${WHISPER_DIR}/src
)

target_compile_definitions(whisper PUBLIC GGML_USE_CPU)

# Always optimize whisper/ggml — it's a compute library, not a debug target
target_compile_options(whisper PRIVATE -O3)

# ARM64 only: dot-product + FP16 extensions and llamafile SGEMM kernels
# sgemm.cpp uses vld1q_f16 which is not available on armeabi-v7a
if (ANDROID AND ${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_sources(whisper PRIVATE ${WHISPER_DIR}/ggml/src/ggml-cpu/llamafile/sgemm.cpp)
    target_compile_definitions(whisper PRIVATE GGML_USE_LLAMAFILE)
    target_compile_options(whisper PRIVATE -march=armv8.2-a+dotprod+fp16)
endif ()

# Shared JNI library
add_library(whisper_jni SHARED whisper_jni.cpp)
target_link_libraries(whisper_jni whisper android log)
