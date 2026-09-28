# Regole per la build di rilascio (oggi la minificazione e disattivata).
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class it.goccia.app.**$$serializer { *; }
-keepclassmembers class it.goccia.app.** {
    *** Companion;
}
-keepclasseswithmembers class it.goccia.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class org.maplibre.** { *; }
