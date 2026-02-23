# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep llama.cpp classes
-keep class com.localai.companion.llama.** { *; }

# Keep TTS classes
-keep class com.localai.companion.tts.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
