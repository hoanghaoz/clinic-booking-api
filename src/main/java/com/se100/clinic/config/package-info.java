/**
 * Framework configuration only — Security, CORS, OpenAPI, JPA auditing, scheduling. No business
 * logic.
 *
 * <p>Unlike {@code shared} (reusable business building blocks such as exceptions and the audit
 * entity, see docs/setup/DECISIONS.md #12), nothing here is called by other modules; it only
 * declares Spring beans.
 */
package com.se100.clinic.config;
