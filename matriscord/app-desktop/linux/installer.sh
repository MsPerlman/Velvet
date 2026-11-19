#!/bin/sh
# puts velvetdesk in ~/.local, no root needed. run it from the unpacked folder
set -e
ici=$(dirname "$(readlink -f "$0")")
dest="$HOME/.local/opt/velvetdesk"
mkdir -p "$HOME/.local/opt" "$HOME/.local/bin" "$HOME/.local/share/applications" "$HOME/.local/share/icons/hicolor/256x256/apps"
if [ "$ici" != "$dest" ]; then rm -rf "$dest"; cp -a "$ici" "$dest"; fi
ln -sf "$dest/velvetdesk" "$HOME/.local/bin/velvetdesk"
cp "$dest/icone/velvet.png" "$HOME/.local/share/icons/hicolor/256x256/apps/velvetdesk.png"
sed "s|^Exec=.*|Exec=$dest/velvetdesk|" "$dest/velvetdesk.desktop" > "$HOME/.local/share/applications/velvetdesk.desktop"
echo "velvetdesk installed in $dest. it is in your app menu, or type velvetdesk"
