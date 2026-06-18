# Networking — letting friends connect (no VPN)

This host has a **real public IPv4** (not behind CGNAT), so friends connect
straight to it — no VPN, no relay. The path is:

```
DHCP reservation (laptop keeps a fixed LAN IP)
        ↓
Router port-forward (game ports → laptop)
        ↓
UFW on the host (allow the same game ports)
        ↓
Router DDNS → No-IP (a stable name for your changing residential IP)
        ↓
(optional) your own domain → CNAME → the No-IP name
```

RCON is the one thing that **never** leaves the laptop — see the rule below.

## 1. Reserve the laptop's LAN IP (DHCP reservation)

Port-forwards target a LAN IP, so that IP must not change. In your router admin
page (usually `192.168.0.1` / `192.168.1.1`) find **DHCP reservation** (a.k.a.
"static lease" / "address reservation"), pick the laptop by its MAC address, and
pin it to an address like `192.168.0.50`.

Find the laptop's MAC and current IP:

```bash
ip -brief address        # IPv4 per interface
ip link                  # MAC (the link/ether address)
```

## 2. Forward the game ports on your router

Forward **only the game you're running** (you run one at a time). Point each
rule at the reserved LAN IP from step 1, same external and internal port:

| Game       | Required (the game port) | Optional (server-browser listing) |
|------------|--------------------------|------------------------------------|
| Minecraft  | `25565/tcp`              | —                                  |
| Palworld   | `8211/udp`               | `27015/udp` (Steam query)          |
| ARK        | `7777/udp`               | `27015/udp` (Steam query)          |

Match the protocol exactly — Minecraft is **TCP**, Palworld/ARK are **UDP**.

The **game port alone is enough to connect** by address (which is how friends
join via your domain). The query port `27015/udp` only makes the server
**appear in the in-game / Steam worlds list** — forward it if you want that
visibility, skip it for a smaller attack surface. (ARK's legacy `7778/udp` raw
socket isn't needed on current builds.) `27015` is shared by Palworld and ARK,
so a single rule covers whichever you're running.

## 3. Open the same ports on the host (UFW)

The router hands the packets to the laptop; UFW still governs the host. Open the
same ports for the game you're running (add `27015/udp` only if you forwarded it
for worlds-list visibility):

```bash
# Minecraft
sudo ufw allow 25565/tcp

# Palworld
sudo ufw allow 8211/udp
sudo ufw allow 27015/udp   # optional: Steam query / worlds list

# ARK
sudo ufw allow 7777/udp
sudo ufw allow 27015/udp   # optional: Steam query / worlds list

sudo ufw enable          # if not already active
sudo ufw status verbose  # confirm
```

> **Docker + UFW caveat:** Docker publishes container ports by writing iptables
> rules directly, which **bypasses UFW** for those ports — so a forwarded game
> port stays reachable even if UFW would deny it. UFW still protects every
> *non-container* port on the host. To actually filter a container port, bind it
> to a specific interface in the compose `ports:` mapping or use the
> `DOCKER-USER` iptables chain. For game ports you *want* open, this is a non-issue.

## 4. RCON stays private — always

RCON is a remote admin console with no meaningful auth hardening. The compose
files bind it to `127.0.0.1` (`25575` for Minecraft/Palworld, `27020` for ARK)
so it never leaves the laptop.

- **Never** add a router port-forward for `25575` or `27020`.
- **Never** `ufw allow` those ports.

The backup script reaches RCON over localhost, which is all it needs.

## 5. Dynamic DNS so the name never changes

A residential public IP changes whenever the ISP feels like it, which would
break every friend's saved address. Dynamic DNS keeps a stable **name** pointed
at your current IP automatically.

### 5a. Let the *router* do the updating (preferred)

The router is the device that actually holds the public IP, so its built-in DDNS
client is the most reliable updater — and it keeps working even when the laptop
is off or rebooting, with **no credentials stored on the host**.

1. Create a free hostname at [No-IP](https://www.noip.com) — e.g.
   `senseei-games.servegame.com`. Tick **Enable Dynamic DNS** when creating it.
2. In No-IP, create a **DDNS Key** (sidebar → *DDNS Keys*) instead of using your
   account email/password. It gives you a plain username + password and a key
   hostname like `all.ddnskey.com`. This avoids the `@`-encoding bugs many
   routers have with email logins, and keeps your real account password off the
   router.
3. In the router's **DDNS** page: Provider `No-IP`, **Usuário/Senha** = the DDNS
   key's username/password, **Hostname** = `all.ddnskey.com` (the key updates all
   hostnames tied to it). Save.
4. Verify: in No-IP → *DNS Records*, the hostname's **last-update** time should
   move to "just now". Then `nslookup <your-hostname>` should return your public IP.

Friends could now connect to `senseei-games.servegame.com` (+ the game port).

### 5b. Front it with your own domain (optional but nicer)

To hand out `play.allsensis.com` instead of a No-IP name, add **one static
record** at your DNS provider (Route53 here) — it never needs updating because it
just aliases the No-IP name, which the router keeps fresh:

- **CNAME** `play.allsensis.com` → `senseei-games.servegame.com` (TTL 60)

For Minecraft you can also **hide the port** with an SRV record, so friends type
just `play.allsensis.com` with no `:25565`:

- **SRV** `_minecraft._tcp.play.allsensis.com` → `0 5 25565 senseei-games.servegame.com`

(SRV is Minecraft-only; Palworld/ARK still use `play.allsensis.com:8211` /
`:7777`.) Example using the AWS CLI:

```bash
aws route53 change-resource-record-sets --hosted-zone-id <ZONE_ID> \
  --change-batch file://r53-change.json   # CNAME + SRV UPSERTs
```

> **Test from outside, not from home Wi-Fi.** Many routers don't support NAT
> loopback (hairpinning), so connecting to your own public name from *inside* the
> LAN can fail even when everything is correct. Test from a phone on mobile data,
> or have a friend try. On the LAN, connect via the laptop's local IP.

### 5c. No router DDNS client?

If your router lacks a DDNS page, use any dynamic-DNS provider's own update client
to keep a hostname current, then point your domain at it with a CNAME exactly as in
5b. The router path above is preferred whenever it's available.

## Security note — you are now publicly exposed

Forwarding a port puts that service on the open internet, where it will be
scanned within minutes. Two non-negotiables:

- **Strong passwords** — set real values for `SERVER_PASSWORD` / `ADMIN_PASSWORD`
  / `RCON_PASSWORD` in `.env`. No defaults, no blanks on a public server.
- **Stay patched** — run `docker compose pull` regularly (then recreate) so
  you're not exposing a known-vulnerable old server build.

> Behind CGNAT instead, with no public IP? Port-forwarding can't work there — if
> you ever need CGNAT traversal, see Tailscale.
