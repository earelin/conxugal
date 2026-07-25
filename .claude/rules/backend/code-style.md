---
paths:
  - "server/**/*.java"
---

# Backend Java code style

Java code follows [Google Java Format](https://google.github.io/styleguide/javaguide.html):
2-space indentation, 100-column line length, no wildcard imports, K&R brace style
(same-line `{`, `case`/`default` bodies on a new line). This is enforced by
Checkstyle — `server/config/checkstyle/checkstyle.xml` is Google's `google_checks.xml`
— but no formatter is wired into the build, so checkstyle only lints, it doesn't
reformat. Write new code matching the style directly; run `checkstyleMain`/
`checkstyleTest` (part of `./gradlew build`, run from `server/`) to verify.

Prefer `.formatted()` over `+` concatenation for strings mixing literal text and
variables, e.g. `"%s/%s".formatted(base, path)`.

Prefer a text block (`"""..."""`) over a regular string literal whenever it avoids
escape characters — e.g. a literal containing `"` (JSON, HTML, quoted attributes).

Fluent call chains with multiple distinct stages — e.g. REST-assured's
`given()/when()/then()` — are formatted as a staircase, regardless of chain length:
each stage keyword sits at the base indent, calls chained onto that stage indent one
level (4 spaces) deeper, and the next stage keyword steps back out to the base
indent:

```java
given(spec)
    .header(HttpHeaders.COOKIE, sessionCookie)
    .body("{}")
.when()
    .post("/logout");
```
