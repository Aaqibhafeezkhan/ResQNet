# ResQNet keeps its own signed envelope format; it is never parsed by
# reflection, so the default optimizations apply cleanly. These rules exist for
# the parts that are.
-keep class dev.resqnet.mesh.** { *; }
-keep class dev.resqnet.android.** { *; }

# Nearby Connections reflects over its connection strategies.
-keep class com.google.android.gms.nearby.** { *; }
-dontwarn com.google.android.gms.nearby.**

# Tink 1.24 ships optional X-Wing HPKE classes that reference the Android 16
# crypto API. ResQNet uses X25519 HPKE and never loads those classes.
-dontwarn android.crypto.hpke.**

# The routing engine uses records and sealed types; keep their members intact
# so the generated equals/hashCode survive shrinking.
-keepattributes Signature,InnerClasses,EnclosingMethod,Record
