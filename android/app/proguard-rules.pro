# kotlinx.serialization: keep generated serializers for the job store.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.clipboost.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.clipboost.core.**$$serializer { *; }
