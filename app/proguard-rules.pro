# Kotlinx serialization (official recommended rules, adapted for this project).
-keepattributes *Annotation*, InnerClasses, Signature

-dontnote kotlinx.serialization.**

# Keep generated serializers of our models.
-keepclassmembers class com.toadlight.filter.** {
    *** Companion;
}
-keepclasseswithmembers class com.toadlight.filter.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.toadlight.filter.**$$serializer { *; }

# Compose / AndroidX ship their own consumer rules; nothing extra needed.
