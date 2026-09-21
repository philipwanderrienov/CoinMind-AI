# CoinMind AI — Ubuntu Server MVP Setup

For the MVP, CoinMind AI runs as native Linux services. Docker is intentionally **not required**.

## Target server

- Ubuntu Server LTS
- Headless / terminal only
- No desktop environment required

## Runtime services

```text
Ubuntu Server
├── Java 25 LTS
│   └── CoinMind Spring Boot backend
├── PostgreSQL
├── TimescaleDB
├── Redis
├── Nginx
└── systemd
```

## Planned installation

### 1. Java

Use an OpenJDK 25 distribution available for Ubuntu and verify:

```bash
java -version
```

### 2. PostgreSQL

Install PostgreSQL from the Ubuntu/PostgreSQL repository appropriate for the server release.

Expected service:

```bash
sudo systemctl status postgresql
```

### 3. TimescaleDB

Install the TimescaleDB package matching the PostgreSQL version and enable the extension in the CoinMind database.

### 4. Redis

```bash
sudo apt install redis-server
sudo systemctl enable --now redis-server
sudo systemctl status redis-server
```

### 5. Nginx

```bash
sudo apt install nginx
sudo systemctl enable --now nginx
```

Nginx will later serve the Angular production build and reverse proxy the Spring Boot API/WebSocket endpoints.

## Suggested deployment layout

```text
/opt/coinmind/
├── backend/
│   └── coinmind.jar
├── frontend/
│   └── dist/
├── config/
│   └── application-prod.yml
└── logs/
```

## Backend systemd service

Planned service name:

```text
coinmind-backend.service
```

Example shape:

```ini
[Unit]
Description=CoinMind AI Backend
After=network.target postgresql.service redis-server.service

[Service]
User=coinmind
WorkingDirectory=/opt/coinmind/backend
ExecStart=/usr/bin/java -jar /opt/coinmind/backend/coinmind.jar --spring.profiles.active=prod
Restart=always
RestartSec=5
EnvironmentFile=-/opt/coinmind/config/coinmind.env

[Install]
WantedBy=multi-user.target
```

Useful commands:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now coinmind-backend
sudo systemctl status coinmind-backend
journalctl -u coinmind-backend -f
```

## MVP deployment policy

- Run services natively on Ubuntu Server.
- Do not install a GUI.
- Do not use Docker for the MVP.
- Keep `docker-compose.yml` only as a possible future deployment option.
- Credentials/API keys must live outside Git and must never be committed.
