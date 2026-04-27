# E2E test public key — NOT a production secret

`public-key.pem` is the RSA public key whose matching **private key** signs JWTs
for the Cypress + Cucumber cross-remote E2E suite (TASK_LIST group 13). It's
loaded by `E2eJwtDecoderConfig` ONLY when the `e2e` Spring profile is active.

## Why it's safe to commit

- The matching private key is in the UI workspace at
  `apps/weekly-commit-ui/cypress/support/auth/keys/private-key.pem`, also
  committed and labeled "test only".
- Production runs the `prod` profile (or no profile + Auth0 issuer-uri env vars).
  The `e2e` profile is **never active in production** — verified by the integration
  test `E2eJwtDecoderConfigIT` and the absence of `SPRING_PROFILES_ACTIVE=e2e` in
  any production config / Helm chart / Terraform.
- An attacker who steals the private key can only forge JWTs that work against an
  e2e-profile backend they're already running. They have no leverage against any
  production service, because production trusts only the Auth0 JWKS — not this key.

## Rotation

If you ever want to rotate (say, paranoid hygiene every quarter):

```sh
cd /tmp
openssl genrsa -out e2e-test-private.pem 2048
openssl rsa -in e2e-test-private.pem -pubout -out e2e-test-public.pem
cp e2e-test-public.pem $REPO/apps/weekly-commit-service/src/main/resources/e2e-keys/public-key.pem
cp e2e-test-public.pem $REPO/apps/weekly-commit-ui/cypress/support/auth/keys/public-key.pem
cp e2e-test-private.pem $REPO/apps/weekly-commit-ui/cypress/support/auth/keys/private-key.pem
```

Make sure both copies of the public key match exactly — `E2eJwtDecoderConfigIT`
will fail otherwise.
