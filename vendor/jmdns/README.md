# Vendored JmDNS 3.6.3

JmDNS 3.6.3 is built from the official sources jar
(`org.jmdns:jmdns:3.6.3:jmdns-3.6.3-sources.jar`) and kept in this module so the Android
compatibility work is reviewable as source rather than as an opaque artifact. The upstream licence is
Apache License 2.0 and is retained verbatim at
`src/main/resources/META-INF/LICENSE`; `docs/THIRD_PARTY_NOTICES.md` already lists JmDNS.

Only the classes under `javax/jmdns/**` are changed. The retained changes address API 22 Java compatibility and multicast socket binding on Android.

## Functional change

| File | Change | Reason |
|---|---|---|
| `impl/JmDNSImpl.openMulticastSocket` | `MulticastSocket` is created unbound, `setReuseAddress(true)` is applied, then it is bound | `new MulticastSocket(SocketAddress)` binds *before* address reuse can be enabled, so the bind fails with `EADDRINUSE` whenever Android's own `mdnsd` already holds UDP 5353. Group selection, interface binding, `joinGroup`, TTL, `closeMulticastSocket` and error recovery are unchanged. |

## API-level compatibility

These are mechanical substitutions with identical semantics; they exist because the corresponding
API postdates Android 5.1 / API 22.

| File | Upstream API | API level | Replacement |
|---|---|---|---|
| `impl/DNSCache` | `Collection.removeIf` | 24 | `Iterator.remove` filtering loop |
| `impl/DNSIncoming` | `Map.forEach` | 24 | `entrySet` loop |
| `impl/ServiceInfoImpl` | `Map.getOrDefault` | 24 | `containsKey ? get : default`, so a present key holding `null` still yields `null` as upstream does |
| `impl/SocketListener` | `Stream.anyMatch` | 24 | `for` loop; an empty question list still means "not unique" |

`ConcurrentMap.putIfAbsent` / `replace` are *not* touched: they are original interface methods
available since API 1, and replacing them would change the atomicity they provide.
