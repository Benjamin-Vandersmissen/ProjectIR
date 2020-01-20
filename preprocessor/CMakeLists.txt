cmake_minimum_required(VERSION 3.14)
project(Information_Retrieval)

set(CMAKE_CXX_STANDARD 17)
add_library(tinyxml2 tinyXML/tinyxml2.cpp tinyXML/tinyxml2.h)

add_executable(Information_Retrieval main.cpp)
target_link_libraries(Information_Retrieval tinyxml2)