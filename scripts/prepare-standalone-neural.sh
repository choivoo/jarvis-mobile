#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIB_DIR="$ROOT/app/libs"
ASSET_ROOT="$ROOT/app/src/main/assets/jarvis_tts"
MODEL_DIR="$ASSET_ROOT/supertonic-3"
CACHE_DIR="$ROOT/.jarvis-cache"
AAR="$LIB_DIR/sherpa-onnx-1.13.2.aar"
ARCHIVE="$CACHE_DIR/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2"

mkdir -p "$LIB_DIR" "$ASSET_ROOT" "$CACHE_DIR"

if [ ! -f "$AAR" ]; then
  echo "[JARVIS] Downloading sherpa-onnx Android runtime..."
  curl -L --fail --retry 4 \
    "https://raw.githubusercontent.com/HayaiApp/HayaiTTS/09e66736b9f382742e477d627231043d8a1a872d/app/libs/sherpa-onnx-1.13.2.aar" \
    -o "$AAR"
fi

if [ ! -f "$MODEL_DIR/vocoder.int8.onnx" ]; then
  echo "[JARVIS] Downloading embedded Supertonic 3 neural voice model..."
  if [ ! -f "$ARCHIVE" ]; then
    curl -L --fail --retry 4 \
      "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2" \
      -o "$ARCHIVE"
  fi
  TMP="$CACHE_DIR/supertonic-extract"
  rm -rf "$TMP"
  mkdir -p "$TMP"
  tar -xjf "$ARCHIVE" -C "$TMP"
  SRC="$(find "$TMP" -maxdepth 2 -type f -name 'vocoder.int8.onnx' -printf '%h\n' | head -n 1)"
  if [ -z "$SRC" ]; then
    echo "ERROR: Supertonic model contents were not found after extraction."
    exit 1
  fi
  rm -rf "$MODEL_DIR"
  mkdir -p "$MODEL_DIR"
  cp "$SRC"/* "$MODEL_DIR"/
fi

for f in duration_predictor.int8.onnx text_encoder.int8.onnx vector_estimator.int8.onnx vocoder.int8.onnx tts.json unicode_indexer.bin voice.bin; do
  test -f "$MODEL_DIR/$f" || { echo "ERROR: missing neural model asset: $f"; exit 1; }
done

echo "[JARVIS] Standalone neural runtime ready."
echo "AAR: $AAR"
echo "MODEL: $MODEL_DIR"
