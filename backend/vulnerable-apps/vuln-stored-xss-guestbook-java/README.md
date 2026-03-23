# Stored XSS Guestbook Lab

This module is a single-container Spring Boot lab for stored cross-site scripting.

## What Makes It Vulnerable

- Guestbook messages are stored in H2 without sanitization.
- Thymeleaf uses `th:utext` to render both author and message content.
- Any stored HTML or script payload is re-executed whenever the feed is viewed.

## Local Build

```bash
./mvnw -pl vulnerable-apps/vuln-stored-xss-guestbook-java -am clean package -DskipTests
```

## Build The Lab Image

```bash
docker build \
  -f dockerfiles/vuln-stored-xss-guestbook-java.Dockerfile \
  -t tianshuvuln/vuln-stored-xss-guestbook-java:0.1.0 \
  .
```

If you are running on local Minikube and do not want to push to an external registry:

```bash
minikube image load tianshuvuln/vuln-stored-xss-guestbook-java:0.1.0
```

## Platform Definition

Use the following values in `/admin`:

- `id`: `stored-xss-java-001`
- `name`: `Stored XSS Guestbook (Java)`
- `category`: `Cross-Site Scripting`
- `difficulty`: `Easy`
- `dockerImageName`: `tianshuvuln/vuln-stored-xss-guestbook-java:0.1.0`
- `containerPort`: `8081`
- `tags`: `java`, `xss`, `stored-xss`, `thymeleaf`, `guestbook`

## Example Exploitation Payload

```html
<img src=x onerror=alert(document.domain)>
```
