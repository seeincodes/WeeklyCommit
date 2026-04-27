/// <reference types="cypress" />

// Cypress's per-spec setup hook. Imported once per spec file via the
// `supportFile` option in cypress.config.ts. Runs before any `before()` block
// in the spec, so it's the right place for globals like custom commands.

import { signTestJwt } from './auth/signTestJwt';
import type { TestRole } from './auth/testUsers';

declare global {
  namespace Cypress {
    interface Chainable {
      /**
       * Signs a JWT for the given test role and arranges for every subsequent
       * `cy.intercept(...)` and real fetch from the app to carry the
       * `Authorization: Bearer <token>` header.
       *
       * The token is signed with the test private key checked in at
       * `cypress/support/auth/keys/private-key.pem`. The backend service's
       * E2eJwtDecoderConfig trusts the matching public key when the `e2e`
       * Spring profile is active (production runs `prod` and ignores it).
       */
      loginAs(role: TestRole): Chainable<string>;
    }
  }
}

Cypress.Commands.add('loginAs', (role: TestRole) => {
  // The private key file is read at command time (not module load) so a key
  // rotation between test files takes effect without restarting cypress.
  return cy
    .readFile('cypress/support/auth/keys/private-key.pem')
    .then(async (privateKeyPem: string) => {
      const token = await signTestJwt(role, privateKeyPem);

      // Two surfaces hand the token to outgoing requests:
      //   (a) Stash it on a window global the app's RTK Query baseQuery can
      //       read (the app picks this up in lieu of an Auth0-issued token
      //       when running under the e2e harness).
      //   (b) Add a global request interceptor so any fetch / XHR -- even
      //       ones the app makes outside RTK -- gets the Authorization header
      //       too. Belt + braces.
      cy.window().then((win) => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (win as any).__WC_E2E_TOKEN__ = token;
      });
      cy.intercept('**/api/v1/**', (req) => {
        req.headers['authorization'] = `Bearer ${token}`;
      });

      return cy.wrap(token);
    });
});

export {};
