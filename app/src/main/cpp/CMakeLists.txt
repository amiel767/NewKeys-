cmake_minimum_required(VERSION 3.22.1)

project("dawstudio")

# Maximum performance flags: -O3, vectorization, loop unrolling
add_compile_options(-O3 -funroll-loops)
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_options(-mfpu=neon -mfloat-abi=softfp)
elseif(ANDROID_ABI STREQUAL "arm64-v8a")
    # ARMv8 includes NEON SIMD by default; enable fp-armv8
    add_compile_options(-march=armv8-a+simd)
endif()

# FluidSynth compilé depuis les sources (submodule git à ajouter séparément,
# voir section "Étape manuelle requise" plus bas). Toutes les fonctionnalités
# non nécessaires sont désactivées pour minimiser la mémoire utilisée.
set(enable-sdl2 OFF CACHE BOOL "" FORCE)
set(enable-jack OFF CACHE BOOL "" FORCE)
set(enable-pulseaudio OFF CACHE BOOL "" FORCE)
set(enable-alsa OFF CACHE BOOL "" FORCE)
set(enable-oss OFF CACHE BOOL "" FORCE)
set(enable-dbus OFF CACHE BOOL "" FORCE)
set(enable-ipv6 OFF CACHE BOOL "" FORCE)
set(enable-network OFF CACHE BOOL "" FORCE)
set(enable-readline OFF CACHE BOOL "" FORCE)
set(enable-midishare OFF CACHE BOOL "" FORCE)
set(enable-opensles OFF CACHE BOOL "" FORCE)
set(enable-oboe OFF CACHE BOOL "" FORCE)
set(enable-aufile OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/fluidsynth/CMakeLists.txt")
    add_subdirectory(fluidsynth EXCLUDE_FROM_ALL)
endif()

add_library(
    native-lib
    SHARED
    native-lib.cpp
    audio_engine.cpp
    soundfont_engine.cpp
    sample_playback_engine.cpp
)

find_package(oboe REQUIRED CONFIG)
find_library(log-lib log)

target_include_directories(
    native-lib PRIVATE
    fluidsynth/include
    ${CMAKE_BINARY_DIR}/fluidsynth/include
)

target_link_libraries(
    native-lib
    oboe::oboe
    libfluidsynth
    ${log-lib}
)
