#!/usr/bin/env bash
set -euo pipefail

# Build the Java-enabled, 16 KB-compatible ONNX Runtime used by this POC.
# The app loads ORT-format files, so the official minimal build is applicable.
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
 "${PY_ENV}/bin/pip" install 'onnx==1.18.0' 'flatbuffers==25.2.10' 'onnxruntime==1.22.0'

MODEL_DIR="${ROOT_DIR}/library/src/main/assets/models"
for model in "${ROOT_DIR}"/tools/source_models/*.onnx; do
  model_name="$(basename "${model}")"
  "${PY_ENV}/bin/python" -m onnxruntime.tools.convert_onnx_models_to_ort \
    "${model}" --output_dir "${MODEL_DIR}"
  generated="${MODEL_DIR}/${model_name%.onnx}.ort"
  test -f "${generated}" || { echo "Missing converted model: ${generated}" >&2; exit 1; }
done

"${PY_ENV}/bin/python" "${ORT_DIR}/tools/python/create_reduced_build_config.py" \
  --format ORT "${MODEL_DIR}" "${OPS_CONFIG}"
sed -i.bak '/^#/d' "${OPS_CONFIG}"
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
  --android_api=21 \
  --android_abi=armeabi-v7a,arm64-v8a \
  --android_ndk_path="${NDK_DIR}" \
  --cmake_path="${CMAKE_DIR}/bin/cmake" \
  --ctest_path="${CMAKE_DIR}/bin/ctest" \
  --minimal_build \
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
AAR_SOURCE="${ORT_DIR}/build/Android/Release/java/build/android/outputs/aar/onnxruntime-release.aar"
AAR_DEST="${ROOT_DIR}/library/src/main/onnxruntime-android-1.22.0-reduced.aar"
cp "${AAR_SOURCE}" "${AAR_DEST}"
echo "Copied reduced AAR to: ${AAR_DEST}"
