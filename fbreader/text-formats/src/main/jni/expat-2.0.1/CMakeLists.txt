cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_VERBOSE_MAKEFILE, on)

add_library(expat
            STATIC
            lib/xmlparse.c
            lib/xmlrole.c
            lib/xmltok.c
)

include_directories(.
                    lib/
)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DHAVE_EXPAT_CONFIG_H")

set(distribution_DIR ${CMAKE_SOURCE_DIR}/distribution)
set_target_properties(expat
                      PROPERTIES
                      ARCHIVE_OUTPUT_DIRECTORY
                      "${distribution_DIR}/expat/lib/${ANDROID_ABI}")