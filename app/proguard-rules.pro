# Vigil Proguard & R8 Optimization Rules

# Preserve Application & Services
-keep class com.vigil.security.service.** { *; }
-keep class com.vigil.security.MainActivity { *; }
-keep class com.vigil.security.VigilApplication { *; }

# Preserve Room Database, DAOs & Entities
-keep class androidx.room.** { *; }
-keep class com.vigil.security.core.database.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Preserve Data Models & JSON Serialization
-keep class com.vigil.security.core.model.** { *; }
-keep class com.vigil.security.threatintel.OpenPhishStats { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# Preserve ViewModels
-keep class com.vigil.security.ui.**ViewModel { *; }
-keepclassmembers class com.vigil.security.ui.**ViewModel { *; }

# OkHttp & Okio Optimization
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Compose Optimization
-dontwarn androidx.compose.**
