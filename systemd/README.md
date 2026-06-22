# Automated backups via systemd timer

Hands-off backups: run `./ctl backup <game>` for each game every 2 hours. Copy these two
files into `/etc/systemd/system/`, replace `YOUR_USER` with your username, then
enable per game.

`game-backup@.service`:

```ini
[Unit]
Description=Backup %i game world

[Service]
Type=oneshot
User=YOUR_USER
WorkingDirectory=/home/YOUR_USER/home-game-servers
ExecStart=/home/YOUR_USER/home-game-servers/ctl backup %i
```

`game-backup@.timer`:

```ini
[Unit]
Description=Periodic backup for %i

[Timer]
OnCalendar=*-*-* 0/2:00:00
Persistent=true

[Install]
WantedBy=timers.target
```

Enable for the games you run:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now game-backup@minecraft.timer
sudo systemctl enable --now game-backup@palworld.timer
sudo systemctl enable --now game-backup@ark-se.timer

# check schedule
systemctl list-timers 'game-backup@*'
```

`Persistent=true` means a missed backup (laptop was off) runs at next boot.
