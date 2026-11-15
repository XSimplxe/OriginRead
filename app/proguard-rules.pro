# Add project specific ProGuard rules here.

# Room entities are serialized by generated code.
-keep class com.yuanyue.reader.model.** { *; }

# PDFBox-Android uses reflection for font and PDF object loading.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
