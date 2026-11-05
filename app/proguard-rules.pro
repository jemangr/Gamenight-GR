# kotlinx.serialization: keep the generated serializers of the content classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class gr.wsol.gamenight.** { *** Companion; *** serializer(...); }
-keep,includedescriptorclasses class gr.wsol.gamenight.**$$serializer { *; }
