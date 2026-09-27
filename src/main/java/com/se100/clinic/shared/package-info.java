/**
 * Shared kernel.
 *
 * <p>Contains the global exception handler ({@link
 * com.se100.clinic.shared.GlobalExceptionHandler}), the audit base entity ({@link
 * com.se100.clinic.shared.BaseAuditEntity}) and common types ({@link
 * com.se100.clinic.shared.Role}).
 *
 * <p>This is the ONLY exception to the rule "modules never access each other directly": any module
 * may import public classes from {@code shared}, because it holds no business
 * Controller/Repository/Entity — only cross-cutting infrastructure.
 */
package com.se100.clinic.shared;
