#!/bin/bash
set -e

REMOTE_HOST="xhao@infrastructure.pod326.demandware.net"
REMOTE_PORT=200
LOCAL_DIR="output/generated/pricebooks"
STAGING_DIR="/home/xhao/import"
TARGET_DIR="/remote/tbhg/tbhg_prd/sharedata/sites/Sites-Site/units/Sites/impex/src/catalog"

SOCKET="/tmp/ssh-upload-$$"

echo "=== Step 1: Establish SSH connection (2FA required) ==="
ssh -M -S "$SOCKET" -fnNT -p "$REMOTE_PORT" "$REMOTE_HOST"

echo "Connection established. Reusing for all transfers."

echo ""
echo "=== Step 2: Upload pricebook files via SCP ==="
for f in "$LOCAL_DIR"/*.xml.gz; do
    filename=$(basename "$f")
    # Check if file already exists on remote
    if ssh -S "$SOCKET" -p "$REMOTE_PORT" "$REMOTE_HOST" "test -f $STAGING_DIR/$filename" 2>/dev/null; then
        read -p "  $filename already exists in $STAGING_DIR. Replace? [y/N] " answer
        if [[ "$answer" != "y" && "$answer" != "Y" ]]; then
            echo "  Skipping $filename"
            continue
        fi
    fi
    echo "  Uploading $filename ..."
    scp -o "ControlPath=$SOCKET" -P "$REMOTE_PORT" "$f" "$REMOTE_HOST:$STAGING_DIR/$filename"
done

echo ""
echo "=== Step 3: Move files to target directory ==="
echo "  Copying all pricebook files to $TARGET_DIR ..."
ssh -t -S "$SOCKET" -p "$REMOTE_PORT" "$REMOTE_HOST" \
    "sudo cp -v $STAGING_DIR/pricebooks*.xml.gz $TARGET_DIR/"
echo "  Done."

echo ""
echo "=== Step 4: Cleanup staging directory ==="
ssh -S "$SOCKET" -p "$REMOTE_PORT" "$REMOTE_HOST" \
    "rm -f $STAGING_DIR/pricebooks*.xml.gz"

echo ""
echo "=== Step 5: Close SSH connection ==="
ssh -S "$SOCKET" -O exit -p "$REMOTE_PORT" "$REMOTE_HOST" 2>/dev/null

echo ""
echo "Done! All pricebook files uploaded and moved to $TARGET_DIR"