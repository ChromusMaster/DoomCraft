#!/usr/bin/env bash
set -Eeuo pipefail

sudo apt-get update
sudo apt-get install -y --no-install-recommends \
  build-essential \
  cmake \
  ninja-build \
  pkg-config \
  git \
  libsdl2-dev \
  libvpx-dev \
  libgtk-3-dev \
  libwebp-dev \
  libopenal-dev \
  libsndfile1-dev \
  libfluidsynth-dev \
  libmpg123-dev \
  libopus-dev \
  libogg-dev \
  libvorbis-dev

cmake --version
ninja --version
