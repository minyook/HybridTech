cmake_minimum_required(VERSION 3.22)
project(fieldguard_llama LANGUAGES C CXX)

include(FetchContent)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
# The Snapdragon plugins and the local llama.cpp core must share libggml-base.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
else()
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
endif()
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
# Keep the x86_64 library usable on Android emulators without AVX.
foreach(extension AVX AVX2 BMI2 FMA F16C)
    set(GGML_${extension} OFF CACHE BOOL "" FORCE)
endforeach()

set(llama_url https://codeload.github.com/ggml-org/llama.cpp/tar.gz/refs/tags/b12248)
set(llama_archive "${CMAKE_CURRENT_LIST_DIR}/../../../../tmp/llama.cpp-b12248.tar.gz")
if(EXISTS "${llama_archive}")
    set(llama_url "${llama_archive}")
endif()
FetchContent_Declare(llama_cpp
    URL "${llama_url}"
    URL_HASH SHA256=0ab19e448f17df9ddf45e7dcbcc9ff41a23afe6b35b53c3073ee306860ffc7f9
    TLS_VERIFY TRUE)
FetchContent_MakeAvailable(llama_cpp)

add_library(fieldguard_llama SHARED llama_bridge.cpp)
target_compile_features(fieldguard_llama PRIVATE cxx_std_17)
target_link_libraries(fieldguard_llama PRIVATE llama log dl)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(fieldguard_llama PRIVATE FIELDGUARD_SNAPDRAGON)
endif()
