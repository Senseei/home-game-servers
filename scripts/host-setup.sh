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

# 2) Tailscale (so friends reach the server through CGNAT) ────────────────────
if ! need tailscale; then
  echo "→ installing Tailscale…"
  curl -fsSL https://tailscale.com/install.sh | sh
  echo "  ✓ run 'sudo tailscale up' then share your tailnet with friends"
else
  echo "✓ Tailscale present"
fi

# 3) rclone (offsite backups) ─────────────────────────────────────────────────
if ! need rclone; then
  echo "→ installing rclone…"
  curl -fsSL https://rclone.org/install.sh | sudo bash
  echo "  ✓ configure a remote with: rclone config   (Backblaze B2 or Cloudflare R2)"
else
  echo "✓ rclone present"
fi

# 4) Keep the laptop awake with the lid closed ───────────────────────────────
# A closed lid suspending the machine would kill the server. Disable lid-sleep.
if [[ -f /etc/systemd/logind.conf ]]; then
  echo "→ disabling suspend-on-lid-close (server must stay awake)…"
  sudo sed -i 's/^#\?HandleLidSwitch=.*/HandleLidSwitch=ignore/' /etc/systemd/logind.conf
  sudo sed -i 's/^#\?HandleLidSwitchExternalPower=.*/HandleLidSwitchExternalPower=ignore/' /etc/systemd/logind.conf
  sudo systemctl restart systemd-logind || true
  echo "  ✓ lid-close will no longer suspend the machine"
fi

# 5) Firewall: only needed if you port-forward instead of using Tailscale ─────
echo
echo "Firewall note:"
echo "  • Using Tailscale (recommended)? You don't need to open any public ports."
echo "  • Port-forwarding instead? Open the game ports on UFW + your router, e.g.:"
echo "      sudo ufw allow 25565/tcp     # Minecraft"
echo "      sudo ufw allow 8211/udp      # Palworld"
echo "      sudo ufw allow 7777,7778/udp # ARK"
echo "    and NEVER open the RCON ports (25575 / 27020)."
echo
echo "✓ host setup complete. Next: cp .env.example .env && edit it."
