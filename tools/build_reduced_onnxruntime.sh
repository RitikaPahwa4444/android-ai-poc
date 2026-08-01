#!/usr/bin/env bash
set -euo pipefail

# Build the Java-enabled, arm64-only ONNX Runtime used by this POC.
# The app loads ONNX files directly, so this is a reduced-operator build rather
# than a minimal build; minimal builds require ORT-format model files.
# The script intentionally does not modify Gradle dependencies or copy files
# into the app; inspect/verify the output first, then run the copy commands
# printed at the end.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ORT_VERSION="${ORT_VERSION:-v1.22.0}"
ORT_DIR="${ORT_DIR:-${TMPDIR:-/tmp}/onnxruntime-${ORT_VERSION}}"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/Users/Shared/Library/Android/sdk}}"
NDK_DIR="${ANDROID_NDK_HOME:-${SDK_DIR}/ndk/28.2.13676358}"
CMAKE_DIR="${CMAKE_DIR:-${SDK_DIR}/cmake/4.1.2}"
PY_ENV="${PY_ENV:-${TMPDIR:-/tmp}/ort-venv}"
OPS_CONFIG="${ROOT_DIR}/tools/reduced_ops.config"
EIGEN_COMMIT="1d8b82b0740839c0de7f1242a3585e3390ff5f33"
EIGEN_DIR="${EIGEN_DIR:-${TMPDIR:-/tmp}/eigen-${EIGEN_COMMIT}}"

# The Java/AAR sub-build uses Gradle and reads the SDK from the environment,
# unlike the native CMake step which receives --android_sdk_path explicitly.
export ANDROID_HOME="${SDK_DIR}"
export ANDROID_SDK_ROOT="${SDK_DIR}"

if [[ ! -d "${ORT_DIR}" ]]; then
  git clone --recursive --branch "${ORT_VERSION}" --depth 1 \
    https://github.com/microsoft/onnxruntime.git "${ORT_DIR}"
fi

python3 -m venv "${PY_ENV}"
"${PY_ENV}/bin/pip" install 'onnx==1.18.0' 'flatbuffers==25.2.10'

# Regenerate from every bundled graph. The app currently selects the INT8 plate
# graph, but keeping the full-precision graph supported prevents a runtime error
# if that asset is selected during testing or later becomes the default.
"${PY_ENV}/bin/python" "${ORT_DIR}/tools/python/create_reduced_build_config.py" \
  "${ROOT_DIR}/app/src/main/assets/models" "${OPS_CONFIG}"

# Graph optimization can fuse Conv nodes into this contrib kernel at runtime;
# it is not present in the original ONNX graph, so preserve it after generation.
sed -i.bak 's/^com\.microsoft;1;.*/com.microsoft;1;FusedConv,QLinearConcat,QLinearSoftmax/' "${OPS_CONFIG}"
rm -f "${OPS_CONFIG}.bak"

if [[ ! -d "${EIGEN_DIR}" ]]; then
  git clone --filter=blob:none --no-checkout \
    https://gitlab.com/libeigen/eigen.git "${EIGEN_DIR}"
  git -C "${EIGEN_DIR}" fetch --depth 1 origin "${EIGEN_COMMIT}"
  git -C "${EIGEN_DIR}" checkout --detach FETCH_HEAD
fi

BUILD_ARGS=( \
  --android \
  --android_sdk_path="${SDK_DIR}" \
  --android_api=24 \
  --android_abi=arm64-v8a \
  --android_ndk_path="${NDK_DIR}" \
  --cmake_path="${CMAKE_DIR}/bin/cmake" \
  --ctest_path="${CMAKE_DIR}/bin/ctest" \
  --include_ops_by_config="${OPS_CONFIG}" \
  --build_java \
  --target onnxruntime4j_jni \
  --config Release \
  --skip_tests
)

BUILD_ARGS+=(--cmake_extra_defines "FETCHCONTENT_SOURCE_DIR_EIGEN3=${EIGEN_DIR}")

PATH="${PY_ENV}/bin:${PATH}" "${ORT_DIR}/build.sh" "${BUILD_ARGS[@]}"

echo "Build complete. Locate the libraries with:"
find "${ORT_DIR}/build/Android" -type f \( -name 'libonnxruntime.so' -o -name 'libonnxruntime4j_jni.so' \) -print
echo "Copy both arm64-v8a libraries into:"
echo "${ROOT_DIR}/app/src/main/jniLibs/arm64-v8a/"
