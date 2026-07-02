# Models are built from JSON manually (no reflection) — no keep rules needed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# androidx.security-crypto → Tink references compile-only javax annotations that
# are not on the Android classpath. These are safe to ignore (R8-generated rules).
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
