#!/usr/bin/env bash
# Prepara un host Linux (WSL2 Ubuntu) para compilar la app Android de HDR DualController.
# Requiere antes (con sudo): openjdk-17-jdk-headless protobuf-compiler python3-protobuf unzip
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
# Mismas versiones que android/app/build.gradle (ndkVersion, cmake version, compileSdk).
NDK_VERSION="28.2.13676358"
CMAKE_VERSION="3.22.1"
PLATFORM="android-35"
BUILD_TOOLS="35.0.0"

missing=()
for c in java protoc python3 perl make unzip; do command -v "$c" >/dev/null || missing+=("$c"); done
python3 -c 'import google.protobuf' 2>/dev/null || missing+=("python3-protobuf")
if ((${#missing[@]})); then
	echo "Faltan dependencias del sistema: ${missing[*]}" >&2
	echo "sudo apt install -y openjdk-17-jdk-headless protobuf-compiler python3-protobuf unzip" >&2
	exit 1
fi

if [[ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]]; then
	zip=$(curl -fsSL https://dl.google.com/android/repository/repository2-3.xml \
		| grep -oE 'commandlinetools-linux-[0-9]+_latest\.zip' | sort -V | tail -1)
	tmp=$(mktemp -d)
	curl -fsSL -o "$tmp/clt.zip" "https://dl.google.com/android/repository/$zip"
	unzip -q "$tmp/clt.zip" -d "$tmp"
	mkdir -p "$SDK/cmdline-tools"
	rm -rf "$SDK/cmdline-tools/latest"
	mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
	rm -rf "$tmp"
fi

bin="$SDK/cmdline-tools/latest/bin"
if [[ -x "$bin/android" ]]; then
	# cmdline-tools 23+: sdkmanager está deprecado y no instala nada; la CLI nueva es `android sdk`.
	"$bin/android" --no-metrics --sdk="$SDK" sdk install \
		"platforms/$PLATFORM" "build-tools/$BUILD_TOOLS" "platform-tools" \
		"ndk/$NDK_VERSION" "cmake/$CMAKE_VERSION"
else
	yes | "$bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null
	"$bin/sdkmanager" --sdk_root="$SDK" \
		"platforms;$PLATFORM" "build-tools;$BUILD_TOOLS" "platform-tools" \
		"ndk;$NDK_VERSION" "cmake;$CMAKE_VERSION"
fi
for p in "platforms/$PLATFORM" "build-tools/$BUILD_TOOLS" "platform-tools" "ndk/$NDK_VERSION" "cmake/$CMAKE_VERSION"; do
	[[ -d "$SDK/$p" ]] || { echo "No se instaló $p" >&2; exit 1; }
done

repo_android="$(cd "$(dirname "$0")/../android" && pwd)"
echo "sdk.dir=$SDK" > "$repo_android/local.properties"

echo "Listo. SDK en $SDK; local.properties escrito en $repo_android"
