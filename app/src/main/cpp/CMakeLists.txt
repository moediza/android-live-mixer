cmake_minimum_required(VERSION 3.22.1)
project("livemixer")

include(FetchContent)
FetchContent_Declare(
  oboe
  GIT_REPOSITORY https://github.com/google/oboe.git
  GIT_TAG 1.9.0
)
FetchContent_MakeAvailable(oboe)

add_library(livemixer SHARED
    native-lib.cpp
    AudioEngine.cpp
)

target_link_libraries(livemixer
    oboe
    log
)