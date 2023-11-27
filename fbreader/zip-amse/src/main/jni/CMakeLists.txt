cmake_minimum_required(VERSION 3.6.0)

set(CMAKE_VERBOSE_MAKEFILE, on)

add_library(DeflatingDecompressor-v3
            SHARED
		DeflatingDecompressor/DeflatingDecompressor.cpp
)

include_directories(android)

target_link_libraries(DeflatingDecompressor-v3
                      z)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -std=gnu++11")