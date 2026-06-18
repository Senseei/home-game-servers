#!/usr/bin/env bash
# host-setup.sh — one-time setup for a Linux laptop acting as the game host.
# Tested against Debian/Ubuntu/Mint. Re-runnable (idempotent-ish).
#
#   ./scripts/host-setup.sh
#
set -euo pipefail
echo "── home-game-servers host setup ──"

need() { command -v "$1" >/dev/null 2>&1; }

# 1) Docker + Compose plugin ──────────────────────────────────────────────────
if ! need docker; then
  echo "→ installing Docker…"
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker "$USER"
  echo "  ✓ Docker installed — log out/in (or 'newgrp docker') for group to apply"
else
  echo "✓ Docker present"
fi

# 2) rclone (offsite backups) ─────────────────────────────────────────────────
if ! need rclone; then
  echo "→ installing rclone…"
  curl -fsSL https://rclone.org/install.sh | sudo bash
  echo "  ✓ configure a remote with: rclone config   (Backblaze B2 or Cloudflare R2)"
else
  echo "✓ rclone present"
fi

# 3) Keep the laptop awake with the lid closed ───────────────────────────────
# A closed lid suspending the machine would kill the server. Disable lid-sleep.
if [[ -f /etc/systemd/logind.conf ]]; then
  echo "→ disabling suspend-on-lid-close (server must stay awake)…"
  sudo sed -i 's/^#\?HandleLidSwitch=.*/HandleLidSwitch=ignore/' /etc/systemd/logind.conf
  sudo sed -i 's/^#\?HandleLidSwitchExternalPower=.*/HandleLidSwitchExternalPower=ignore/' /etc/systemd/logind.conf
  sudo systemctl restart systemd-logind || true
  echo "  ✓ lid-close will no longer suspend the machine"
fi

# 4) Firewall: open the game ports you port-forward (see NETWORKING.md) ───────
echo
echo "Firewall note (full walkthrough in NETWORKING.md):"
echo "  1. DHCP-reserve this laptop's LAN IP, then port-forward the game ports"
echo "     on your router to it."
echo "  2. Open the SAME game ports on UFW — only the game you're running, e.g.:"
echo "      sudo ufw allow 25565/tcp        # Minecraft"
echo "      sudo ufw allow 8211/udp         # Palworld game"
echo "      sudo ufw allow 27015/udp        # Palworld/ARK Steam query"
echo "      sudo ufw allow 7777,7778/udp    # ARK game + raw socket"
echo "  3. NEVER forward or open the RCON ports (25575 / 27020) — they stay"
echo "     bound to 127.0.0.1."
echo
echo "✓ host setup complete. Next: cp .env.example .env && edit it."
