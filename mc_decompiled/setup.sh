#!/bin/zsh

# set version here
VERSION="26.2"

SRC="sources/$VERSION"

# fetch cfr if not present (kept at the mc_decompiled/ root, shared across versions)
if [ ! -f "cfr.jar" ]; then
    echo "CFR not found, downloading..."
    curl https://www.benf.org/other/cfr/cfr-0.152.jar -o cfr.jar
else
    echo "CFR already present, skipping download."
fi

# check if common.jar exists
if [ ! -f "$SRC/common.jar" ]; then
    echo "common.jar not found! Please place common.jar in $SRC and run this script again."
    exit 1
fi

# check if client.jar exists
if [ ! -f "$SRC/client.jar" ]; then
    echo "client.jar not found! Please place client.jar in $SRC and run this script again."
    exit 1
fi

# decompile
echo "Decompiling common.jar..."
java -jar cfr.jar "$SRC/common.jar" --outputdir "$SRC/common_src"

echo "Decompiling client.jar..."
java -jar cfr.jar "$SRC/client.jar" --outputdir "$SRC/client_src"
