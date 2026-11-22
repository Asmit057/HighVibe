# Deploying HighVibe for free

HighVibe is a full microservice stack: six Spring Boot services, PostgreSQL, Kafka, Redis, MinIO,
an nginx frontend and a Node.js dev server for every live preview. Together they need about
**6 GB of RAM**, so the usual free hosting tiers are too small:

| Platform | Free tier | Fits HighVibe? |
|---|---|---|
| Render, Railway, Koyeb, Fly.io | one small container, 256–512 MB RAM, sleeps when idle | No: one Spring service alone uses that |
| Vercel, Netlify | static sites and serverless functions only | Frontend only, and HTTPS pages cannot load the HTTP previews |
| AWS / Google Cloud free VMs | 1 vCPU, 1 GB RAM | No |
| **Oracle Cloud Always Free** | **Arm VM with up to 4 CPUs, 24 GB RAM, 200 GB disk, free with no time limit** | **Yes, with room to spare** |

The guide below uses Oracle Cloud. The same script works on any Ubuntu server with
at least 4 CPUs and 8 GB of RAM.

---

## How it runs on the server

```
             internet
                │
     ┌──────────┴───────────────────────────────────────────────┐
     │  :80  frontend (nginx)  ──/api──►  api-gateway             │
     │                                     │  (private network)   │
     │        account · workspace · intelligence · eureka · config │
     │        postgres · kafka · redis · minio                     │
     │                                                             │
     │  :5200-5210  live previews (Vite) inside workspace-service  │
     └─────────────────────────────────────────────────────────────┘
```

- `docker-compose.yml` defines every container; `docker-compose.prod.yml` adds the server settings:
  only port **80** (the website) and **5200-5210** (live previews) are public; the databases, Kafka,
  Redis, MinIO, Eureka, the config server and the gateway are reachable only inside Docker.
- Every container restarts on its own after a crash or a reboot, logs are rotated, and each Java
  service has a memory limit.
- One Maven build compiles all services, and Docker reuses it for the six service images.

---

## Step 1: create the Oracle Cloud account

1. Sign up at <https://signup.cloud.oracle.com>. Oracle asks for a card to verify your identity;
   Always Free resources are never charged.
2. Choose a **home region** close to you. It cannot be changed later, and Arm capacity differs by
   region (Mumbai and Hyderabad are both available in India).

## Step 2: create the virtual machine

1. In the console open **Compute → Instances → Create instance**.
2. **Image:** click *Edit* → *Change image* → **Canonical Ubuntu 24.04**.
3. **Shape:** *Change shape* → **Ampere** → `VM.Standard.A1.Flex`, **4 OCPUs and 24 GB memory**
   (2 OCPUs and 12 GB also works).
4. **Networking:** keep the default virtual cloud network and make sure *Assign a public IPv4 address* is on.
5. **SSH keys:** *Generate a key pair for me* and **download the private key**.
6. Click **Create**. When it is *Running*, copy the **Public IP address**.

> "Out of capacity for shape VM.Standard.A1.Flex" means the region has no free Arm machines right
> now. Pick a different *Availability domain*, try a smaller shape, or try again later.

## Step 3: open the ports

Oracle blocks everything except SSH until you allow it.

1. On the instance page click the **Subnet** link → **Security** (or *Security Lists*) →
   **Default Security List** → **Add Ingress Rules**.
2. Add two rules, both with **Source CIDR** `0.0.0.0/0` and **IP Protocol** TCP:
   - **Destination port** `80`: the website
   - **Destination port** `5200-5210`: the live previews

The deploy script opens the same ports in the server's own firewall.

## Step 4: connect and deploy

From your computer (Windows PowerShell, macOS or Linux):

```bash
ssh -i path/to/ssh-key.key ubuntu@YOUR_PUBLIC_IP
```

On the server:

```bash
curl -fsSL https://raw.githubusercontent.com/Asmit057/HighVibe/main/scripts/deploy.sh | bash
```

The script:

1. installs Docker, Git and a 4 GB swap file;
2. opens ports 80 and 5200-5210 in the firewall;
3. clones the repository into `~/HighVibe`;
4. writes `~/HighVibe/.env` with a random JWT secret, database password and MinIO password, plus the
   server's public IP as `PUBLIC_HOST`;
5. builds and starts every container (the first build takes 10-15 minutes);
6. waits until the API answers and prints the address.

Open **http://YOUR_PUBLIC_IP** in a browser, sign up and build something.

## Step 5: add the Gemini key

Without a key, the offline demo generator answers. To let Gemini write the apps:

```bash
nano ~/HighVibe/.env          # set GEMINI_API_KEY=your-key (free at https://aistudio.google.com/apikey)
~/HighVibe/scripts/deploy.sh  # applies the change
```

---

## Everyday commands

Run these inside `~/HighVibe`. The alias saves typing the two file names:

```bash
alias hv='sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile apps'
```

| What | Command |
|---|---|
| Deploy the latest code from GitHub | `./scripts/deploy.sh` |
| See every container and its state | `hv ps` |
| Follow the logs of one service | `hv logs -f intelligence-service` |
| Restart one service | `hv restart workspace-service` |
| Stop everything (data is kept) | `hv down` |
| Back up the databases | `hv exec postgres pg_dumpall -U highvibe > backup.sql` |

Data lives in Docker volumes (`postgres-data`, `minio-data`) and survives restarts and redeploys.

## Using a domain name (optional)

Point an `A` record (for example `highvibe.example.com`) at the server's IP, then run:

```bash
PUBLIC_HOST=highvibe.example.com ~/HighVibe/scripts/deploy.sh
```

The site stays on plain HTTP. Live previews are served from their own ports, and a browser will not
show an HTTP preview inside an HTTPS page (mixed content). HTTPS for the whole app needs the
Kubernetes preview mode, which serves every preview through one proxy on a wildcard domain
(`k8s/previews/`).

## Troubleshooting

| Symptom | Fix |
|---|---|
| The site does not open | Check the two ingress rules from step 3, then run `hv ps`: every container should be `running` |
| The site opens, the preview stays blank | Port range `5200-5210` is not open in the security list |
| "No free preview port between 5200 and 5210" | Eleven previews can run at once (one port each); they stop on their own after 60 minutes |
| The build stops with "killed" | Not enough memory: use at least 8 GB of RAM (the script already adds swap) |
| Gemini errors in the chat | Check `GEMINI_API_KEY` in `.env`, then run `./scripts/deploy.sh` again |

## Memory budget

| Container | Memory limit |
|---|---|
| workspace-service (plus its preview dev servers) | 512 MB heap + about 200 MB per running preview |
| api-gateway, account-service, intelligence-service | 384 MB heap each |
| discovery-service, config-service | 256 MB heap each |
| Kafka | 512 MB heap |
| PostgreSQL, Redis, MinIO, nginx | about 500 MB together |
