package com.se100.clinic.shared;

/**
 * One entry of the {@code errors} array in a {@code VALIDATION_ERROR} response: which input {@code
 * field} (body property or query parameter) is wrong and why.
 */
public record FieldViolation(String field, String message) {}
