# SQLCipher uses JNI; keep its classes intact.
-keep class net.zetetic.database.** { *; }

# kotlinx.serialization: keep generated serializers for app models.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class dev.dyfeed.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class dev.dyfeed.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.dyfeed.**$$serializer { *; }

# Retrofit service interface (Retrofit ships its own rules for the rest).
-keep,allowobfuscation interface dev.dyfeed.source.api.ApiService

# Activity aliases are referenced by class name from LauncherDisguise.
-keep class dev.dyfeed.MainActivity
