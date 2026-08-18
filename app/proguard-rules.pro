# kotlinx.serialization : les sérialiseurs générés sont référencés par réflexion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.madhi.tracker.adapter.output.network.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.madhi.tracker.adapter.output.network.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room et Hilt gèrent leurs propres règles via des fichiers embarqués.
