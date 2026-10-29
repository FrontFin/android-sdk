# Security Policy

## Reporting a vulnerability

Please report security vulnerabilities to **security@meshpay.com**. Do not open a
public GitHub issue for a security report.

We will acknowledge your report, keep you updated on our progress, and credit you
once a fix ships (unless you prefer to remain anonymous).

## Scope and handling in this SDK

- The SDK never logs the Link URL, the session token, bridge payloads, or caught
  error details in release builds.
- The session token and bridge payloads are never persisted.
- Bridge messages are accepted only from the session's Link origin, in the main
  frame; injected scripts are always JSON-encoded.
- The SDK requests no runtime permissions and ships no analytics, crash reporting
  or telemetry.
