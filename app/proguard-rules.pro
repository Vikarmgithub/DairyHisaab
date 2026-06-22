# Add project specific ProGuard rules here.

# 🔒 ENABLED minifyEnabled/shrinkResources for release — code ab obfuscated hoga,
# decompile karne pe class/field/method names readable nahi rahenge.
# Niche ke "keep" rules zaroori hain taaki obfuscation se app ka data/JSON/Firebase
# breakage na ho.

# ── Gson model classes ──
# Yeh classes Gson se JSON serialize/deserialize hoti hain (local backup + Firestore
# data field names se match karta hai). Agar field names obfuscate ho gaye, purana
# saved data (Customer/MilkEntry/Payment/RateEntry) dobara load nahi hoga.
-keep class com.example.dairyhisaab.Customer { *; }
-keep class com.example.dairyhisaab.MilkEntry { *; }
-keep class com.example.dairyhisaab.Payment { *; }
-keep class com.example.dairyhisaab.RateEntry { *; }

# Gson library internals (reflection use karti hai)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# 🔧 R8 full-mode TypeToken fix — anonymous "new TypeToken<...>(){}" subclasses
# ko R8 merge/inline kar deta hai jisse generic type info toot jata hai, isi se
# "TypeToken must be created with a type argument" crash aata hai. Yeh Gson ka
# official recommended fix hai (allowobfuscation/allowshrinking so naam/shrink
# to ho sakta hai, par class structure aur generic signature surakshit rahega).
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ── Firebase ──
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ── WorkManager background worker (AutoBackupWorker reflection se instantiate hota hai) ──
-keep class com.example.dairyhisaab.AutoBackupWorker { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker

# ── androidx.security crypto (Keystore-backed encryption) ──
-keep class androidx.security.crypto.** { *; }
