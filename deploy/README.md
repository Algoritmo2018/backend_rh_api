# Staging local (todo o sistema em Docker)

Corre o sistema completo no seu computador com um só comando, **sem instalar Java nem Node**: base de dados,
armazenamento de ficheiros, caixa de email de testes, backend e site. É o mesmo formato que depois serve para o
servidor real (ver o fim desta página).

> **Só para testes neste PC.** As contas de demonstração têm uma senha conhecida. O backend recusa carregá-las se o
> endereço do site não for `localhost`.

## 1. Preparar (uma vez)

1. Instale o **Docker Desktop** para Windows (com WSL 2) e abra-o. Em *Settings → Resources*, dê-lhe pelo menos
   **4 GB de memória**.
2. Tenha as duas pastas do projeto **lado a lado**, por exemplo:

   ```text
   Projecto_de_software_Recrutamento\
     recrutamento-api\   (backend, contém esta pasta deploy\)
     Front_RH\           (frontend)
   ```

3. Abra o **PowerShell** na pasta `recrutamento-api` e crie o ficheiro de segredos (senhas e chaves aleatórias,
   guardadas só neste PC em `deploy\.env.staging`, nunca no git):

   ```powershell
   powershell -ExecutionPolicy Bypass -File deploy\staging.ps1 init
   ```

## 2. Arrancar

```powershell
powershell -ExecutionPolicy Bypass -File deploy\staging.ps1 up
```

A primeira vez demora vários minutos (descarrega imagens e compila tudo). O comando só termina quando tudo está
pronto. Depois abra no **Chrome, Edge ou Firefox**:

| O quê | Endereço |
|---|---|
| Aplicação (só este PC; ver secção 4 para a abrir à rede) | <http://localhost:8088/software_rh/> |
| Emails enviados (Mailpit): convites, recuperação de senha, confirmações | <http://localhost:8026> |
| Consola do MinIO (ficheiros), opcional | <http://localhost:9011> (utilizador/senha `MINIO_ROOT_*` do `.env.staging`) |
| Base de dados (DBeaver…), opcional | `localhost:5433` (`POSTGRES_*` do `.env.staging`) |

**Contas de teste:** as de `dev/README.md` (por exemplo `ana.costa@fehoiram.local`, administradora). A senha está
nesse ficheiro.

Sem as contas de teste (base vazia): em `deploy\.env.staging` mude para `SPRING_PROFILES_ACTIVE=staging` e faça
`reset`. É criado o administrador `admin@fehoiram.local` e o convite para definir a senha chega ao Mailpit.

## 3. Comandos do dia a dia

Todos com `powershell -ExecutionPolicy Bypass -File deploy\staging.ps1 <comando>`:

| Comando | Para quê |
|---|---|
| `up` | Constrói e arranca tudo |
| `status` | Estado de cada contentor (`healthy` = pronto) |
| `logs` / `logs backend` / `logs web` | Últimos registos; acrescente `-Follow` para seguir em direto |
| `sms` | **Códigos SMS** simulados (verificação em 2 passos, entrada por SMS, mudança de telemóvel); `-Follow` para seguir |
| `rebuild` / `rebuild backend` / `rebuild web` | Depois de alterar o código (ou de um `git pull`) |
| `down` | Para tudo; os dados ficam guardados |
| `reset` | **Apaga tudo** (base de dados, ficheiros, emails) e recomeça do zero |

Os mesmos comandos sem o script (por exemplo para Linux/macOS), a partir da pasta `recrutamento-api`:

```powershell
docker compose -f deploy/docker-compose.staging.yml --env-file deploy/.env.staging up -d --build --wait
docker compose -f deploy/docker-compose.staging.yml --env-file deploy/.env.staging ps
docker compose -f deploy/docker-compose.staging.yml --env-file deploy/.env.staging logs -f backend
docker compose -f deploy/docker-compose.staging.yml --env-file deploy/.env.staging down        # parar
docker compose -f deploy/docker-compose.staging.yml --env-file deploy/.env.staging down -v     # apagar dados
```

## 4. Segurança e portas

- **Por omissão, tudo fica acessível só a partir deste PC** (`127.0.0.1`): a aplicação (8088), o Mailpit (8026), a
  base de dados (5433) e a consola do MinIO (9011). O Mailpit contém links de convite e de recuperação de senha, e a
  base de dados e os ficheiros contêm dados pessoais.
- O backend não publica nenhuma porta: só o Nginx do site fala com ele.
- **Abrir a aplicação à rede local** (por exemplo para ver o site noutro computador): em `deploy\.env.staging` mude
  `WEB_BIND=127.0.0.1` para `WEB_BIND=0.0.0.0` e faça `rebuild web`. Só a aplicação abre; o resto continua fechado.

  > ⚠️ **Atenção:** com as contas de teste ativas (`SPRING_PROFILES_ACTIVE=staging,devseed`), **quem estiver na mesma
  > rede entra com essas contas**, incluindo a de administrador, porque a senha é conhecida (`dev/README.md`). Abra
  > só numa rede de confiança, pelo tempo necessário, e volte a pôr `WEB_BIND=127.0.0.1` no fim.

- **A sessão só se mantém em `localhost`.** Aberto a partir de outro aparelho (`http://192.168.x.x:8088`), o login
  funciona, mas não sobrevive a recarregar a página: o cookie da sessão exige HTTPS fora de `localhost`. Para isso
  seria preciso HTTPS local (certificado `mkcert`). Não está incluído.

## 5. Problemas comuns

| Sintoma | O que fazer |
|---|---|
| `port is already allocated` | Outra aplicação usa a porta. Mude `STAGING_HTTP_PORT` (ou outra) em `deploy\.env.staging` e faça `up`. |
| `up` termina com erro / `backend` não fica `healthy` | `logs backend`. Na primeira vez, o backend pode reiniciar uma ou duas vezes até o MinIO estar pronto: é normal. |
| `Refusing to start: the development seed…` | O endereço do site não é `localhost`. As contas de teste só são permitidas neste PC. |
| `Run deploy/staging.ps1 init` | Falta o `deploy\.env.staging`: corra `init`. |
| Erro `\r` / `bad interpreter` ao construir | Finais de linha do Windows. Ver a secção 6. |
| O site mostra "Não foi possível contactar o servidor" | O backend está a arrancar ou parado: `status` e `logs backend`. |
| Apagou o `.env.staging` e os ficheiros deixaram de abrir | A chave do MinIO mudou. Faça `reset` (recomeça do zero). |

## 6. Finais de linha (Windows)

Os ficheiros `mvnw`, `*.sh` e `*.template` têm de ter finais de linha Unix (LF): o `.gitattributes` dos dois
repositórios garante isso e a imagem do backend corrige o `mvnw` por segurança. Se um ficheiro foi extraído antes
do `.gitattributes` existir, normalize-o uma vez em cada repositório:

```powershell
git add --renormalize .
git status   # revê o que mudou antes de fazer commit
```

## 7. O modo de desenvolvimento continua igual

Este modo não substitui o de desenvolvimento (`mvnw spring-boot:run` + `npm run dev`, ver `dev/README.md`): usa
outras portas, outra base de dados e outros volumes, por isso os dois podem estar ligados ao mesmo tempo.

## 8. O que muda no servidor real (etapa de deploy)

Domínio e DNS; certificado HTTPS com renovação automática (+ HSTS); firewall só com as portas 80/443; perfil `prod`
(nunca `devseed`) e fornecedor de SMS real; IP real atrás de um balanceador, se existir; SMTP real com SPF, DKIM e
DMARC; armazenamento S3 compatível gerido em vez do MinIO (bucket criado pelos operadores); backups do Postgres
(diários, fora do servidor, com teste de reposição); antivírus dos ficheiros (ClamAV); logs e monitorização; segredos
fora de ficheiros; obrigações da Lei n.º 22/11 sobre o alojamento dos dados.
