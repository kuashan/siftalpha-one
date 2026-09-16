cmake_minimum_required(VERSION 3.22.1)

project(siftalpha_x_native LANGUAGES CXX)

if(NOT CPYTHON_PREFIX_DIR)
    message(FATAL_ERROR "CPYTHON_PREFIX_DIR is required")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(cpython SHARED IMPORTED)
set_target_properties(cpython PROPERTIES
    IMPORTED_LOCATION "${CPYTHON_PREFIX_DIR}/lib/libpython3.14.so"
    INTERFACE_INCLUDE_DIRECTORIES "${CPYTHON_PREFIX_DIR}/include/python3.14"
)

add_library(siftalpha_x_native SHARED EmbeddedPythonNative.cpp)

find_library(log-lib log)
find_library(dl-lib dl)
find_library(m-lib m)

target_link_libraries(siftalpha_x_native
    PRIVATE
    cpython
    ${log-lib}
    ${dl-lib}
    ${m-lib}
)
