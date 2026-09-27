/**
 * Module {@code doctor} — specialties (ChuyenKhoa) and doctors (BacSi), docs/de-tai.md section B.
 *
 * <p><b>THIS IS THE SAMPLE MODULE.</b> Only Specialty CRUD is implemented. When starting a new
 * module, copy this package's STRUCTURE (not its business content):
 *
 * <ul>
 *   <li>{@code XxxController} — package-private; HTTP routing only, no business logic
 *   <li>{@code XxxService} — PUBLIC; the only entry point other modules may call
 *   <li>{@code XxxRepository} — package-private; used only by the service in this package
 *   <li>Entity (e.g. {@code Specialty}) — package-private; never leaves the module
 *   <li>{@code XxxDtos} — records; public when other modules need them
 *   <li>Domain events (when needed) — public; published via {@code ApplicationEventPublisher}
 * </ul>
 *
 * <p>"package-private" = no access modifier at all. Only code in the exact same package can see it,
 * so the compiler rejects any other module importing our repository or entity. See
 * docs/JAVA_FOR_NESTJS_DEVS.md for the NestJS/ASP.NET Core concept mapping.
 */
package com.se100.clinic.doctor;
