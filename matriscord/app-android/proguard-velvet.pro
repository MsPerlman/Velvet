
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    <fields>;
}
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

-keep class org.matrix.rustcomponents.sdk.** { *; }
-keep class uniffi.** { *; }
-keep interface org.matrix.rustcomponents.sdk.** { *; }
-keep interface uniffi.** { *; }

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static <1>$Companion Companion;
    static **$* *;
}
-keepclasseswithmembers class ** {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class li.velvetchat.core.model.** { *; }
-keep class li.velvetchat.design.** { *; }

-keep class io.livekit.** { *; }
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

-keep class io.github.jaredmdobson.concentus.** { *; }

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
