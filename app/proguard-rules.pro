# TransNote 混淆规则（纯本地应用，未开启 minify，规则仅作占位与未来扩展）
-keep class com.transnote.traffic.data.database.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
