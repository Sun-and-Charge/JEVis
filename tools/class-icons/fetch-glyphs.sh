#!/usr/bin/env bash
# Copyright (C) 2026 Sun and Charge Germany GmbH
# This file is part of JEVis, licensed under the GNU General Public License version 3.
#
# Downloads the Material Symbols (google/material-design-icons, Apache License 2.0) named in mapping.tsv,
# outlined and filled, into <work>/outlined and <work>/filled.
# Usage: fetch-glyphs.sh <mapping.tsv> <work dir>
set -euo pipefail
MAPPING="${1:?mapping.tsv}"
WORK="${2:?work dir}"
BASE="https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web"
mkdir -p "$WORK/outlined" "$WORK/filled"
for name in $( (cut -f3 "$MAPPING"; echo folder) | sort -u); do
  [ -s "$WORK/outlined/$name.svg" ] || curl -sf -o "$WORK/outlined/$name.svg" "$BASE/$name/materialsymbolsoutlined/${name}_24px.svg"
  [ -s "$WORK/filled/$name.svg" ] || curl -sf -o "$WORK/filled/$name.svg" "$BASE/$name/materialsymbolsoutlined/${name}_fill1_24px.svg" || true
done
echo "glyphs in $WORK"
