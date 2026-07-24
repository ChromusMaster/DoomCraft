#!/usr/bin/env bash
set -Eeuo pipefail

export HOMEBREW_NO_AUTO_UPDATE=1

brew install \
  cmake \
  ninja \
  pkg-config \
  sdl2 \
  libvpx \
  webp \
  openal-soft \
  libsndfile \
  fluidsynth \
  mpg123 \
  opus \
  libogg \
  libvorbis

prefixes=()
library_paths=()
pkg_paths=()

for formula in \
  sdl2 \
  libvpx \
  webp \
  openal-soft \
  libsndfile \
  fluidsynth \
  mpg123 \
  opus \
  libogg \
  libvorbis
 do
  prefix="$(brew --prefix "$formula")"
  prefixes+=("$prefix")

  [[ -d "$prefix/lib" ]] && \
    library_paths+=("$prefix/lib")

  [[ -d "$prefix/lib/pkgconfig" ]] && \
    pkg_paths+=("$prefix/lib/pkgconfig")
 done

{
  echo "DOOMCRAFT_CMAKE_PREFIX_PATH=$(IFS=';'; echo "${prefixes[*]}")"
  echo "DOOMCRAFT_LIBRARY_PATHS=$(IFS=':'; echo "${library_paths[*]}")"
  echo "PKG_CONFIG_PATH=$(IFS=':'; echo "${pkg_paths[*]}"):${PKG_CONFIG_PATH:-}"
} >> "$GITHUB_ENV"

cmake --version
ninja --version
pkg-config --version
brew --prefix webp
