# Room entities are read/written via reflection by Firestore's POJO mapper
# (TransactionRepository writes them straight into Firestore documents), so
# keep their fields and no-arg constructors through R8 shrinking/obfuscation.
-keep class com.hisab.app.data.local.entity.** { *; }
-keepclassmembers class com.hisab.app.data.local.entity.** {
    <init>();
}
