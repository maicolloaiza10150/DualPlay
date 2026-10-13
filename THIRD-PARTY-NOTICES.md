# Third-party notices / Avisos de terceros

DualPlay is distributed under the GNU Affero General Public License v3.0 with the OpenSSL exception
(see [COPYING](COPYING) and [LICENSES/AGPL-3.0-only-OpenSSL.txt](LICENSES/AGPL-3.0-only-OpenSSL.txt)).
It is a fork of chiaki-ng, which is a continuation of Chiaki; both are credited below.

The components below are built into or linked by the Android APK. Versions are the ones pinned in this repository at the
time of writing; check `cmake/`, `android/gradle/libs.versions.toml` and `android/app/build.gradle` for the current ones.
Each project's own license text applies and is available at the linked source.

## Project lineage

| Project | Author | License |
|---------|--------|---------|
| [Chiaki](https://git.sr.ht/~thestr4ng3r/chiaki) | Florian Märkl (thestr4ng3r) and contributors | AGPL-3.0 with OpenSSL exception |
| [chiaki-ng](https://github.com/streetpea/chiaki-ng) | streetpea and contributors | AGPL-3.0 with OpenSSL exception |

## Native libraries

| Component | Version | License | Source |
|-----------|---------|---------|--------|
| Opus | 1.5.2 | BSD-3-Clause | https://opus-codec.org/ |
| OpenSSL | 1.1.1w | OpenSSL License + SSLeay License (Apache-2.0 only from 3.0) | https://www.openssl.org/ |
| json-c | 0.17-20230812 | MIT | https://github.com/json-c/json-c |
| miniupnpc | 2.2.8 | BSD-3-Clause | https://github.com/miniupnp/miniupnp |
| nanopb | git submodule `third-party/nanopb` | zlib | https://github.com/nanopb/nanopb |
| Jerasure | git submodule `third-party/jerasure` | BSD-3-Clause | https://github.com/streetpea/jerasure |
| GF-Complete | git submodule `third-party/gf-complete` | BSD-3-Clause | https://github.com/streetpea/gf-complete |
| curl | git submodule `third-party/curl` | curl license (MIT-style) | https://curl.se/ |
| Oboe | 1.10.0 (git submodule) | Apache-2.0 | https://github.com/google/oboe |

## Android / Kotlin libraries

| Component | License |
|-----------|---------|
| [hiddenapibypass](https://github.com/LSPosed/AndroidHiddenApiBypass) 6.1 | Apache-2.0 |
| AndroidX (Activity, AppCompat, Core, ConstraintLayout, RecyclerView, Preference, Lifecycle, Navigation, Room, Security-Crypto) | Apache-2.0 |
| Jetpack Compose (UI, Material 3, Runtime) | Apache-2.0 |
| Material Components for Android 1.12.0 | Apache-2.0 |
| Kotlin standard library and kotlinx.coroutines 1.8.1 | Apache-2.0 |
| Moshi 1.15.2 | Apache-2.0 |

## Graphics and fonts

- The launcher icon and the interface artwork of DualPlay are original work released under the project license.
- Vector icons derived from Material Design Icons (Apache-2.0, Google).
- No third-party font is bundled; the interface uses the system font.
- PlayStation console illustrations inherited from upstream are **not** used by DualPlay.

## Trademarks

"PlayStation", "PS5", "PS4", "DualSense" and related names and logos are trademarks of Sony Interactive Entertainment LLC.
DualPlay is not affiliated with, endorsed by or sponsored by Sony Interactive Entertainment. The names are used only to
describe compatibility.
