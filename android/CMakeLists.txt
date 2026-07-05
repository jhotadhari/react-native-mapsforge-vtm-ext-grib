# Wrapper CMakeLists.txt for react-native-mapsforge-vtm-ext-grib.
#
# Discovered by React Native autolinking at the android/ module root.
# Pulls in the codegen-generated TurboModule JNI from generated/jni/.
#
# Structure mirrors the parent library (react-native-mapsforge-vtm):
# the wrapper is discovered by autolinking and delegates to the
# generated JNI subdirectory.
cmake_minimum_required(VERSION 3.16)

# Codegen-generated TurboModule JNI.
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/generated/jni/"
                 extgrib_codegen_build)
