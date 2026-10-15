# ResQNet keeps its own signed envelope format; it is never parsed by
# reflection, so the default optimizations apply cleanly. These rules exist for
# the parts that are.
-keep class dev.resqnet.mesh.** { *; }
-keep class dev.resqnet.android.** { *; }

# Nearby Connections reflects over its connection strategies.
-keep class com.google.android.gms.nearby.** { *; }
-dontwarn com.google.android.gms.nearby.**

# The routing engine uses records and sealed types; keep their members intact
# so the generated equals/hashCode survive shrinking.
-keepattributes Signature,InnerClasses,EnclosingMethod,Record
