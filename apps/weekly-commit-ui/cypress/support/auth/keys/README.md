# E2E test keypair — NOT production secrets

This directory contains an RSA keypair (`private-key.pem` + `public-key.pem`)
used **only** by the Cypress + Cucumber cross-remote E2E suite to sign JWTs that
the backend's `e2e` Spring profile trusts.

## Why it's safe to commit

The backend service trusts this public key **only** under the `e2e` profile (see
`apps/weekly-commit-service/src/main/resources/e2e-keys/`). Production runs the
`prod` profile (or no profile) and validates JWTs against Auth0's JWKS — this
keypair has zero authority against any non-`e2e` deployment.

If an attacker steals the private key, the only thing they can do is forge JWTs
that work against an `e2e`-profile backend they're already running locally. No
production exposure.

## Public-key sync

The public key here MUST match the public key in
`apps/weekly-commit-service/src/main/resources/e2e-keys/public-key.pem` byte-for-byte.
The backend integration test `E2eJwtDecoderConfigIT` enforces this; it'll fail
loudly if the two drift.

If you regenerate the keypair (see the BE-side README for the openssl command),
update **both** copies of the public key in the same commit.
